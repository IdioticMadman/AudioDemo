package com.robert.audiodemo.network;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

public class ServerNamedConnector extends Connector {

    private volatile String mServerName;
    private ConnectorStatusListener mConnectorStatusListener;
    private MessageArrivedListener mMessageArrivedListener;

    public ServerNamedConnector(String address, int port) throws IOException {
        SocketChannel channel = SocketChannel.open();
        Socket socket = channel.socket();
        socket.setTcpNoDelay(true);
        socket.setPerformancePreferences(1, 3, 2);
        socket.setReceiveBufferSize(1024);
        socket.setSendBufferSize(256);
        channel.connect(new InetSocketAddress(InetAddress.getByName(address), port));
        setup(channel);
    }

    @Override
    protected File createNewReceiveFile(long l, byte[] bytes) {
        return null;
    }

    @Override
    protected OutputStream createNewReceiveDirectOutputStream(long l, byte[] bytes) {
        return null;
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String entity = ((StringReceivePacket) packet).entity();
            if (entity.startsWith(ConnectorContract.COMMAND_INFO_NAME)) {
                synchronized (this) {
                    mServerName = entity.substring(ConnectorContract.COMMAND_INFO_NAME.length());
                    this.notifyAll();
                }
            } else if (entity.startsWith(ConnectorContract.COMMAND_INFO_AUDIO_PREFIX)) {
                if (mMessageArrivedListener != null) {
                    String command = entity.substring(ConnectorContract.COMMAND_INFO_AUDIO_PREFIX.length());
                    mMessageArrivedListener.onNewMessageArrived(new ConnectorInfo(command));
                }
            }
        }
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        if (mConnectorStatusListener != null) {
            mConnectorStatusListener.onConnectorClosed(this);
        }
    }

    public synchronized String getServerName() {
        if (mServerName == null) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return mServerName;
    }

    public void setConnectorStatusListener(ConnectorStatusListener connectorStatusListener) {
        this.mConnectorStatusListener = connectorStatusListener;
    }

    public void setMessageArrivedListener(MessageArrivedListener messageArrivedListener) {
        this.mMessageArrivedListener = messageArrivedListener;
    }

    public interface MessageArrivedListener {
        void onNewMessageArrived(ConnectorInfo connectorInfo);
    }

    public interface ConnectorStatusListener {
        void onConnectorClosed(Connector connector);
    }
}
