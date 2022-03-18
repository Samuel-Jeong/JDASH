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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.net.URI;

public class DashHttpMessageSender {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageSender.class);

    private String host = null;
    private SslContext sslContext = null;

    private final ConfigManager configManager;
    private final SocketManager socketManager;
    private final NetAddress localListenAddress;
    private final NetAddress targetAddress;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageSender(BaseEnvironment baseEnvironment, boolean isSsl) {
        configManager = AppInstance.getInstance().getConfigManager();

        socketManager = new SocketManager(
                baseEnvironment,
                false, false,
                configManager.getThreadCount(), configManager.getSendBufSize(), configManager.getRecvBufSize()
        );

        localListenAddress = new NetAddress(
                configManager.getHttpListenIp(),
                configManager.getHttpListenPort() + 1,
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
                logger.warn("[DashHttpMessageSender] Fail to set the ssl context.", e);
            }
        }

        socketManager.addSocket(localListenAddress, new HttpMessageServerInitializer());
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start(DashClient dashClient) {
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        localGroupSocket.getListenSocket().openListenChannel();

        localGroupSocket.addDestination(
                targetAddress,
                null,
                0,
                new HttpMessageClientInitializer(sslContext, dashClient)
        );
    }

    public void stop() {
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        localGroupSocket.getListenSocket().closeListenChannel();

        DestinationRecord destinationRecord = localGroupSocket.getDestination(0);
        destinationRecord.getNettyChannel().closeConnectChannel();
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

    public void sendMessage(HttpRequest httpRequest) {
        if (httpRequest == null) { return; }

        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        if (localGroupSocket == null) { return; }

        DestinationRecord destinationRecord = localGroupSocket.getDestination(0);
        if (destinationRecord == null) { return; }

        destinationRecord.getNettyChannel().sendHttpRequest(httpRequest);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public URI makeUri(String path) {
        URI uri;
        try {
            uri = new URI(path);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            host = uri.getHost() == null ? configManager.getHttpListenIp() : uri.getHost();

            // 아직 https 지원하지 않음
            if (!"http".equalsIgnoreCase(scheme)) { // && !"https".equalsIgnoreCase(scheme)) {
                logger.warn("[DashHttpMessageSender] Only HTTP(S) is supported.");
                return null;
            }
        } catch (Exception e) {
            logger.warn("[DashHttpMessageSender] URI Parsing error", e);
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

    private static class HttpMessageClientInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;
        private final DashClient dashClient;

        public HttpMessageClientInitializer(SslContext sslContext, DashClient dashClient) {
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
            p.addLast(new DashHttpClientHandler(dashClient));
        }
    }
    ////////////////////////////////////////////////////////////

}
