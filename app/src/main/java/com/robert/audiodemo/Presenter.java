package com.robert.audiodemo;

import com.robert.audiodemo.audio.AudioRecordThread;
import com.robert.audiodemo.audio.AudioTrackThread;
import com.robert.audiodemo.network.ConnectorContract;
import com.robert.audiodemo.network.ConnectorInfo;
import com.robert.audiodemo.network.ServerNamedConnector;
import com.robert.audiodemo.plugin.BlockingAvailableInputStream;

import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.genius.kit.handler.runable.Action;
import net.qiujuer.library.clink.box.StreamDirectSendPacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;
import net.qiujuer.library.clink.utils.plugin.CircularByteBuffer;

import java.io.IOException;
import java.io.OutputStream;

public class Presenter implements AppContract.Presenter, ServerNamedConnector.ConnectorStatusListener {
    //音频录制缓冲区，录制的声音将缓存到当前缓冲区等待被网络发送
    private final CircularByteBuffer mAudioRecordBuffer = new CircularByteBuffer(128, true);
    //音频播放缓冲区，网络接收的语音将存储到当前缓冲区等待被播放器读取播放
    private final CircularByteBuffer mAudioTrackBuffer = new CircularByteBuffer(1024, true);

    private final AppContract.View mView;

    private volatile ServerNamedConnector mCmdConnector;
    private volatile ServerNamedConnector mStreamConnector;

    private volatile AudioTrackThread mAudioTrackThread;
    private volatile AudioRecordThread mAudioRecordThread;

    private volatile StreamDirectSendPacket mAudioRecordStreamDirectSendPacket;

    public Presenter(AppContract.View view) {
        this.mView = view;
        mView.showProgress(R.string.dialog_linking);
        Run.onBackground(new InitAction());
    }

    @Override
    public void leaveRoom() {
        if (checkConnector()) {
            mView.showProgress(R.string.dialog_loading);
            mCmdConnector.send(ConnectorContract.COMMAND_AUDIO_LEAVE_ROOM);
        }
    }


    @Override
    public void joinRoom(String code) {
        if (checkConnector()) {
            mView.showProgress(R.string.dialog_loading);
            mCmdConnector.send(ConnectorContract.COMMAND_AUDIO_JOIN_ROOM + code);
        }
    }

    @Override
    public void createRoom() {
        if (checkConnector()) {
            mView.showProgress(R.string.dialog_loading);
            mCmdConnector.send(ConnectorContract.COMMAND_AUDIO_CREATE_ROOM);
        }
    }

    @Override
    public void destroy() {
        stopAudioThread();
        Run.onBackground(new DestroyAction());
    }

    private void startAudioThread() {
        BlockingAvailableInputStream audioRecordInputStream =
                new BlockingAvailableInputStream(mAudioRecordBuffer.getInputStream());
        mAudioRecordStreamDirectSendPacket = new StreamDirectSendPacket(audioRecordInputStream);
        mStreamConnector.send(mAudioRecordStreamDirectSendPacket);

        AudioRecordThread audioRecordThread =
                new AudioRecordThread(mAudioRecordBuffer.getOutputStream());
        BlockingAvailableInputStream audioTrackInputStream =
                new BlockingAvailableInputStream(mAudioTrackBuffer.getInputStream());
        AudioTrackThread audioTrackThread =
                new AudioTrackThread(audioTrackInputStream,
                        audioRecordThread.getAudioRecord().getAudioSessionId());

        audioRecordThread.start();
        audioTrackThread.start();
        mAudioTrackThread = audioTrackThread;
        mAudioRecordThread = audioRecordThread;
    }

    private void stopAudioThread() {
        //停止发送包
        if (mAudioRecordStreamDirectSendPacket != null) {
            CloseUtils.close(mAudioRecordStreamDirectSendPacket.open());
            mAudioRecordStreamDirectSendPacket = null;
        }

        //停止录音
        if (mAudioRecordThread != null) {
            mAudioRecordThread.interrupt();
            mAudioRecordThread = null;
        }

        //停止播放
        if (mAudioTrackThread != null) {
            mAudioTrackThread.interrupt();
            mAudioTrackThread = null;
        }
        //清空缓存
        mAudioRecordBuffer.clear();
        mAudioTrackBuffer.clear();
    }

    @Override
    public void onConnectorClosed(Connector connector) {
        destroy();
        dismissDialogAndToast(R.string.toast_connector_closed, false);
    }


    private boolean checkConnector() {
        if (mCmdConnector == null || mStreamConnector == null) {
            mView.showToast(R.string.toast_bad_network);
            return false;
        }
        return true;
    }

    private ServerNamedConnector.MessageArrivedListener mMessageArrivedListener =
            new ServerNamedConnector.MessageArrivedListener() {
                @Override
                public void onNewMessageArrived(ConnectorInfo connectorInfo) {
                    switch (connectorInfo.getKey()) {
                        case ConnectorContract.KEY_COMMAND_INFO_AUDIO_ROOM:
                            setViewRoomCode(connectorInfo.getValue());
                            dismissDialogAndToast(R.string.toast_room_name, true);
                            break;
                        case ConnectorContract.KEY_COMMAND_INFO_AUDIO_START:
                            startAudioThread();
                            dismissDialogAndToast(R.string.toast_start, true);
                            break;
                        case ConnectorContract.KEY_COMMAND_INFO_AUDIO_STOP:
                            stopAudioThread();
                            dismissDialogAndToast(R.string.toast_stop, false);
                            break;
                        case ConnectorContract.KEY_COMMAND_INFO_AUDIO_ERROR:
                            stopAudioThread();
                            dismissDialogAndToast(R.string.toast_error, false);
                            break;
                        default:
                            break;
                    }
                }
            };


    private void setViewRoomCode(final String roomCode) {
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                mView.showRoomCode(roomCode);
            }
        });
    }


    private class InitAction implements Action {
        @Override
        public void call() {
            try {
                //启动调度器
                IoContext.setup()
                        .ioProvider(new IoSelectorProvider())
                        .scheduler(new SchedulerImpl(1))
                        .start();

                //命令链接
                mCmdConnector = new ServerNamedConnector(AppContract.SERVER_ADDRESS, AppContract.PORT);
                mCmdConnector.setMessageArrivedListener(mMessageArrivedListener);
                mCmdConnector.setConnectorStatusListener(Presenter.this);

                //流链接
                mStreamConnector = new ServerNamedConnector(AppContract.SERVER_ADDRESS, AppContract.PORT) {
                    @Override
                    protected OutputStream createNewReceiveDirectOutputStream(long l, byte[] bytes) {
                        return mAudioTrackBuffer.getOutputStream();
                    }
                };

                mStreamConnector.setConnectorStatusListener(Presenter.this);
                //发送绑定流链接请求
                mCmdConnector.send(ConnectorContract.COMMAND_CONNECTOR_BIND + mStreamConnector.getServerName());
                //链接成功
                dismissDialogAndToast(R.string.toast_link_succeed, false);
            } catch (IOException e) {
                //失败了，销毁
                new DestroyAction().call();
                dismissDialogAndToast(R.string.toast_link_failed, false);
            }
        }
    }


    /**
     * 销毁动作
     * 1. 关闭两个链接
     * 2. 关闭IoContext
     */
    private class DestroyAction implements Action {
        @Override
        public void call() {
            CloseUtils.close(mCmdConnector, mStreamConnector);
            mCmdConnector = null;
            mStreamConnector = null;
            try {
                IoContext.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dismissDialogAndToast(final int stringRes, final boolean online) {
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                mView.dismissProgress();
                mView.showToast(stringRes);
                if (online) {
                    mView.onOnline();
                } else {
                    mView.onOffline();
                }
            }
        });

    }


}
