package dash.client.handler;

import config.ConfigManager;
import dash.client.DashClient;
import instance.BaseEnvironment;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import network.definition.DestinationRecord;
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import network.socket.netty.NettyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

import java.net.URI;

public class DashHttpMessageSender {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageSender.class);

    public static final String HTTP_PREFIX = "http";

    private final String dashUnitId;
    private String host = null;
    private SslContext sslContext = null;

    private final ConfigManager configManager;
    private final SocketManager socketManager;
    private final NetAddress localAudioListenAddress;
    private NetAddress localVideoListenAddress = null;
    private final NetAddress localMpdListenAddress;
    private final NetAddress targetAddress;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageSender(String dashUnitId, BaseEnvironment baseEnvironment, boolean isSsl) {
        this.dashUnitId = dashUnitId;
        configManager = AppInstance.getInstance().getConfigManager();

        socketManager = new SocketManager(
                baseEnvironment,
                false, false,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        );

        // AUDIO
        localAudioListenAddress = new NetAddress(
                configManager.getHttpListenIp(),
                ServiceManager.getInstance()
                        .getDashServer()
                        .getBaseEnvironment()
                        .getPortResourceManager()
                        .takePort(),
                true, SocketProtocol.TCP
        );

        // VIDEO
        if (!configManager.isAudioOnly()) {
            localVideoListenAddress = new NetAddress(
                    configManager.getHttpListenIp(),
                    ServiceManager.getInstance()
                            .getDashServer()
                            .getBaseEnvironment()
                            .getPortResourceManager()
                            .takePort(),
                    true, SocketProtocol.TCP
            );
        }

        // MPD
        localMpdListenAddress = new NetAddress(
                configManager.getHttpListenIp(),
                ServiceManager.getInstance()
                        .getDashServer()
                        .getBaseEnvironment()
                        .getPortResourceManager()
                        .takePort(),
                true, SocketProtocol.TCP
        );

        targetAddress = new NetAddress(
                configManager.getHttpTargetIp(),
                configManager.getHttpTargetPort(),
                true, SocketProtocol.TCP
        );

        if (isSsl) {
            try {
                sslContext = SslContext.newClientContext();
            } catch (Exception e) {
                logger.warn("[DashHttpMessageSender({})] Fail to set the ssl context.", dashUnitId, e);
            }
        }

        socketManager.addSocket(localAudioListenAddress, new HttpMessageServerInitializer());
        if (localVideoListenAddress != null) { socketManager.addSocket(localVideoListenAddress, new HttpMessageServerInitializer()); }
        socketManager.addSocket(localMpdListenAddress, new HttpMessageServerInitializer());
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start(DashClient dashClient) {
        // AUDIO
        GroupSocket localAudioGroupSocket = socketManager.getSocket(localAudioListenAddress);
        localAudioGroupSocket.getListenSocket().openListenChannel();
        localAudioGroupSocket.addDestination(
                targetAddress,
                null,
                dashUnitId.hashCode(),
                new HttpAudioMessageClientInitializer(sslContext, dashClient)
        );

        // VIDEO
        if (!configManager.isAudioOnly() && localVideoListenAddress != null) {
            GroupSocket localVideoGroupSocket = socketManager.getSocket(localVideoListenAddress);
            localVideoGroupSocket.getListenSocket().openListenChannel();
            localVideoGroupSocket.addDestination(
                    targetAddress,
                    null,
                    dashUnitId.hashCode(),
                    new HttpVideoMessageClientInitializer(sslContext, dashClient)
            );
        }

        // MPD
        GroupSocket localMpdGroupSocket = socketManager.getSocket(localMpdListenAddress);
        localMpdGroupSocket.getListenSocket().openListenChannel();
        localMpdGroupSocket.addDestination(
                targetAddress,
                null,
                dashUnitId.hashCode(),
                new HttpMpdMessageClientInitializer(sslContext, dashClient)
        );
    }

    public void stop() {
        // AUDIO
        GroupSocket localAudioGroupSocket = socketManager.getSocket(localAudioListenAddress);
        localAudioGroupSocket.getListenSocket().closeListenChannel();

        DestinationRecord audioDestinationRecord = localAudioGroupSocket.getDestination(dashUnitId.hashCode());
        if (audioDestinationRecord == null) { return; }

        NettyChannel audioTargetNettyChannel = audioDestinationRecord.getNettyChannel();
        if (audioTargetNettyChannel != null) {
            audioTargetNettyChannel.closeConnectChannel();
        }

        // VIDEO
        if (!configManager.isAudioOnly() && localVideoListenAddress != null) {
            GroupSocket localVideoGroupSocket = socketManager.getSocket(localVideoListenAddress);
            localVideoGroupSocket.getListenSocket().closeListenChannel();

            DestinationRecord destinationRecord = localVideoGroupSocket.getDestination(dashUnitId.hashCode());
            if (destinationRecord == null) { return; }

            NettyChannel videoTargetNettyChannel = destinationRecord.getNettyChannel();
            if (videoTargetNettyChannel != null) {
                videoTargetNettyChannel.closeConnectChannel();
            }
        }

        // MPD
        GroupSocket localMpdGroupSocket = socketManager.getSocket(localMpdListenAddress);
        localMpdGroupSocket.getListenSocket().closeListenChannel();

        DestinationRecord destinationRecord = localMpdGroupSocket.getDestination(dashUnitId.hashCode());
        if (destinationRecord == null) { return; }

        NettyChannel mpdTargetNettyChannel = destinationRecord.getNettyChannel();
        if (mpdTargetNettyChannel != null) {
            mpdTargetNettyChannel.closeConnectChannel();
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpRequest makeHttpGetRequestMessage(String path) {
        URI uri = makeUri(path);
        if (uri == null) { return null; }

        HttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                uri.getRawPath()
        );

        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        request.headers().set(HttpHeaderNames.USER_AGENT, configManager.getServiceName());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");

        return request;
    }

    public void sendMessageForMpd(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        sendMessage(socketManager.getSocket(localMpdListenAddress), httpRequest);
    }

    public void sendMessageForVideo(HttpRequest httpRequest) {
        if (httpRequest == null || localVideoListenAddress == null) { return; }

        sendMessage(socketManager.getSocket(localVideoListenAddress), httpRequest);
    }

    public void sendMessageForAudio(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        sendMessage(socketManager.getSocket(localAudioListenAddress), httpRequest);
    }

    public void sendMessage(GroupSocket groupSocket, HttpRequest httpRequest) {
        if (groupSocket == null) { return; }

        DestinationRecord destinationRecord = groupSocket.getDestination(dashUnitId.hashCode());
        if (destinationRecord == null) { return; }

        NettyChannel nettyChannel = destinationRecord.getNettyChannel();
        if (nettyChannel != null) {
            nettyChannel.sendHttpRequest(httpRequest);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public URI makeUri(String path) {
        URI uri;
        try {
            uri = new URI(path);
            String scheme = uri.getScheme() == null ? HTTP_PREFIX : uri.getScheme();
            host = uri.getHost() == null ? configManager.getHttpListenIp() : uri.getHost();

            // 아직 https 지원하지 않음
            if (!HTTP_PREFIX.equalsIgnoreCase(scheme)) { // && !"https".equalsIgnoreCase(scheme)) {
                logger.warn("[DashHttpMessageSender({})] Only HTTP(S) is supported.", dashUnitId);
                return null;
            }
        } catch (Exception e) {
            logger.warn("[DashHttpMessageSender({})] URI Parsing error", dashUnitId, e);
            return null;
        }

        return uri;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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
