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
import service.ResourceManager;
import service.scheduler.schedule.ScheduleManager;

import java.net.URI;

public class DashHttpMessageSender {

    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageSender.class);

    private static final String URL = System.getProperty("url", "http://127.0.0.1:8080/");
    private URI uri;
    private String host;

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

        socketManager = new SocketManager(
                baseEnvironment,
                false, false,
                10, 500000, 500000
        );

        localListenAddress = new NetAddress("127.0.0.1", 6000,true, SocketProtocol.TCP);
        remoteAddress = new NetAddress("127.0.0.1", 5000, true, SocketProtocol.TCP);

        socketManager.addSocket(localListenAddress, new HttpMessageServerInitializer());

        try {
            uri = new URI(URL);
            host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            /*int port = uri.getPort();
            if (port == -1) {
                String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
                if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }*/
        } catch (Exception e) {
            logger.warn("[DashHttpSender] URI Parsing error", e);
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
                new HttpMessageClientInitializer(null)
        );

        DestinationRecord destinationRecord = localGroupSocket.getDestination(0);
        destinationRecord.getNettyChannel().openConnectChannel(remoteAddress.getAddressString(), remoteAddress.getPort());
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
        private final SslContext sslCtx;

        public HttpMessageClientInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            // Enable HTTPS if necessary.
            if (sslCtx != null) {
                p.addLast(sslCtx.newHandler(ch.alloc()));
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
