package dash.client.handler.base;

import config.ConfigManager;
import dash.client.DashClient;
import dash.client.handler.DashAudioHttpClientHandler;
import dash.client.handler.DashMpdHttpClientHandler;
import dash.client.handler.DashVideoHttpClientHandler;
import instance.BaseEnvironment;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.system.ResourceManager;

import java.util.concurrent.atomic.AtomicInteger;

public class DashLocalAddressManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashLocalAddressManager.class);

    // TODO : 네트워크 자원 관리 필요
    // 1) [1~N] 개의 MPD local listen address 사전에 할당 필요
    // 2) [1~N] 개의 Audio local listen address 사전에 할당 필요
    // 3) [1~N] 개의 Video local listen address 사전에 할당 필요

    private final DashLocalNetworkInfo[] dashLocalMpdNetworkInfos;
    private final AtomicInteger dashLocalMpdNetworkInfoIndex = new AtomicInteger(0);

    private final DashLocalNetworkInfo[] dashLocalAudioNetworkInfos;
    private final AtomicInteger dashLocalAudioNetworkInfoIndex = new AtomicInteger(0);

    private final DashLocalNetworkInfo[] dashLocalVideoNetworkInfos;
    private final AtomicInteger dashLocalVideoNetworkInfoIndex = new AtomicInteger(0);

    private final ResourceManager resourceManager;
    private final SocketManager socketManager;
    private final ConfigManager configManager;

    private final boolean isSsl;
    private SslContext sslContext = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashLocalAddressManager(BaseEnvironment baseEnvironment, boolean isSsl) {
        this.configManager = AppInstance.getInstance().getConfigManager();
        this.isSsl = isSsl;

        resourceManager = baseEnvironment.getPortResourceManager();
        socketManager = new SocketManager(
                baseEnvironment,
                true, false,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        );

        dashLocalMpdNetworkInfos = new DashLocalNetworkInfo[configManager.getLocalMpdListenSocketSize()];
        dashLocalAudioNetworkInfos = new DashLocalNetworkInfo[configManager.getLocalAudioListenSocketSize()];
        dashLocalVideoNetworkInfos = new DashLocalNetworkInfo[configManager.getLocalVideoListenSocketSize()];
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean start() {
        if (isSsl) {
            try {
                sslContext = SslContext.newClientContext();
            } catch (Exception e) {
                logger.warn("[DashLocalAddressManager] Fail to set the ssl context.", e);
            }
        }

        // 1) ADDRESS
        // MPD
        for (int i = 0; i < dashLocalMpdNetworkInfos.length; i++) {
            NetAddress localMpdListenAddress = new NetAddress(
                    configManager.getHttpListenIp(),
                    resourceManager.takePort(),
                    true, SocketProtocol.TCP
            );

            socketManager.addSocket(localMpdListenAddress, new HttpMessageServerInitializer());
            GroupSocket localMpdGroupSocket = socketManager.getSocket(localMpdListenAddress);
            if (!localMpdGroupSocket.getListenSocket().openListenChannel()) {
                closeAllMpdSocket();
                logger.warn("[DashLocalAddressManager] Fail to open the local mpd listen channel.");
                return false;
            }

            dashLocalMpdNetworkInfos[i] = new DashLocalNetworkInfo(localMpdListenAddress, localMpdGroupSocket);
            logger.debug("[DashLocalAddressManager] Success to register the local mpd network info. ([{}]:{})", i, dashLocalMpdNetworkInfos[i]);
        }

        // AUDIO
        for (int i = 0; i < dashLocalAudioNetworkInfos.length; i++) {
            NetAddress localAudioListenAddress = new NetAddress(
                    configManager.getHttpListenIp(),
                    resourceManager.takePort(),
                    true, SocketProtocol.TCP
            );

            socketManager.addSocket(localAudioListenAddress, new HttpMessageServerInitializer());
            GroupSocket localAudioGroupSocket = socketManager.getSocket(localAudioListenAddress);
            if (!localAudioGroupSocket.getListenSocket().openListenChannel()) {
                closeAllMpdSocket();
                closeAllAudioSocket();
                logger.warn("[DashLocalAddressManager] Fail to open the local audio listen channel.");
                return false;
            }

            dashLocalAudioNetworkInfos[i] = new DashLocalNetworkInfo(localAudioListenAddress, localAudioGroupSocket);
            logger.debug("[DashLocalAddressManager] Success to register the local audio network info. ([{}]:{})", i, dashLocalAudioNetworkInfos[i]);
        }


        // VIDEO
        if (!configManager.isAudioOnly()) {
            for (int i = 0; i < dashLocalAudioNetworkInfos.length; i++) {
                NetAddress localVideoListenAddress = new NetAddress(
                        configManager.getHttpListenIp(),
                        resourceManager.takePort(),
                        true, SocketProtocol.TCP
                );

                socketManager.addSocket(localVideoListenAddress, new HttpMessageServerInitializer());

                GroupSocket localVideoGroupSocket = socketManager.getSocket(localVideoListenAddress);
                if (!localVideoGroupSocket.getListenSocket().openListenChannel()) {
                    closeAllMpdSocket();
                    closeAllAudioSocket();
                    closeAllVideoSocket();
                    logger.warn("[DashLocalAddressManager] Fail to open the local video listen channel.");
                    return false;
                }

                dashLocalVideoNetworkInfos[i] = new DashLocalNetworkInfo(localVideoListenAddress, localVideoGroupSocket);
                logger.debug("[DashLocalAddressManager] Success to register the local video network info. ([{}]:{})", i, dashLocalVideoNetworkInfos[i]);
            }

        }

        return true;
    }

    public void stop() {
        closeAllMpdSocket();
        closeAllAudioSocket();
        closeAllVideoSocket();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void closeMpdSocket(int index) {
        DashLocalNetworkInfo dashLocalMpdNetworkInfo = dashLocalMpdNetworkInfos[index];
        if (dashLocalMpdNetworkInfo == null) { return; }

        GroupSocket localMpdGroupSocket = dashLocalMpdNetworkInfo.getLocalGroupSocket();
        NetAddress localMpdListenAddress = dashLocalMpdNetworkInfo.getLocalListenAddress();
        if (localMpdGroupSocket != null && localMpdListenAddress != null) {
            localMpdGroupSocket.removeAllDestinations();
            localMpdGroupSocket.getListenSocket().stop();
            resourceManager.restorePort(localMpdListenAddress.getPort());
            logger.debug("[DashLocalAddressManager] Success to unregister the local mpd network info. ([{}]:{})", index, dashLocalMpdNetworkInfo);
        }
    }

    public void closeAllMpdSocket() {
        for (int i = 0; i < dashLocalMpdNetworkInfos.length; i++) {
            closeMpdSocket(i);
        }
    }

    public void closeAudioSocket(int index) {
        DashLocalNetworkInfo dashLocalAudioNetworkInfo = dashLocalAudioNetworkInfos[index];
        if (dashLocalAudioNetworkInfo == null) { return; }

        GroupSocket localAudioGroupSocket = dashLocalAudioNetworkInfo.getLocalGroupSocket();
        NetAddress localAudioListenAddress = dashLocalAudioNetworkInfo.getLocalListenAddress();
        if (localAudioGroupSocket != null && localAudioListenAddress != null) {
            localAudioGroupSocket.removeAllDestinations();
            localAudioGroupSocket.getListenSocket().stop();
            resourceManager.restorePort(localAudioListenAddress.getPort());
            logger.debug("[DashLocalAddressManager] Success to unregister the local audio network info. ([{}]:{})", index, dashLocalAudioNetworkInfo);
        }
    }

    public void closeAllAudioSocket() {
        for (int i = 0; i < dashLocalAudioNetworkInfos.length; i++) {
            closeAudioSocket(i);
        }
    }

    public void closeVideoSocket(int index) {
        DashLocalNetworkInfo dashLocalVideoNetworkInfo = dashLocalVideoNetworkInfos[index];
        if (dashLocalVideoNetworkInfo == null) { return; }

        GroupSocket localVideoGroupSocket = dashLocalVideoNetworkInfo.getLocalGroupSocket();
        NetAddress localVideoListenAddress = dashLocalVideoNetworkInfo.getLocalListenAddress();
        if (localVideoGroupSocket != null && localVideoListenAddress != null) {
            localVideoGroupSocket.removeAllDestinations();
            localVideoGroupSocket.getListenSocket().stop();
            resourceManager.restorePort(localVideoListenAddress.getPort());
            logger.debug("[DashLocalAddressManager] Success to unregister the local video network info. ([{}]:{})", index, dashLocalVideoNetworkInfo);
        }
    }

    public void closeAllVideoSocket() {
        for (int i = 0; i < dashLocalVideoNetworkInfos.length; i++) {
            closeVideoSocket(i);
        }
    }

    public int addTargetToMpdSocket(DashClient dashClient, NetAddress targetAddress, long sessionId) {
        int mpdNetworkInfoIndex = dashLocalMpdNetworkInfoIndex.get();
        DashLocalNetworkInfo dashLocalMpdNetworkInfo = dashLocalMpdNetworkInfos[mpdNetworkInfoIndex];
        if (dashLocalMpdNetworkInfo == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the mpd connect channel. The mpd network info is not defined. (index={})", mpdNetworkInfoIndex);
            return -1;
        }

        GroupSocket localMpdGroupSocket = dashLocalMpdNetworkInfo.getLocalGroupSocket();
        if (localMpdGroupSocket == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the mpd connect channel. The mpd network info's group socket is not defined. (index={})", mpdNetworkInfoIndex);
            return -1;
        }

        if (!localMpdGroupSocket.addDestination(
                targetAddress,
                null,
                sessionId,
                new HttpMpdMessageClientInitializer(sslContext, dashClient))) {
            logger.warn("[DashLocalAddressManager] Fail to open the mpd connect channel. Fail to add the destination. (localNetworkInfo={}, target={})", dashLocalMpdNetworkInfo, targetAddress);
            return -1;
        }

        int newMpdNetworkInfoIndex = dashLocalMpdNetworkInfoIndex.incrementAndGet();
        if (newMpdNetworkInfoIndex >= dashLocalMpdNetworkInfos.length) {
            dashLocalMpdNetworkInfoIndex.set(0);
        }

        return mpdNetworkInfoIndex;
    }

    public boolean deleteTargetFromMpdSocket (int index, long sessionId) {
        if (!checkMpdNetworkInfoIndex(index)) { return false; }

        DashLocalNetworkInfo dashLocalMpdNetworkInfo = dashLocalMpdNetworkInfos[index];
        if (dashLocalMpdNetworkInfo == null) { return false; }

        GroupSocket localMpdGroupSocket = dashLocalMpdNetworkInfo.getLocalGroupSocket();
        if (localMpdGroupSocket == null) { return false; }

        return localMpdGroupSocket.removeDestination(sessionId);
    }

    public int addTargetToAudioSocket(DashClient dashClient, NetAddress targetAddress, long sessionId) {
        int audioNetworkInfoIndex = dashLocalAudioNetworkInfoIndex.get();
        DashLocalNetworkInfo dashLocalAudioNetworkInfo = dashLocalAudioNetworkInfos[audioNetworkInfoIndex];
        if (dashLocalAudioNetworkInfo == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the audio connect channel. The audio network info is not defined. (index={})", audioNetworkInfoIndex);
            return -1;
        }

        GroupSocket localAudioGroupSocket = dashLocalAudioNetworkInfo.getLocalGroupSocket();
        if (localAudioGroupSocket == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the audio connect channel. The audio network info's group socket is not defined. (index={})", audioNetworkInfoIndex);
            return -1;
        }

        if (!localAudioGroupSocket.addDestination(
                targetAddress,
                null,
                sessionId,
                new HttpAudioMessageClientInitializer(sslContext, dashClient))) {
            logger.warn("[DashLocalAddressManager] Fail to open the audio connect channel.  Fail to add the destination. (localNetworkInfo={}, target={})", localAudioGroupSocket, targetAddress);
            return -1;
        }

        int newAudioNetworkInfoIndex = dashLocalAudioNetworkInfoIndex.incrementAndGet();
        if (newAudioNetworkInfoIndex >= dashLocalAudioNetworkInfos.length) {
            dashLocalAudioNetworkInfoIndex.set(0);
        }

        return audioNetworkInfoIndex;
    }

    public boolean deleteTargetFromAudioSocket (int index, long sessionId) {
        if (!checkAudioNetworkInfoIndex(index)) { return false; }

        DashLocalNetworkInfo dashLocalAudioNetworkInfo = dashLocalAudioNetworkInfos[index];
        if (dashLocalAudioNetworkInfo == null) { return false; }

        GroupSocket localAudioGroupSocket = dashLocalAudioNetworkInfo.getLocalGroupSocket();
        if (localAudioGroupSocket == null) { return false; }

        return localAudioGroupSocket.removeDestination(sessionId);
    }

    public int addTargetToVideoSocket(DashClient dashClient, NetAddress targetAddress, long sessionId) {
        int videoNetworkInfoIndex = dashLocalVideoNetworkInfoIndex.get();
        DashLocalNetworkInfo dashLocalVideoNetworkInfo = dashLocalVideoNetworkInfos[videoNetworkInfoIndex];
        if (dashLocalVideoNetworkInfo == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the video connect channel. The video network info is not defined. (index={})", videoNetworkInfoIndex);
            return -1;
        }

        GroupSocket localVideoGroupSocket = dashLocalVideoNetworkInfo.getLocalGroupSocket();
        if (localVideoGroupSocket == null) {
            logger.warn("[DashLocalAddressManager] Fail to open the video connect channel. The video network info's group socket is not defined. (index={})", videoNetworkInfoIndex);
            return -1;
        }

        if (!localVideoGroupSocket.addDestination(
                targetAddress,
                null,
                sessionId,
                new HttpVideoMessageClientInitializer(sslContext, dashClient))) {
            logger.warn("[DashLocalAddressManager] Fail to open the video connect channel.  Fail to add the destination. (localNetworkInfo={}, target={})", dashLocalVideoNetworkInfo, targetAddress);
            return -1;
        }

        int newVideoNetworkInfoIndex = dashLocalVideoNetworkInfoIndex.incrementAndGet();
        if (newVideoNetworkInfoIndex >= dashLocalVideoNetworkInfos.length) {
            dashLocalVideoNetworkInfoIndex.set(0);
        }

        return videoNetworkInfoIndex;
    }

    public boolean deleteTargetFromVideoSocket (int index, long sessionId) {
        if (!checkVideoNetworkInfoIndex(index)) { return false; }

        DashLocalNetworkInfo dashLocalVideoNetworkInfo = dashLocalVideoNetworkInfos[index];
        if (dashLocalVideoNetworkInfo == null) { return false; }

        GroupSocket localVideoGroupSocket = dashLocalVideoNetworkInfo.getLocalGroupSocket();
        if (localVideoGroupSocket == null) { return false; }

        return localVideoGroupSocket.removeDestination(sessionId);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashLocalNetworkInfo getMpdNetworkInfo(int index) {
        if (!checkMpdNetworkInfoIndex(index)) { return null; }

        return dashLocalMpdNetworkInfos[index];
    }

    public DashLocalNetworkInfo getAudioNetworkInfo(int index) {
        if (!checkAudioNetworkInfoIndex(index)) { return null; }

        return dashLocalAudioNetworkInfos[index];
    }

    public DashLocalNetworkInfo getVideoNetworkInfo(int index) {
        if (!checkVideoNetworkInfoIndex(index)) { return null; }

        return dashLocalVideoNetworkInfos[index];
    }

    private boolean checkMpdNetworkInfoIndex(int index) {
        return index >= 0 && index < dashLocalMpdNetworkInfos.length;
    }

    private boolean checkAudioNetworkInfoIndex(int index) {
        return index >= 0 && index < dashLocalAudioNetworkInfos.length;
    }

    private boolean checkVideoNetworkInfoIndex(int index) {
        return index >= 0 && index < dashLocalVideoNetworkInfos.length;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private static class HttpMessageServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();
            p.addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192, false));
            p.addLast("aggregator", new HttpObjectAggregator(100 * 1024 * 1024));
            p.addLast("encoder", new HttpResponseEncoder());
        }
    }

    private static class HttpAudioMessageClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;
        private final DashClient dashClient;

        public HttpAudioMessageClientInitializer(SslContext sslContext, DashClient dashClient) {
            this.sslContext = sslContext;
            this.dashClient = dashClient;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            // Enable HTTPS if necessary.
            if (sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc()));
            }

            p.addLast("encoder", new HttpClientCodec());
            p.addLast(new HttpContentDecompressor());
            p.addLast(new DashAudioHttpClientHandler(dashClient));
        }
    }

    private static class HttpVideoMessageClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;
        private final DashClient dashClient;

        public HttpVideoMessageClientInitializer(SslContext sslContext, DashClient dashClient) {
            this.sslContext = sslContext;
            this.dashClient = dashClient;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            // Enable HTTPS if necessary.
            if (sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc()));
            }

            p.addLast("encoder", new HttpClientCodec());
            p.addLast(new HttpContentDecompressor());
            p.addLast(new DashVideoHttpClientHandler(dashClient));
        }
    }

    private static class HttpMpdMessageClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;
        private final DashClient dashClient;

        public HttpMpdMessageClientInitializer(SslContext sslContext, DashClient dashClient) {
            this.sslContext = sslContext;
            this.dashClient = dashClient;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            // Enable HTTPS if necessary.
            if (sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc()));
            }

            p.addLast("encoder", new HttpClientCodec());
            p.addLast(new HttpContentDecompressor());
            p.addLast(new DashMpdHttpClientHandler(dashClient));
        }
    }
    ////////////////////////////////////////////////////////////

}
