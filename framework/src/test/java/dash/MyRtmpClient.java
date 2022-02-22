package dash;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.flv.impl.Tag;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.ICommand;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.service.flv.impl.FLVService;
import org.red5.server.stream.AbstractClientStream;
import org.red5.server.stream.IStreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public class MyRtmpClient extends RTMPClient {

    //////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MyRtmpClient.class);

    private static final String host = "192.168.5.222";
    private static final int port = 1940;
    private static final String name = "jamesj";
    private static final String app = "live";
    private static final String saveAsFileName = "/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/jamesj.flv";
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private static RTMPConnection conn = null;
    private static ITagWriter writer = null;

    private static int videoTs = 0;
    private static int audioTs = 0;

    private static MyNetStream stream = null;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public static void main(String[] args) {

        final int duration = 3000; // milliseconds, -2 means until end of stream

        final MyRtmpClient client = new MyRtmpClient();
        /*client.setStreamEventDispatcher(new StreamEventDispatcher());
        client.setStreamEventHandler(notify -> {
            logger.debug("onStreamEvent: {}", notify);
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
            String code = (String) map.get("code");
            logger.debug("<:{}", code);
            if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
                logger.warn("Requested stream was not found");
                client.disconnect();
            } else if (StatusCodes.NS_PLAY_UNPUBLISHNOTIFY.equals(code) || StatusCodes.NS_PLAY_COMPLETE.equals(code)) {
                logger.debug("Source has stopped publishing or play is complete");
                client.disconnect();
            }
        });
        client.setExceptionHandler(throwable -> {
            throwable.printStackTrace();
            System.exit(1);
        });*/
        logger.debug("connecting, host: " + host + ", app: " + app + ", port: " + port);

        IPendingServiceCallback callback = new IPendingServiceCallback() {
            public void resultReceived(IPendingServiceCall call) {
                if ("connect".equals(call.getServiceMethodName())) {
                    client.createStream(this);
                } else if ("createStream".equals(call.getServiceMethodName())) {
                    Double streamIdDouble = (Double) call.getResult();
                    int streamId = streamIdDouble.intValue();
                    logger.debug("createStream result stream id: " + streamId);
                    logger.debug("playing video by name: " + name);
                    client.play(streamId, name, 0, duration);
                }
            }
        };

        client.connect(host, port, app, callback);
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    @Override
    public void connectionOpened(RTMPConnection conn) {
        logger.debug("connection opened");
        super.connectionOpened(conn);
        this.conn = conn;
        init();
    }

    @Override
    public void connectionClosed(RTMPConnection conn) {
        logger.debug("connection closed");
        super.connectionClosed(conn);
        if (writer != null) {
            writer.close();
            writer = null;
        }
        System.exit(0);
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    @Override
    public void createStream(IPendingServiceCallback callback) {
        logger.debug("create stream");
        IPendingServiceCallback wrapper = new CreateStreamCallBack(callback);
        invoke("createStream", null, wrapper);
    }

   /* @Override
    protected void onCommand(RTMPConnection conn, Channel channel, Header header, ICommand command) {
        super.onCommand(conn, channel, header, command);
        logger.debug("onInvoke, header = {}", header.toString());
        logger.debug("onInvoke, command = {}", command.toString());
        Object obj = command.getCall().getArguments().length > 0 ? command.getCall().getArguments()[0] : null;
        if (obj instanceof Map) {
            Map<String, String> map = (Map<String, String>) obj;
            String code = map.get("code");
            if (StatusCodes.NS_PLAY_STOP.equals(code)) {
                logger.debug("onInvoke, code == NetStream.Play.Stop, disconnecting");
                disconnect();
            }
        }
    }*/
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private void init() {
        File file = new File(saveAsFileName);
        FLVService flvService = new FLVService();
        flvService.setGenerateMetadata(true);
        try {
            IStreamableFile flv = flvService.getStreamableFile(file);
            writer = flv.getWriter();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class CreateStreamCallBack implements IPendingServiceCallback {

        private final IPendingServiceCallback wrapped;

        public CreateStreamCallBack(IPendingServiceCallback wrapped) {
            this.wrapped = wrapped;
        }

        public void resultReceived(IPendingServiceCall call) {
            Double streamIdDouble = (Double) call.getResult();
            if (conn != null && streamIdDouble != null) {
                int streamId = streamIdDouble.intValue();
                stream = new MyNetStream();
                stream.setConnection(conn);
                stream.setStreamId(streamId);
                stream.setName(name);
                stream.setScope(conn.getScope());
                stream.start();
                conn.addClientStream(stream);
            }
            wrapped.resultReceived(call);
        }

    }

    private static final class StreamEventDispatcher implements IEventDispatcher {

        @Override
        public void dispatchEvent(IEvent event) {
            logger.debug("ClientStream.dispatchEvent(): {}", event.toString());
            if (stream != null) {
                stream.dispatchEvent(event);
            }
        }

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private static class MyNetStream extends AbstractClientStream implements IEventDispatcher {

        @Override
        public void close() {
            logger.debug("MyNetStream.close");
        }

        @Override
        public void start() {
            logger.debug("MyNetStream.start");
        }

        @Override
        public void stop() {
            logger.debug("MyNetStream.stop");
        }

        @Override
        public void dispatchEvent(IEvent event) {
            if (!(event instanceof IRTMPEvent)) {
                logger.debug("skipping non rtmp event: " + event);
                return;
            }
            IRTMPEvent rtmpEvent = (IRTMPEvent) event;
            if (logger.isDebugEnabled()) {
                logger.debug("rtmp event: " + rtmpEvent.getHeader() + ", "
                        + rtmpEvent.getClass().getSimpleName());
            }
            if (!(rtmpEvent instanceof IStreamData)) {
                logger.debug("skipping non stream data");
                return;
            }
            if (rtmpEvent.getHeader().getSize() == 0) {
                logger.debug("skipping event where size == 0");
                return;
            }

            ITag tag = new Tag();
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
                logger.debug("WRITING: {}", tag);
                writer.writeTag(tag);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    //////////////////////////////////////////////////////////

}
