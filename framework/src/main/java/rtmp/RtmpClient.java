package rtmp;

import config.ConfigManager;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.BaseRTMPClientHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.flv.impl.Tag;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.*;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.service.flv.impl.FLVService;
import org.red5.server.stream.AbstractClientStream;
import org.red5.server.stream.ClientBroadcastStream;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.consumer.ConnectionConsumer;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class RtmpClient extends RTMPClient {

    //////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(RtmpClient.class);

    private final int duration = -2; // milliseconds, -2 means until end of stream
    private final String host;
    private final int port;
    private String app;
    private String name;
    private final String saveAsFileName;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private RTMPConnection conn = null;
    private final Timer timer = new Timer();
    private boolean finished = false;
    private StreamEventDispatcher streamEventDispatcher = null;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public RtmpClient(String uri, String saveAsFileName) {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        this.host = configManager.getRtmpPublishIp();
        this.port = configManager.getRtmpPublishPort();
        this.saveAsFileName = saveAsFileName + ".flv";

        if (uri.indexOf("/") == 0) {
            uri = uri.substring(1);
        }
        this.app = uri.substring(0, uri.indexOf("/"));
        this.name = uri.substring(uri.indexOf("/") + 1);

        logger.debug("[RtmpClient] Initiating, host: [{}], app: [{}], port: [{}], saveAsFileName: [{}]", host , app, port, saveAsFileName);
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public void start() {
        ///////////////////////////
        streamEventDispatcher = new StreamEventDispatcher();
        streamEventDispatcher.start(saveAsFileName);
        ///////////////////////////

        ///////////////////////////
        IPendingServiceCallback callback = call -> {
            if (call.getResult() == null) { return; }

            ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
            logger.debug("[RtmpClient] connectCallback");
            String code = (String) map.get("code");
            if (code != null) {
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    logger.warn("Rejected: {}", map.get("description"));
                    streamEventDispatcher.stop();
                    disconnect();
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    logger.debug("[RtmpClient] Connected!");
                    timer.schedule(new BandwidthStatusTask(name, this, timer), 2000L);
                } else {
                    streamEventDispatcher.stop();
                    logger.error("[RtmpClient] Unhandled response code: {}", code);
                }
            }
        };
        ///////////////////////////

        ///////////////////////////
        setStreamEventDispatcher(streamEventDispatcher);
        setStreamEventHandler(notify -> {
            logger.debug("[RtmpClient] onStreamEvent: {}", notify);
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
            String code = (String) map.get("code");
            logger.debug("[RtmpClient] <:{}", code);
            if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
                logger.warn("[RtmpClient] Requested stream was not found");
                streamEventDispatcher.stop();
                disconnect();
            } else if (StatusCodes.NS_PLAY_UNPUBLISHNOTIFY.equals(code) || StatusCodes.NS_PLAY_COMPLETE.equals(code)) {
                logger.warn("[RtmpClient] Source has stopped publishing or play is complete");
                streamEventDispatcher.stop();
                disconnect();
            }
        });
        setConnectionClosedHandler(() -> {
            logger.warn("[RtmpClient] Source connection has been closed, server will be stopped");
        });
        setExceptionHandler(throwable -> {
            throwable.printStackTrace();
            streamEventDispatcher.stop();
            disconnect();
        });
        ///////////////////////////

        ///////////////////////////
        connect(host, port, app, callback);
        ///////////////////////////
    }

    public void stop() {
        streamEventDispatcher.stop();
        disconnect();
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public int getDuration() {
        return duration;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getApp() {
        return app;
    }

    public String getName() {
        return name;
    }

    public String getSaveAsFileName() {
        return saveAsFileName;
    }

    public boolean isFinished() {
        return finished;
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private static final class StreamEventDispatcher implements IEventDispatcher {

        ////////////////////////////
        //private ITagWriter writer = null;
        private int videoTs = 0;
        private int audioTs = 0;
        private final ITag tag = new Tag();
        ////////////////////////////

        ////////////////////////////
        public void start(String saveAsFileName) {
            File file = new File(saveAsFileName);
            FLVService flvService = new FLVService();
            flvService.setGenerateMetadata(true);
            try {
                IStreamableFile flv = flvService.getStreamableFile(file);
                //writer = flv.getWriter();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            logger.debug("[StreamEventDispatcher] START");
        }

        public void stop() {
            /*if (writer != null) {
                writer.close();
                writer = null;
            }*/
            logger.debug("[StreamEventDispatcher] STOP");
        }
        ////////////////////////////

        ////////////////////////////
        @Override
        public void dispatchEvent(IEvent event) {
            logger.debug("[StreamEventDispatcher] Dispatch Event(): {}", event.toString());

            try {
                //if (writer == null) { return; }

                if (!(event instanceof IRTMPEvent)) {
                    logger.warn("skipping non rtmp event: {}", event);
                    return;
                }

                IRTMPEvent rtmpEvent = (IRTMPEvent) event;
                if (logger.isDebugEnabled()) {
                    logger.debug("[StreamEventDispatcher] rtmp event: {}", rtmpEvent.getHeader() + ", " + rtmpEvent.getClass().getSimpleName());
                }
                if (!(rtmpEvent instanceof IStreamData)) {
                    logger.warn("[StreamEventDispatcher] skipping non stream data");
                    return;
                }
                if (rtmpEvent.getHeader().getSize() == 0) {
                    logger.warn("[StreamEventDispatcher] skipping event where size == 0");
                    return;
                }

                tag.setDataType(rtmpEvent.getDataType());
                if (rtmpEvent instanceof VideoData) {
                    videoTs += rtmpEvent.getTimestamp();
                    tag.setTimestamp(videoTs);
                } else if (rtmpEvent instanceof AudioData) {
                    audioTs += rtmpEvent.getTimestamp();
                    tag.setTimestamp(audioTs);
                }

                IoBuffer data = ((IStreamData) rtmpEvent).getData().asReadOnlyBuffer();
                tag.setBodySize(data.limit());
                tag.setBody(data);
                try {
                    logger.debug("[StreamEventDispatcher] WRITING: {}", tag);

                    //writer.writeTag(tag);
                } catch (Exception e) {
                    logger.warn("[StreamEventDispatcher] dispatchEvent.writer.writeTag", e);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                logger.warn("[StreamEventDispatcher] dispatchEvent.Exception", e);
            }
        }
        ////////////////////////////

    }

    private static final class BandwidthStatusTask extends TimerTask {

        private final String name;
        private final RtmpClient rtmpClient;
        private final Timer timer;

        public BandwidthStatusTask(String name, RtmpClient rtmpClient, Timer timer) {
            this.name = name;
            this.rtmpClient = rtmpClient;
            this.timer = timer;
        }

        @Override
        public void run() {
            // check for onBWDone
            logger.debug("Bandwidth check done: {}", rtmpClient.isBandwidthCheckDone());
            // cancel this task
            this.cancel();
            // create a task to wait for subscribed
            timer.schedule(new PlayStatusTask(name, rtmpClient), 1000L);
            // 2. send FCSubscribe
            rtmpClient.subscribe(new SubscribeStreamCallBack(), new Object[] {name});
        }

    }

    private static final class PlayStatusTask extends TimerTask {

        private final String name;
        private final RtmpClient rtmpClient;

        private PlayStatusTask(String name, RtmpClient rtmpClient) {
            this.name = name;
            this.rtmpClient = rtmpClient;
        }

        @Override
        public void run() {
            logger.debug("Subscribed: {}", rtmpClient.isSubscribed());
            this.cancel();
            rtmpClient.createStream(new CreateStreamCallback(rtmpClient.getConnection(), name, rtmpClient));
        }

    }

    @Override
    public void createStream(IPendingServiceCallback callback) {
        logger.debug("[RtmpClient] createStream - callback: {}", callback);
        IPendingServiceCallback wrapper = new CreateStreamCallback(conn, name, this);
        this.invoke("createStream", null, wrapper);
    }


    private static final class SubscribeStreamCallBack implements IPendingServiceCallback {

        @Override
        public void resultReceived(IPendingServiceCall call) {
            logger.debug("SubscribeStreamCallBack.resultReceived: {}", call);
        }

    }

    private static final class CreateStreamCallback implements IPendingServiceCallback {

        private final RTMPConnection rtmpConnection;
        private final String name;
        private final RtmpClient rtmpClient;

        private CreateStreamCallback(RTMPConnection rtmpConnection, String name, RtmpClient rtmpClient) {
            this.rtmpConnection = rtmpConnection;
            this.name = name;
            this.rtmpClient = rtmpClient;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            Number streamId = (Number) call.getResult();

            if (streamId != null) {
                logger.debug("[CreateStreamCallback] Setting new net ClientBroadcastStream");
                ClientBroadcastStream clientBroadcastStream = new ClientBroadcastStream();
                clientBroadcastStream.setConnection(rtmpConnection);
                clientBroadcastStream.setStreamId(streamId);
                rtmpConnection.addClientStream(clientBroadcastStream);
            }

            ///////////////////////////////
            IClientStream clientStream = rtmpConnection.getStreamById(streamId);
            if (clientStream == null) { return; }

            if (clientStream instanceof IBroadcastStream) {
                logger.debug("[CreateStreamCallback] Getting new net ClientBroadcastStream");
                IBroadcastStream iBroadcastStream = (IBroadcastStream) clientStream;
                iBroadcastStream.addStreamListener(
                        new VideoStreamListener(
                                rtmpConnection.getScope(),
                                iBroadcastStream,
                                true,
                                name,
                                10000
                        )
                );
            }
            ///////////////////////////////

            // live buffer 0.5s / vod buffer 4s
            //if (Boolean.valueOf(PropertiesReader.getProperty("live"))) {
                rtmpConnection.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 500));
                rtmpClient.play(streamId, name, -1, -1);
            /*} else {
                rtmpConnection.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 4000));
                play(streamId, PropertiesReader.getProperty("name"), 0, -1);
            }*/
        }
    };
    //////////////////////////////////////////////////////////

    @Override
    public void connectionOpened(RTMPConnection conn) {
        logger.debug("connection opened");
        super.connectionOpened(conn);
        this.conn = conn;
    }

    @Override
    public void connectionClosed(RTMPConnection conn) {
        logger.debug("connection closed");
        super.connectionClosed(conn);
        System.exit(0);
    }
    //////////////////////////////////////////////////////////

}
