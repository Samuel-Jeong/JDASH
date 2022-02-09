package dash.handler;

import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpMessageRoute;
import dash.handler.definition.HttpMessageRouteTable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.schedule.ScheduleManager;

import java.util.List;

public class HttpMessageManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(HttpMessageManager.class);

    static {
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        System.setProperty("javax.net.ssl.trustStore", "servercert.keystore");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
    }

    //static final boolean SSL = System.getProperty("ssl") != null;
    //static final boolean SSL = System.getProperty("javax.net.ssl.trustStore") != null;
    static final boolean SSL = false;

    public static final String SERVER_NAME = "JDASH";
    public static final String TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String TYPE_DASH_XML = "application/dash+xml; charset=UTF-8";
    public static final String HTTP_SCHEDULE_KEY = "HTTP_MESSAGE_HANDLE";

    private final ScheduleManager scheduleManager;
    private final SocketManager socketManager;
    private final HttpMessageRouteTable routeTable;

    private final NetAddress localListenAddress;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMessageManager(ScheduleManager scheduleManager, SocketManager socketManager) {
        this.scheduleManager = scheduleManager;
        this.socketManager = socketManager;
        this.routeTable = new HttpMessageRouteTable();

        localListenAddress = new NetAddress(
                AppInstance.getInstance().getConfigManager().getHttpListenIp(),
                AppInstance.getInstance().getConfigManager().getHttpListenPort(),
                true, SocketProtocol.TCP
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start() {
        SslContext sslContext = null;
        try {
            if (SSL) {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslContext = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
                logger.debug("[HttpMessageManager] SSL is enabled.");
            } else {
                logger.debug("[HttpMessageManager] SSL is disabled.");
            }
        } catch (Exception e) {
            logger.warn("[HttpMessageManager] SSL Initialization error.", e);
        }
        socketManager.addSocket(localListenAddress, new HttpMessageServerInitializer(sslContext));

        // OPEN LISTEN CHANNEL
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        localGroupSocket.getListenSocket().openListenChannel();
        logger.debug("[HttpMessageManager] OPEN [{}]", localGroupSocket);
    }

    public void stop() {
        // STOP ALL JOBS
        scheduleManager.stopAll(HTTP_SCHEDULE_KEY);

        // CLOSE LISTEN CHANNEL
        GroupSocket localGroupSocket = socketManager.getSocket(localListenAddress);
        if (localGroupSocket != null) {
            localGroupSocket.getListenSocket().closeListenChannel();
        }
        logger.debug("[HttpMessageManager] CLOSE [{}]", localGroupSocket);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void get(final String path, final HttpMessageHandler handler) {
        this.routeTable.addRoute(new HttpMessageRoute(HttpMethod.GET, path, handler));
    }

    public void post(final String path, final HttpMessageHandler handler) {
        this.routeTable.addRoute(new HttpMessageRoute(HttpMethod.POST, path, handler));
    }

    public void clear() {
        this.routeTable.clear();
    }

    public List<HttpMessageRoute> getAllRoutes() {
        return this.routeTable.getRoutes();
    }

    public List<String> getAllUris() {
        return this.routeTable.getUriList();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private class HttpMessageServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslContext;

        public HttpMessageServerInitializer(SslContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            // Enable HTTPS if necessary.
            if (sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc()));
            }

            p.addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192, false));
            p.addLast("aggregator", new HttpObjectAggregator(100 * 1024 * 1024));
            p.addLast("encoder", new HttpResponseEncoder());
            p.addLast("handler", new DashHttpMessageFilter(routeTable));
        }
    }
    ////////////////////////////////////////////////////////////

}