package rtmp;

import config.ConfigManager;
import dash.DashManager;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.*;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.IStreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import tool.parser.mpd.*;
import tool.parser.mpd.descriptor.Descriptor;
import util.module.FileManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RtmpClient extends RTMPClient {

    //////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(RtmpClient.class);

    private final int duration = -2; // milliseconds, -2 means until end of stream
    private final String host;
    private final int port;
    private final String app;
    private final String name;
    private final String saveAsManifestFileName;
    private final String saveAsVideoFileName;
    private final String saveAsAudioFileName;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private RTMPConnection rtmpConnection = null;
    private final Timer timer = new Timer();
    private boolean finished = false;
    private StreamEventHandler streamEventHandler = null;
    private StreamEventDispatcher streamEventDispatcher = null;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public RtmpClient(String uri, String saveAsFileName) {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        this.host = configManager.getRtmpPublishIp();
        this.port = configManager.getRtmpPublishPort();
        this.saveAsManifestFileName = saveAsFileName + ".mpd";
        this.saveAsVideoFileName = saveAsFileName + "_chunk0_%05d.m4s";
        this.saveAsAudioFileName = saveAsFileName + "_chunk1_%05d.m4s";

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
        streamEventDispatcher.start();

        streamEventHandler = new StreamEventHandler();
        ///////////////////////////

        ///////////////////////////
        setStreamEventDispatcher(streamEventDispatcher);
        setStreamEventHandler(streamEventHandler);
        setConnectionClosedHandler(() -> logger.warn("[RtmpClient] Source connection has been closed, server will be stopped"));
        setExceptionHandler(throwable -> {
            throwable.printStackTrace();
            streamEventDispatcher.stop();
            disconnect();
        });
        ///////////////////////////

        ///////////////////////////
        IPendingServiceCallback callback = call -> {
            if (call.getResult() == null) {
                return;
            }

            ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
            logger.debug("[RtmpClient] connectCallback");
            String code = (String) map.get("code");
            if (code != null) {
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    logger.warn("[RtmpClient] Rejected: {}", map.get("description"));
                    streamEventDispatcher.stop();
                    disconnect();
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    logger.debug("[RtmpClient] Connected!");

                    timer.schedule(
                            new BandwidthStatusTask(name, timer),
                            2000L
                    );
                } else {
                    streamEventDispatcher.stop();
                    logger.error("[RtmpClient] Unhandled response code: {}", code);
                }
            }
        };
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

    public boolean isFinished() {
        return finished;
    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private final class StreamEventDispatcher implements IEventDispatcher {

        ////////////////////////////
        private static final int SEGMENT_DURATION = 10000;
        private static final int SEGMENT_GAP = 5;

        private final String manifestFileName = saveAsManifestFileName;

        private File videoFile = null;
        private String curVideoName = saveAsVideoFileName;
        private int curVideoSeqNum = 1;
        private int videoTs = 0;
        private int curVideoTsGap = 0;
        private int prevVideoTsGap = 0;
        private int videoOffset = 0;
        private int curVideoCount = 0;

        private File audioFile = null;
        private String curAudioName = saveAsAudioFileName;
        private int curAudioSeqNum = 1;
        private int audioTs = 0;
        private int curAudioTsGap = 0;
        private int prevAudioTsGap = 0;
        private int audioOffset = 0;
        private int curAudioCount = 0;
        ////////////////////////////

        ////////////////////////////
        public void start() {
            logger.debug("[RtmpClient.StreamEventDispatcher] START");

            curVideoName = String.format(saveAsVideoFileName, curVideoSeqNum);
            videoFile = new File(curVideoName);

            curAudioName = String.format(saveAsAudioFileName, curAudioSeqNum);
            audioFile = new File(curAudioName);
        }

        public void stop() {
            logger.debug("[RtmpClient.StreamEventDispatcher] STOP");
        }
        ////////////////////////////

        ////////////////////////////
        @Override
        public void dispatchEvent(IEvent event) {
            logger.debug("[RtmpClient.StreamEventDispatcher] Dispatch Event(): {}", event.toString());

            try {
                //////////////
                // GET RTMP EVENT
                if (!(event instanceof IRTMPEvent)) {
                    logger.warn("[RtmpClient.StreamEventDispatcher] skipping non rtmp event: {}", event);
                    return;
                }
                IRTMPEvent rtmpEvent = (IRTMPEvent) event;
                if (logger.isDebugEnabled()) {
                    logger.debug("[RtmpClient.StreamEventDispatcher] rtmp event: {}", rtmpEvent.getHeader() + ", " + rtmpEvent.getClass().getSimpleName());
                }
                if (!(rtmpEvent instanceof IStreamData)) {
                    logger.warn("[RtmpClient.StreamEventDispatcher] skipping non stream data");
                    return;
                }
                if (rtmpEvent.getHeader().getSize() == 0) {
                    logger.warn("[RtmpClient.StreamEventDispatcher] skipping event where size == 0");
                    return;
                }
                //////////////

                //////////////
                // SAVE DATA
                InputStream inputStream = ((IStreamData) rtmpEvent).getData().asInputStream();
                byte[] data = inputStream.readAllBytes();
                if (rtmpEvent instanceof VideoData) {
                    videoTs += rtmpEvent.getTimestamp();

                    if (prevVideoTsGap > 0) {
                        curVideoTsGap = videoTs - prevVideoTsGap;
                    } else {
                        prevVideoTsGap = videoTs;
                    }

                    if (curVideoTsGap >= SEGMENT_DURATION) {
                        curVideoName = String.format(saveAsVideoFileName, ++curVideoSeqNum);
                        videoFile = new File(curVideoName);

                        prevVideoTsGap = 0;
                        curVideoTsGap = 0;
                        curVideoCount++;
                    }

                    FileManager.writeBytes(videoFile, data, true);
                    videoOffset += data.length;
                    logger.debug("[RtmpClient.StreamEventDispatcher] [prevVideoTsGap={}, curVideoTsGap={}] VIDEO(curVideoName={}, offset={}, timestamp={})",
                            prevVideoTsGap, curVideoTsGap, curVideoName, videoOffset, videoTs
                    );
                } else if (rtmpEvent instanceof AudioData) {
                    audioTs += rtmpEvent.getTimestamp();

                    if (prevAudioTsGap > 0) {
                        curAudioTsGap = audioTs - prevAudioTsGap;
                    } else {
                        prevAudioTsGap = audioTs;
                    }

                    if (curAudioTsGap >= SEGMENT_DURATION) {
                        curAudioName = String.format(saveAsAudioFileName, ++curAudioSeqNum);
                        audioFile = new File(curAudioName);

                        prevAudioTsGap = 0;
                        curAudioTsGap = 0;
                        curAudioCount++;
                    }

                    FileManager.writeBytes(audioFile, data, true);
                    audioOffset += data.length;
                    logger.debug("[RtmpClient.StreamEventDispatcher] [prevAudioTsGap={}, curAudioTsGap={}] AUDIO(curAudioName={}, offset={}, timestamp={})",
                            prevAudioTsGap, curAudioTsGap, curAudioName, audioOffset, audioTs
                    );
                }
                //////////////

                //////////////
                // GENERATE MPD
                if (curVideoCount > SEGMENT_GAP && curAudioCount > SEGMENT_GAP) {
                    //////////////
                    // SEGMENT LIST (URL)
                    List<SegmentURL> videoSegmentURLs = new ArrayList<>();
                    for (int i = curVideoSeqNum; i <= curVideoCount; i++) {
                        videoSegmentURLs.add(
                                SegmentURL.builder()
                                        .withMedia(String.format(saveAsVideoFileName, i))
                                        .build()
                        );
                    }

                    List<SegmentURL> audioSegmentURLs = new ArrayList<>();
                    for (int i = curVideoSeqNum; i <= curVideoCount; i++) {
                        audioSegmentURLs.add(
                                SegmentURL.builder()
                                        .withMedia(String.format(saveAsAudioFileName, i))
                                        .build()
                        );
                    }
                    //////////////

                    //////////////
                    // SEGMENT TIMELINE
                    //List<Segment> videoSegments = new ArrayList<>();
                    /*for (int i = 0; i < curVideoCount; i++) {
                        videoSegments.add(
                                Segment.builder()
                                        .withT()
                                        .withD()
                                        .withR()
                                        //.withN()
                                        .build()
                        );
                    }*/

                    //List<Segment> audioSegments = new ArrayList<>();
                    /*for (int i = 0; i < curAudioCount; i++) {
                        audioSegments.add(
                                Segment.builder()
                                        .withT()
                                        .withD()
                                        .withR()
                                        //.withN()
                                        .build()
                        );
                    }*/
                    //////////////

                    //////////////
                    // REPRESENTATIONS
                    List<Representation> videoRepresentations = new ArrayList<>();
                    for (int i = 0; i < 1; i++) { // TODO: 화질 개수 설정
                        videoRepresentations.add(
                                Representation.builder()
                                        .withId(String.valueOf(i))
                                        .withBandwidth(128000)
                                        .withCodecs("avc1")
                                        .withHeight(1280)
                                        .withWidth(720)
                                        .withSar(new Ratio(1L, 1L))
                                        .withMimeType("video/mp4")
                                        /*.withSegmentBase(
                                                SegmentBase.builder()
                                                        .withInitialization(
                                                                URLType.builder()
                                                                        .withSourceURL()
                                                                        .build()
                                                        )
                                                        .build()
                                        )*/
                                        .withSegmentList(
                                                SegmentList.builder()
                                                        .withDuration((long) (SEGMENT_DURATION / 1000))
                                                        .withSegmentURLs(videoSegmentURLs)
                                                        .build()
                                        )
                                        /*.withSegmentTemplate(
                                                SegmentTemplate.builder()
                                                        .withTimescale(15360L)
                                                        .withInitialization(name + "_init$RepresentationID$.m4s")
                                                        .withMedia(name + "_chunk$RepresentationID$-$Number%05d$.m4s")
                                                        .withStartNumber((long) curVideoCount)
                                                        .withSegmentTimeline(videoSegments)
                                                        .build()
                                        )*/
                                        .build()
                        );
                    }

                    List<Descriptor> audioChannelConfigurations = new ArrayList<>();
                    audioChannelConfigurations.add(
                            new Descriptor("urn:mpeg:dash:23003:3:audio_channel_configuration:2011", "1") {
                                @Override
                                public String getValue() { return null; }
                            }
                    );
                    List<Representation> audioRepresentations = new ArrayList<>();
                    for (int i = 0; i < 1; i++) { // TODO: 화질 개수 설정
                        audioRepresentations.add(
                                Representation.builder()
                                        .withId(String.valueOf(i))
                                        .withBandwidth(128000)
                                        .withCodecs("mp4a.40.2")
                                        .withAudioSamplingRate("44100")
                                        .withSar(new Ratio(1L, 1L))
                                        .withMimeType("audio/mp4")
                                        .withAudioChannelConfigurations(audioChannelConfigurations)
                                        /*.withSegmentBase(
                                                SegmentBase.builder()
                                                        .withInitialization(
                                                                URLType.builder()
                                                                        .withSourceURL()
                                                                        .build()
                                                        )
                                                        .build()
                                        )*/
                                        .withSegmentList(
                                                SegmentList.builder()
                                                        .withDuration((long) (SEGMENT_DURATION / 1000))
                                                        .withSegmentURLs(audioSegmentURLs)
                                                        .build()
                                        )
                                        /*.withSegmentTemplate(
                                                SegmentTemplate.builder()
                                                        .withTimescale(44100L)
                                                        .withInitialization(name + "_init$RepresentationID$.m4s")
                                                        .withMedia(name + "_chunk$RepresentationID$-$Number%05d$.m4s")
                                                        .withStartNumber((long) curAudioCount)
                                                        .withSegmentTimeline(audioSegments)
                                                        .build()
                                        )*/
                                        .build()
                        );
                    }
                    //////////////

                    //////////////
                    // ADAPTATION-SETS
                    List<AdaptationSet> adaptationSets = new ArrayList<>();
                    adaptationSets.add(
                            AdaptationSet.builder()
                                    .withId(0)
                                    .withBitstreamSwitching(true)
                                    .withContentType("video")
                                    .withMaxHeight(1280L)
                                    .withMaxWidth(720L)
                                    .withPar(new Ratio(16L, 9L))
                                    .withStartWithSAP(1L)
                                    .withFrameRate(new FrameRate(30L, 1L))
                                    .withSegmentAlignment("true")
                                    .withRepresentations(videoRepresentations)
                                    .build()
                    );
                    adaptationSets.add(
                            AdaptationSet.builder()
                                    .withId(1)
                                    .withBitstreamSwitching(true)
                                    .withContentType("audio")
                                    .withStartWithSAP(1L)
                                    .withSegmentAlignment("true")
                                    .withRepresentations(audioRepresentations)
                                    .build()
                    );
                    //////////////

                    //////////////
                    // PERIODS
                    List<Period> periods = new ArrayList<>();
                    Period period = Period.builder()
                            .withId(String.valueOf(0))
                            .withStart(Duration.ofSeconds(0, 0))
                            .withAdaptationSets(adaptationSets)
                            .build();
                    periods.add(period);
                    //////////////

                    //////////////
                    // MPD
                    ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                    List<ProgramInformation> programInformations = new ArrayList<>();
                    programInformations.add(
                            ProgramInformation.builder()
                                    .withCopyright(configManager.getServiceName())
                                    .withLang("KR")
                                    //.withSource()
                                    //.withMoreInformationURL()
                                    .build()
                    );

                    List<ServiceDescription> serviceDescriptions = new ArrayList<>();
                    serviceDescriptions.add(
                            ServiceDescription.builder()
                                    .withId(0)
                                    .build()
                    );

                    List<Profile> profiles = new ArrayList<>();
                    profiles.add(Profile.MPEG_DASH_LIVE);

                    MPD mpd = MPD.builder()
                            .withProgramInformations(programInformations)
                            .withServiceDescriptions(serviceDescriptions)
                            .withProfiles(
                                    Profiles.builder()
                                            .withProfiles(profiles)
                                            .build()
                            )
                            .withPeriods(periods)
                            .withType(PresentationType.DYNAMIC)
                            //.withAvailabilityStartTime() // TODO
                            //.withAvailabilityEndTime() // TODO
                            //.withPublishTime()
                            .withMediaPresentationDuration(Duration.ofSeconds(36, 6)) // TODO
                            //.withMinimumUpdatePeriod() // TODO
                            .withMinBufferTime(Duration.ofSeconds(10, 0)) // TODO
                            .withMaxSegmentDuration(Duration.ofSeconds(5, 0)) // TODO
                            .withSchemaLocation("urn:mpeg:DASH:schema:MPD:2011 http://standards.iso.org/ittf/PubliclyAvailableStandards/MPEG-DASH_schema_files/DASH-MPD.xsd")
                            .build();

                    DashManager dashManager = ServiceManager.getInstance().getDashManager();
                    String mpdString = dashManager.getMpdParser().writeAsString(mpd);

                    BufferedWriter writer = new BufferedWriter(new FileWriter(manifestFileName));
                    writer.write(mpdString);
                    writer.close();
                    //////////////

                    curVideoCount = 0;
                    curAudioCount = 0;
                }
                //////////////
            } catch (Exception e) {
                logger.warn("[RtmpClient.StreamEventDispatcher] dispatchEvent.Exception", e);
            }
        }
        ////////////////////////////

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private final class BandwidthStatusTask extends TimerTask {

        private final String name;
        private final Timer timer;

        public BandwidthStatusTask(String name, Timer timer) {
            this.name = name;
            this.timer = timer;
        }

        @Override
        public void run() {
            logger.debug("[RtmpClient.BandwidthStatusTask] Bandwidth check done: {}", isBandwidthCheckDone());
            this.cancel();
            timer.schedule(new PlayStatusTask(name), 1000L);
            subscribe(new SubscribeStreamCallBack(), new Object[] {name});
        }

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private final class PlayStatusTask extends TimerTask {

        private final String name;

        private PlayStatusTask(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            logger.debug("[RtmpClient.PlayStatusTask] Subscribed: {}", isSubscribed());
            this.cancel();
            createStream(
                    new CreateStreamCallback(
                            getConnection(), name
                    )
            );
        }

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private static final class SubscribeStreamCallBack implements IPendingServiceCallback {

        @Override
        public void resultReceived(IPendingServiceCall call) {
            logger.debug("[RtmpClient.SubscribeStreamCallBack] SubscribeStreamCallBack.resultReceived: {}", call);
        }

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private final class CreateStreamCallback implements IPendingServiceCallback {

        private final RTMPConnection rtmpConnection;
        private final String name;

        private CreateStreamCallback(RTMPConnection rtmpConnection, String name) {
            this.rtmpConnection = rtmpConnection;
            this.name = name;
        }

        public void resultReceived(IPendingServiceCall call) {
            Number streamId = (Number) call.getResult();
            // live buffer 0.5s / vod buffer 4s
            //if (Boolean.valueOf(PropertiesReader.getProperty("live"))) {
                rtmpConnection.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 500));
                play(streamId, name, -1, -1);
            /*} else {
                rtmpConnection.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 4000));
                play(streamId, PropertiesReader.getProperty("name"), 0, -1);
            }*/
        }
    };
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private final class StreamEventHandler implements INetStreamEventHandler {

        @Override
        public void onStreamEvent(Notify notify) {
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
        }

    }
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    @Override
    public void connectionOpened(RTMPConnection conn) {
        logger.debug("[RtmpClient] connection opened");
        super.connectionOpened(conn);
        this.rtmpConnection = conn;
    }

    @Override
    public void connectionClosed(RTMPConnection conn) {
        logger.debug("[RtmpClient] connection closed");
        super.connectionClosed(conn);
        System.exit(0);
    }
    //////////////////////////////////////////////////////////

}
