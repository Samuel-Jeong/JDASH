package dash;

import com.xuggle.xuggler.*;
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

        //testRtmpGetStreamInformation();
        //testRtmpSubscribe1();
        testRtmpSubscribe2();
    }

    public static MPD parseMpdTest(DashManager dashManager) {
        return dashManager.parseMpd("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/mpd_example4.xml");
    }

    public void testRtmpGetStreamInformation() {
        String URI = "rtmp://192.168.5.222:1940/live/jamesj";
        String outputPath = "/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/jamesj.mpd";

        try {
            // first we create a Xuggler container object
            IContainer container = IContainer.make();

            // we attempt to open up the container
            int result = container.open(URI, IContainer.Type.READ, null);

            // check if the operation was successful
            if (result<0)
                throw new RuntimeException("Failed to open media file");

            // query how many streams the call to open found
            int numStreams = container.getNumStreams();

            // query for the total duration
            long duration = container.getDuration();

            // query for the file size
            long fileSize = container.getFileSize();

            // query for the bit rate
            long bitRate = container.getBitRate();

            System.out.println("Number of streams: " + numStreams);
            System.out.println("Duration (ms): " + duration);
            System.out.println("File Size (bytes): " + fileSize);
            System.out.println("Bit Rate: " + bitRate);

            // iterate through the streams to print their meta data
            for (int i=0; i<numStreams; i++) {

                // find the stream object
                IStream stream = container.getStream(i);

                // get the pre-configured decoder that can decode this stream;
                IStreamCoder coder = stream.getStreamCoder();

                System.out.println("*** Start of Stream Info ***");

                System.out.printf("stream %d: ", i);
                System.out.printf("type: %s; ", coder.getCodecType());
                System.out.printf("codec: %s; ", coder.getCodecID());
                System.out.printf("duration: %s; ", stream.getDuration());
                System.out.printf("start time: %s; ", container.getStartTime());
                System.out.printf("timebase: %d/%d; ",
                        stream.getTimeBase().getNumerator(),
                        stream.getTimeBase().getDenominator());
                System.out.printf("coder tb: %d/%d; ",
                        coder.getTimeBase().getNumerator(),
                        coder.getTimeBase().getDenominator());
                System.out.println();

                if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
                    System.out.printf("sample rate: %d; ", coder.getSampleRate());
                    System.out.printf("channels: %d; ", coder.getChannels());
                    System.out.printf("format: %s", coder.getSampleFormat());
                }
                else if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
                    System.out.printf("width: %d; ", coder.getWidth());
                    System.out.printf("height: %d; ", coder.getHeight());
                    System.out.printf("format: %s; ", coder.getPixelType());
                    System.out.printf("frame-rate: %5.2f; ", coder.getFrameRate().getDouble());
                }

                System.out.println();
                System.out.println("*** End of Stream Info ***");

            }

        } catch (Exception e) {
            logger.warn("[RTMP] Exception", e);
            System.exit(1);
        }
    }

    public void testRtmpSubscribe1() {
        String URI = "rtmp://192.168.5.222:1940/live/jamesj";
        String outputPath = "/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/jamesj.mp4";

        try {
            IContainer readContainer = IContainer.make();
            IContainer writeContainer = IContainer.make();

            int videoStreamId = 0;
            int i = readContainer.open(URI, IContainer.Type.READ, null, true, false);
            writeContainer.open(outputPath, IContainer.Type.WRITE, null);

            if (i >= 0) {
                IStream inVideoStream = readContainer.getStream(0);
                IStreamCoder inVideocoder = inVideoStream.getStreamCoder();

                inVideocoder.setCodec(ICodec.ID.CODEC_ID_H264);
                inVideocoder.setFrameRate(IRational.make(30));
                inVideocoder.setNumPicturesInGroupOfPictures(30);
                inVideocoder.setTimeBase(inVideoStream.getTimeBase());
                inVideocoder.setBitRate(50000);
                inVideocoder.setHeight(640);
                inVideocoder.setWidth(320);

                IStream outVideoStream = writeContainer.addNewStream(0);
                outVideoStream.setStreamCoder(inVideocoder);

                int video = inVideocoder.open();

                if (video >= 0) {
                    System.out.println("Good videoStream");
                } else {
                    System.out.println("Wrong videoStream");
                }

                IStream inAudioStream = readContainer.getStream(1);
                IStreamCoder inAudioCoder = inAudioStream.getStreamCoder();

                inAudioCoder.setCodec(ICodec.ID.CODEC_ID_AAC);
                inAudioCoder.setBitRate(128000);
                inAudioCoder.setChannels(1);
                inAudioCoder.setSampleRate(44100);

                inAudioCoder.setTimeBase(inAudioStream.getTimeBase());

                IStream outAudioStream = writeContainer.addNewStream(1);
                outAudioStream.setStreamCoder(inAudioCoder);

                int audio = inAudioCoder.open();
                if (audio >= 0) {
                    System.out.println("Good audioStream");
                } else {
                    System.out.println("Wrong audioStream");
                }

                int header = writeContainer.writeHeader();


                if (header == 0) {
                    System.out.println("good header");
                } else {
                    System.out.println("wrong header" + header);
                }

                IPacket packet = IPacket.make();
                //File file = new File("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/data0-1000.manu");
                //FileChannel wChannel = new FileOutputStream(file, false).getChannel();
                long initTS = 1000;

                //ByteBuffer bbuf;
                while (readContainer.readNextPacket(packet) >= 0 && packet.isComplete()) {
                    //bbuf = packet.getByteBuffer();
                    if(((packet.getTimeStamp() - initTS) >= 1000) && packet.isKeyPacket()) {
                        //file = new File("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/data" + initTS + "-" + packet.getTimeStamp() + ".manu");
                        initTS=packet.getTimeStamp();
                        //wChannel = new FileOutputStream(file, false).getChannel();
                    }

                    //if(bbuf != null) { wChannel.write(bbuf); }

                    writeContainer.writePacket(packet);
                    //System.out.println(packet.getTimeStamp());
                }

                //writeContainer.writeTrailer();
                writeContainer.close();
                //wChannel.close();
            } else {
                System.out.print("Wrong!!!");
            }
        } catch (Exception e) {
            logger.warn("[RTMP] Exception", e);
            System.exit(1);
        }
    }

    public void testRtmpSubscribe2() {
        String URI = "rtmp://192.168.5.222:1940/live/jamesj";
        String outputPath = "/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/live/jamesj.mp4";
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
