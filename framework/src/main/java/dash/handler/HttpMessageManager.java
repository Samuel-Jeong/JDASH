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
import network.definition.NetAddress;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;

import java.util.concurrent.TimeUnit;

public class HttpMessageManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(HttpMessageManager.class);

    public static final String SERVER_NAME = "JDASH";
    public static final String TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String TYPE_JSON = "application/json; charset=UTF-8";
    public static final String HTTP_SCHEDULE_KEY = "HTTP_MESSAGE_HANDLE";

    private final ScheduleManager scheduleManager;
    private final SocketManager socketManager;
    private final HttpMessageRouteTable routeTable;

    private final NetAddress localListenAddress;
    private final ConcurrentCyclicFIFO<Object[]> httpMessageQueue;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMessageManager(ScheduleManager scheduleManager, SocketManager socketManager) {
        this.scheduleManager = scheduleManager;
        this.socketManager = socketManager;
        this.routeTable = new HttpMessageRouteTable();
        this.httpMessageQueue = new ConcurrentCyclicFIFO<>();

        localListenAddress = new NetAddress("127.0.0.1", 5000,true, SocketProtocol.TCP);
        socketManager.addSocket(localListenAddress, new HttpMessageServerInitializer());
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start() {
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
        localGroupSocket.getListenSocket().closeListenChannel();
        logger.debug("[HttpMessageManager] CLOSE [{}]", localGroupSocket);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void putMessage(Object[] httpObject) {
        this.httpMessageQueue.offer(httpObject);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void get(final String path, final HttpMessageHandler handler) {
        this.routeTable.addRoute(new HttpMessageRoute(HttpMethod.GET, path, handler));
    }

    public void post(final String path, final HttpMessageHandler handler) {
        this.routeTable.addRoute(new HttpMessageRoute(HttpMethod.POST, path, handler));
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private class HttpMessageServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) {
            final ChannelPipeline p = ch.pipeline();
            p.addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192, false));
            p.addLast("aggregator", new HttpObjectAggregator(100 * 1024 * 1024));
            p.addLast("encoder", new HttpResponseEncoder());
            p.addLast("handler", new DashHttpMessageFilter(routeTable));
        }
    }
    ////////////////////////////////////////////////////////////

}