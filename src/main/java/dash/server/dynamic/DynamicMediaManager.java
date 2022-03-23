package dash.server.dynamic;

import dash.server.dynamic.handler.ProcessClientChannelHandler;
import dash.server.dynamic.handler.ProcessServerChannelHandler;
import dash.unit.DashUnit;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioDatagramChannel;
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicMediaManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DynamicMediaManager.class);

    public static final String MESSAGE_MAGIC_COOKIE = "PM";

    private final SocketManager socketManager;
    private final NetAddress localListenAddress;
    private GroupSocket localGroupSocket = null;
    private final NetAddress targetAddress;
    private final int sessionId = new Random().nextInt();

    private DashUnit targetDashUnit = null;

    private final AtomicInteger requestSeqNumber = new AtomicInteger(0);
    private final AtomicInteger responseSeqNumber = new AtomicInteger(0);
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DynamicMediaManager(SocketManager socketManager) {
        this.socketManager = socketManager;

        this.localListenAddress = new NetAddress(
                AppInstance.getInstance().getConfigManager().getPreprocessListenIp(),
                AppInstance.getInstance().getConfigManager().getPreprocessListenPort(),
                true, SocketProtocol.UDP
        );

        this.targetAddress = new NetAddress(
                AppInstance.getInstance().getConfigManager().getPreprocessTargetIp(),
                AppInstance.getInstance().getConfigManager().getPreprocessTargetPort(),
                true, SocketProtocol.UDP
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start() {
        // OPEN LISTEN CHANNEL
        socketManager.addSocket(localListenAddress, new PreProcessServerChannelHandlerInitializer());
        localGroupSocket = socketManager.getSocket(localListenAddress);
        localGroupSocket.getListenSocket().openListenChannel();
        logger.debug("[DynamicMediaManager] OPEN [{}]", localGroupSocket);

        // ADD TARGET CHANNEL
        localGroupSocket.addDestination(targetAddress, null, sessionId, new PreProcessClientChannelHandlerInitializer());
        logger.debug("[DynamicMediaManager] ADD DESTINATION [{}]", targetAddress);
    }

    public void stop() {
        // CLOSE LISTEN CHANNEL
        if (localGroupSocket != null) {
            // REMOVE TARGET CHANNEL
            localGroupSocket.removeDestination(sessionId);

            localGroupSocket.getListenSocket().closeListenChannel();
            socketManager.removeSocket(localListenAddress);

            logger.debug("[DynamicMediaManager] CLOSE [{}]", localGroupSocket);
            localGroupSocket = null;
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public int getSessionId() {
        return sessionId;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public NetAddress getLocalListenAddress() {
        return localListenAddress;
    }

    public GroupSocket getLocalGroupSocket() {
        return localGroupSocket;
    }

    public NetAddress getTargetAddress() {
        return targetAddress;
    }

    public DashUnit getTargetDashUnit() {
        return targetDashUnit;
    }

    public void setTargetDashUnit(DashUnit targetDashUnit) {
        this.targetDashUnit = targetDashUnit;
    }

    public AtomicInteger getRequestSeqNumber() {
        return requestSeqNumber;
    }

    public AtomicInteger getResponseSeqNumber() {
        return responseSeqNumber;
    }

    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private static class PreProcessClientChannelHandlerInitializer extends ChannelInitializer<NioDatagramChannel> {

        public PreProcessClientChannelHandlerInitializer() {}

        @Override
        public void initChannel(NioDatagramChannel nioDatagramChannel) {
            final ChannelPipeline p = nioDatagramChannel.pipeline();
            p.addLast("client_handler", new ProcessClientChannelHandler());
        }
    }

    private static class PreProcessServerChannelHandlerInitializer extends ChannelInitializer<NioDatagramChannel> {

        public PreProcessServerChannelHandlerInitializer() {}

        @Override
        public void initChannel(NioDatagramChannel nioDatagramChannel) {
            final ChannelPipeline p = nioDatagramChannel.pipeline();
            p.addLast("server_handler", new ProcessServerChannelHandler());
        }
    }
    ////////////////////////////////////////////////////////////

}
