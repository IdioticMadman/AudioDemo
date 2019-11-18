package com.robert.audiodemo.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Process;

import net.qiujuer.opus.OpusDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class AudioTrackThread extends Thread {

    private final InputStream mInputStream;

    private final AudioTrack mAudioTrack;
    private AcousticEchoCanceler mAcousticEchoCanceler;

    public AudioTrackThread(InputStream inputStream, int audioSessionId) {
        this.mInputStream = inputStream;

        //播放器内部缓存区大小
        final int minBufferSize = AudioTrack.getMinBufferSize(AudioContract.SAMPLE_RATE,
                AudioContract.AUDIO_CHANNEL_OUT,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setChannelMask(AudioContract.AUDIO_CHANNEL_OUT)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AudioContract.SAMPLE_RATE)
                .build();
        //初始化播放器
        mAudioTrack = new AudioTrack(attributes, audioFormat, minBufferSize, AudioTrack.MODE_STREAM, audioSessionId);

        //初始化回声消音器
        try {
            AcousticEchoCanceler canceler = AcousticEchoCanceler.create(audioSessionId);
            canceler.setEnabled(true);
            mAcousticEchoCanceler = canceler;
        } catch (Exception e) {
            mAcousticEchoCanceler = null;
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
        final AudioTrack audioTrack = this.mAudioTrack;
        final InputStream inputStream = mInputStream;

        final byte[] pcmBuffer = new byte[AudioContract.FRAME_SIZE * AudioContract.NUM_CHANNELS * AudioContract.OPUS_PCM_STRUCT_SIZE];

        final byte[] encodeBuffer = new byte[1024];

        final ByteBuffer encodeSizeBuffer = ByteBuffer.allocate(2);

        OpusDecoder decoder = new OpusDecoder(AudioContract.SAMPLE_RATE, AudioContract.NUM_CHANNELS);

        audioTrack.play();

        try {
            while (!Thread.interrupted()) {
                encodeSizeBuffer.clear();
                //获取长度
                fullData(inputStream, encodeSizeBuffer.array(), 2);
                encodeSizeBuffer.position(2);
                encodeSizeBuffer.flip();
                int encodeSize = encodeSizeBuffer.getShort();

                //填充数据
                if (!fullData(inputStream, encodeBuffer, encodeSize)) {
                    continue;
                }

                //解压数据
                int pcmSize = decoder.decode(encodeBuffer, encodeSize, pcmBuffer, AudioContract.FRAME_SIZE);

                //播放数据
                audioTrack.write(pcmBuffer, 0, pcmSize);
                audioTrack.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            audioTrack.stop();
            audioTrack.release();
            decoder.release();
            if (mAcousticEchoCanceler != null) {
                mAcousticEchoCanceler.release();
            }
        }
    }

    private boolean fullData(InputStream inputStream, byte[] array, int size) throws IOException {
        int readSize = 0;
        do {
            int read = inputStream.read(array, readSize, size - readSize);
            if (read == -1) {
                return false;
            }
            readSize += read;
        } while (readSize < size);
        return true;

    }
}
