package dash;

import org.junit.Test;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.mpd.MPD;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class DashTest {

    private static final Logger logger = LoggerFactory.getLogger(DashTest.class);

    @Test
    public void test() {
        /////////////////////////////////////////////
        // 1) MPD PARSING TEST
        /*MPD mpd = parseMpdTest(dashManager);
        Assert.assertNotNull(mpd);*/
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        // 2) HTTP COMMUNICATION TEST
        /*DashHttpMessageSender dashHttpSender = new DashHttpMessageSender();
        dashHttpSender.start();

        TimeUnit timeUnit = TimeUnit.SECONDS;
        try {
            dashHttpSender.sendSampleMessage();

            *//*timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();

            timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();*//*

            timeUnit.sleep(2);
        } catch (Exception e) {
            logger.warn("DashTest.test.Exception", e);
        }

        dashHttpSender.stop();*/
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        //testRtmpSubscribe1();
        /////////////////////////////////////////////
    }

    public static MPD parseMpdTest(DashManager dashManager) {
        return dashManager.parseMpd("/Users/jamesj/GIT_PROJECTS/JDASH/src/test/resources/mpd_example4.xml");
    }

    public void testRtmpSubscribe1() {
        String URI = "rtmp://192.168.5.222:1940/live/jamesj";
        String outputPath = "/Users/jamesj/GIT_PROJECTS/JDASH/src/test/resources/live/jamesj.mp4";
        String appName = "JDASH_PUB_TEST";

        try {
            final RTMPClient client = new RTMPClient();
            client.setStreamEventDispatcher(new StreamEventDispatcher());
            client.setStreamEventHandler(notify -> {
                logger.debug("onStreamEvent: {}", notify);
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
                String code = (String) map.get("code");
                logger.debug("<:{}", code);
                if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
                    logger.warn("Requested stream was not found");
                    client.disconnect();
                } else if (StatusCodes.NS_PLAY_UNPUBLISHNOTIFY.equals(code) || StatusCodes.NS_PLAY_COMPLETE.equals(code)) {
                    logger.warn("Source has stopped publishing or play is complete");
                    client.disconnect();
                }
            });
            client.setConnectionClosedHandler(() -> {
                logger.warn("Source connection has been closed, server will be stopped");
            });
            client.setExceptionHandler(throwable -> {
                throwable.printStackTrace();
                System.exit(1);
            });


            Timer timer = new Timer();
            Map<String, Object> defParams = client.makeDefaultConnectionParams("192.168.5.222", 1940, "live");
            if (logger.isDebugEnabled()) {
                for (Map.Entry<String, Object> e : defParams.entrySet()) {
                    logger.debug("Connection property: {} = {}", e.getKey(), e.getValue());
                }
            }

            client.connect("192.168.5.222", 1940, defParams, call -> {
                if (call.getResult() == null) { return; }

                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                logger.debug("connectCallback");
                String code = (String) map.get("code");
                if (code != null) {
                    if ("NetConnection.Connect.Rejected".equals(code)) {
                        logger.warn("Rejected: {}", map.get("description"));
                        client.disconnect();
                    } else if ("NetConnection.Connect.Success".equals(code)) {
                        logger.debug("@@@");
                        timer.schedule(new BandwidthStatusTask("jamesj", client, timer), 2000L);
                    } else {
                        logger.error("Unhandled response code: {}", code);
                    }
                }
            });

            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            logger.warn("[RTMP] Exception", e);
            System.exit(1);
        }
    }

    private static final class StreamEventDispatcher implements IEventDispatcher {

        @Override
        public void dispatchEvent(IEvent event) {
            System.out.println("ClientStream.dispachEvent()" + event.toString());
            try {
                logger.debug("RTMPMessage.build((IRTMPEvent) event): {}", RTMPMessage.build((IRTMPEvent) event));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static final class BandwidthStatusTask extends TimerTask {

        private final String sourceStreamName;
        private final RTMPClient rtmpClient;
        private final Timer timer;

        public BandwidthStatusTask(String sourceStreamName, RTMPClient rtmpClient, Timer timer) {
            this.sourceStreamName = sourceStreamName;
            this.rtmpClient = rtmpClient;
            this.timer = timer;
        }

        @Override
        public void run() {
            // check for onBWDone
            System.out.println("Bandwidth check done: " + rtmpClient.isBandwidthCheckDone());
            // cancel this task
            this.cancel();
            // create a task to wait for subscribed
            timer.schedule(new PlayStatusTask(sourceStreamName, rtmpClient), 1000L);
            // 2. send FCSubscribe
            rtmpClient.subscribe(new SubscribeStreamCallBack(), new Object[] { sourceStreamName });
        }

    }

    private static final class PlayStatusTask extends TimerTask {

        private final String sourceStreamName;
        private final RTMPClient rtmpClient;

        private PlayStatusTask(String sourceStreamName, RTMPClient rtmpClient) {
            this.sourceStreamName = sourceStreamName;
            this.rtmpClient = rtmpClient;
        }

        @Override
        public void run() {
            // checking subscribed
            System.out.println("Subscribed: " + rtmpClient.isSubscribed());
            // cancel this task
            this.cancel();
            // 3. create stream
            rtmpClient.createStream(new CreateStreamCallback(sourceStreamName, rtmpClient));
        }

    }

    private static final class CreateStreamCallback implements IPendingServiceCallback {

        private final String sourceStreamName;
        private final RTMPClient rtmpClient;

        private CreateStreamCallback(String sourceStreamName, RTMPClient rtmpClient) {
            this.sourceStreamName = sourceStreamName;
            this.rtmpClient = rtmpClient;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("resultReceived: " + call);
            Double streamId = (Double) call.getResult();
            System.out.println("stream id: " + streamId);
            // send our buffer size request
            if (sourceStreamName.endsWith(".flv") || sourceStreamName.endsWith(".f4v") || sourceStreamName.endsWith(".mp4")) {
                rtmpClient.play(streamId, sourceStreamName, 0, -1);
            } else {
                rtmpClient.play(streamId, sourceStreamName, -1, 0);
            }
        }

    }

    private static final class SubscribeStreamCallBack implements IPendingServiceCallback {

        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("resultReceived: " + call);
        }

    }

}
