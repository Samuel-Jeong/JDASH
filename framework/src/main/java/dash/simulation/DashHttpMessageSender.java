package dash.simulation;

import instance.BaseEnvironment;
import instance.DebugLevel;
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
import service.scheduler.schedule.ScheduleManager;
import service.system.ResourceManager;

import java.net.URI;

public class DashHttpMessageSender {

    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageSender.class);

    private static final String URL =
            System.getProperty(
                    "url",
                    "http://127.0.0.1:5000/Seoul.mp4"
            );

    private URI uri = null;
    private String host = null;
    private SslContext sslContext = null;

    ////////////////////////////////////////////////////////////
    private final BaseEnvironment baseEnvironment;
    private final SocketManager socketManager;
    private final NetAddress localListenAddress;
    private final NetAddress remoteAddress;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageSender() {
        baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                new ResourceManager(5000, 7000),
                DebugLevel.DEBUG
        );
        baseEnvironment.start();

        socketManager = new SocketManager(
                baseEnvironment,
                false, false,
                10, 500000, 500000
        );

        localListenAddress = new NetAddress(
                "127.0.0.1", 6000,
                true, SocketProtocol.TCP
        );
        remoteAddress = new NetAddress(
                AppInstance.getInstance().getConfigManager().getHttpListenIp(),
                AppInstance.getInstance().getConfigManager().getHttpListenPort(),
                true, SocketProtocol.TCP
        );

        socketManager.addSocket(localListenAddress, new HttpMessageServerInitializer());

        try {
            uri = new URI(URL);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            /*int port = uri.getPort();
            if (port == -1) {
                if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }*/

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                logger.warn("[DashHttpMessageSender] Only HTTP(S) is supported.");
                return;
            }

            // Configure SSL context if necessary.
            final boolean isEnabledSsl = "https".equalsIgnoreCase(scheme);
            if (isEnabledSsl) {
                sslContext = SslContext.newClientContext();
            }
        } catch (Exception e) {
            logger.warn("[DashHttpMessageSender] URI Parsing error", e);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start() {
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        localGroupSocket.getListenSocket().openListenChannel();

        localGroupSocket.addDestination(
                remoteAddress,
                null,
                0,
                new HttpMessageClientInitializer(sslContext)
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
    public void sendSampleMessage() {
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        if (localGroupSocket == null) { return; }

        DestinationRecord destinationRecord = localGroupSocket.getDestination(0);
        if (destinationRecord == null) { return; }

        destinationRecord.getNettyChannel().sendHttpRequest(
                makeSampleHttpRequestMessage(uri, host)
        );
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
    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public NetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public NetAddress getLocalListenAddress() {
        return localListenAddress;
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

        public HttpMessageClientInitializer(SslContext sslContext) {
            this.sslContext = sslContext;
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
            p.addLast(new DashHttpClientHandler());
        }
    }
    ////////////////////////////////////////////////////////////

    public HttpRequest makeSampleHttpRequestMessage(URI uri, String host) {
        // Prepare the HTTP request.
        HttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath()
        );

        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

        // Set some example cookies.
        request.headers().set(
                HttpHeaderNames.COOKIE,
                ClientCookieEncoder.encode(
                        new DefaultCookie("my-cookie", "foo"),
                        new DefaultCookie("another-cookie", "bar")
                )
        );
        return request;
    }

}
