package network.socket.netty;

import instance.BaseEnvironment;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import util.module.ConcurrentCyclicFIFO;

public class NettyChannel {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final BaseEnvironment baseEnvironment;
    private final long sessionId;
    private final int threadCount;

    private final int sendBufSize;
    private final int recvBufSize;
    private final ConcurrentCyclicFIFO<byte[]> sendBuf;
    private final ConcurrentCyclicFIFO<byte[]> recvBuf;

    private String listenIp = null;
    private int listenPort = 0;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NettyChannel(BaseEnvironment baseEnvironment, long sessionId, int threadCount, int sendBufSize, int recvBufSize) {
        this.baseEnvironment = baseEnvironment;
        this.sessionId = sessionId;
        this.threadCount = threadCount;

        this.sendBufSize = sendBufSize;
        this.recvBufSize = recvBufSize;
        this.sendBuf = new ConcurrentCyclicFIFO<>();
        this.recvBuf = new ConcurrentCyclicFIFO<>();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public void stop() {}

    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getSendBufSize() {
        return sendBufSize;
    }

    public int getRecvBufSize() {
        return recvBufSize;
    }

    public String getListenIp() {
        return listenIp;
    }

    public void setListenIp(String listenIp) {
        this.listenIp = listenIp;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public ConcurrentCyclicFIFO<byte[]> getSendBuf() {
        return sendBuf;
    }

    public ConcurrentCyclicFIFO<byte[]> getRecvBuf() {
        return recvBuf;
    }

    public Channel openListenChannel(String ip, int port) {
        return null;
    }

    public void closeListenChannel() {}

    public Channel openConnectChannel(String ip, int port) {
        return null;
    }

    public void closeConnectChannel() {}

    public void sendData(byte[] data, int dataLength) {}

    public void sendHttpRequest(HttpRequest httpRequest) {}
    ////////////////////////////////////////////////////////////

}
