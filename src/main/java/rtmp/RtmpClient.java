package rtmp;

import config.ConfigManager;
import dash.DashManager;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.codec.AudioCodec;
import org.red5.codec.VideoCodec;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.flv.impl.Tag;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.event.*;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.consumer.ImmutableTag;
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
import java.util.*;

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
    private final String saveAsVideoInitFileName;
    private final String saveAsAudioFileName;
    private final String saveAsAudioInitFileName;
    private final String saveAsMp4FileName;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    private RTMPConnection rtmpConnection = null;
    private final Timer timer = new Timer();
    private boolean finished = false;
    private StreamEventHandler streamEventHandler = null;
    private StreamEventDispatcher streamEventDispatcher = null;
    //////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////
    public RtmpClient(String uri) {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        this.host = configManager.getRtmpPublishIp();
        this.port = configManager.getRtmpPublishPort();

        this.saveAsManifestFileName = uri + ".mpd";
        this.saveAsVideoFileName = uri + "_chunk0_%05d.m4s"; // 화질 추가 필요
        this.saveAsVideoInitFileName = uri + "_init0.m4s"; // 화질 추가 필요
        this.saveAsAudioFileName = uri + "_chunk1_%05d.aac";
        this.saveAsAudioInitFileName = uri + "_init1.m4s";
        this.saveAsMp4FileName = uri + ".mp4";

        if (uri.indexOf("/") == 0) {
            uri = uri.substring(1);
        }
        this.app = uri.substring(0, uri.indexOf("/"));
        this.name = uri.substring(uri.indexOf("/") + 1);

        logger.debug("[RtmpClient] Initiating, host: [{}], app: [{}], port: [{}], uri: [{}]", host , app, port, uri);
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
        private static final int SEGMENT_DURATION = 1000;
        private static final int SEGMENT_GAP = 5;
        private static final int FILE_DELETION_WATERMARK = 5 * SEGMENT_GAP; // SEGMENT_GAP 배수 만큼 저장

        private long videoTimeGap = 0;
        private long videoPrevTime = System.currentTimeMillis();
        private long audioTimeGap = 0;
        private long audioPrevTime = System.currentTimeMillis();

        private File mp4File = null;
        private String curMp4Name = null;
        private long timeGap = 0;
        private long prevTime = System.currentTimeMillis();

        private boolean isManifestCreate = true;
        private String manifestFileName;
        private int manifestFileCount = 0;
        private boolean isDelete = false;
        private final List<String> deleteFiles = new ArrayList<>();

        private File videoInitFile = null;
        private File videoFile = null;
        private String curVideoName = null;
        private int curVideoSeqNum = 1;
        private long videoTotalTs = 0;
        private long curVideoTotalTs = 0;
        private long curVideoTsGap = 0;
        private long prevVideoTsGap = 0;
        private int curVideoCount = 0;

        private File audioInitFile = null;
        private File audioFile = null;
        private String curAudioName = null;
        private int curAudioSeqNum = 1;
        private long audioTotalTs = 0;
        private long curAudioTotalTs = 0;
        private long curAudioTsGap = 0;
        private long prevAudioTsGap = 0;
        private int curAudioCount = 0;

        private final Map<String, String> streamMetaDataMap = new HashMap<>();
        private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        ////////////////////////////

        ////////////////////////////
        public void start() {
            logger.debug("[RtmpClient.StreamEventDispatcher] START");

            manifestFileName = FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsManifestFileName);

            videoInitFile = new File(FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsVideoInitFileName));
            curVideoName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsVideoFileName, curVideoSeqNum));
            videoFile = new File(curVideoName);
            if (videoFile.exists()) {
                FileManager.deleteFile(curVideoName);
                logger.debug("[RtmpClient.StreamEventDispatcher] START");
            }

            audioInitFile = new File(FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsAudioInitFileName));
            curAudioName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsAudioFileName, curAudioSeqNum));
            audioFile = new File(curAudioName);
            if (audioFile.exists()) {
                FileManager.deleteFile(curAudioName);
            }

            curMp4Name = FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsMp4FileName);
            mp4File = new File(curMp4Name);
            if (mp4File.exists()) {
                FileManager.deleteFile(curMp4Name);
            }
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
                // PARSE & SAVE DATA
                long curTime = System.currentTimeMillis();
                InputStream inputStream = ((IStreamData) rtmpEvent).getData().asInputStream();
                byte[] data = inputStream.readAllBytes();

                boolean isConfig;
                int codecId;

                if (rtmpEvent instanceof VideoData) {
                    isConfig = ((VideoData) rtmpEvent).isConfig();
                    VideoData.FrameType frameType = ((VideoData) rtmpEvent).getFrameType();
                    /**
                     *     JPEG((byte)1), H263((byte)2), SCREEN_VIDEO((byte)3),
                     *     VP6((byte)4), VP6a((byte)5), SCREEN_VIDEO2((byte)6),
                     *     AVC((byte)7), VP8((byte)8), VP9((byte)9),
                     *     AV1((byte)10), MPEG1((byte)11), HEVC((byte)12);
                     */
                    codecId = ((VideoData) rtmpEvent).getCodecId();
                    logger.warn("[RtmpClient.StreamEventDispatcher] [VIDEO] isConfig: {}, codecId={}, frameType={}", isConfig, codecId, frameType);

                    videoTotalTs += rtmpEvent.getTimestamp();
                    curVideoTotalTs += rtmpEvent.getTimestamp();

                    if (videoPrevTime > 0) { videoTimeGap = curTime - videoPrevTime; }
                    else { videoPrevTime = curTime; }
                    if (videoTimeGap >= (SEGMENT_DURATION * SEGMENT_GAP)) {
                        curVideoName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsVideoFileName, curVideoSeqNum++));
                        videoFile = new File(curVideoName);
                        if (videoFile.exists()) {
                            FileManager.deleteFile(curVideoName);
                        }
                        videoTimeGap = 0;
                        videoPrevTime = 0;
                        curVideoCount++;
                    }

                    /*if (prevVideoTsGap > 0) { curVideoTsGap = videoTotalTs - prevVideoTsGap; }
                    else { prevVideoTsGap = videoTotalTs; }
                    if (curVideoTsGap >= (SEGMENT_DURATION * SEGMENT_GAP)) {
                        curVideoName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsVideoFileName, curVideoSeqNum++));
                        videoFile = new File(curVideoName);
                        if (videoFile.exists()) {
                            FileManager.deleteFile(curVideoName);
                        }
                        prevVideoTsGap = 0;
                        curVideoTsGap = 0;
                        curVideoCount++;
                    }*/

                    FileManager.writeBytes(videoFile, data, true);
                    if (logger.isTraceEnabled()) {
                        logger.trace("[RtmpClient.StreamEventDispatcher] [prevVideoTsGap={}, curVideoTsGap={}] VIDEO(curVideoCount={}, curVideoName={}, videoTotalTs={})",
                                prevVideoTsGap, curVideoTsGap, curVideoCount, curVideoName, videoTotalTs
                        );
                    }
                } else if (rtmpEvent instanceof AudioData) {
                    isConfig = ((AudioData) rtmpEvent).isConfig();
                    /**
                     *     PCM((byte)0), ADPCM((byte)1), MP3((byte)2), PCM_LE((byte)3),
                     *     NELLY_MOSER_16K((byte)4), NELLY_MOSER_8K((byte)5), NELLY_MOSER((byte)6),
                     *     PCM_ALAW((byte)7), PCM_MULAW((byte)8), RESERVED((byte)9),
                     *     AAC((byte)10), SPEEX((byte)11), MP2((byte)12),
                     *     OPUS((byte)13), MP3_8K((byte)14), DEVICE_SPECIFIC((byte)15);
                     */
                    codecId = ((AudioData) rtmpEvent).getCodecId();
                    logger.warn("[RtmpClient.StreamEventDispatcher] [AUDIO] isConfig: {}, codecId={}", isConfig, codecId);

                    audioTotalTs += rtmpEvent.getTimestamp();
                    curAudioTotalTs += rtmpEvent.getTimestamp();

                    if (audioPrevTime > 0) { audioTimeGap = curTime - audioPrevTime; }
                    else { audioPrevTime = curTime; }
                    if (audioTimeGap >= (SEGMENT_DURATION * SEGMENT_GAP)) {
                        curAudioName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsAudioFileName, curAudioSeqNum++));
                        audioFile = new File(curAudioName);
                        if (audioFile.exists()) {
                            FileManager.deleteFile(curAudioName);
                        }
                        audioTimeGap = 0;
                        audioPrevTime = 0;
                        curAudioCount++;
                    }

                    /*if (prevAudioTsGap > 0) { curAudioTsGap = audioTotalTs - prevAudioTsGap; }
                    else { prevAudioTsGap = audioTotalTs; }
                    if (curAudioTsGap >= (SEGMENT_DURATION * SEGMENT_GAP)) {
                        curAudioName = FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsAudioFileName, curAudioSeqNum++));
                        audioFile = new File(curAudioName);
                        if (audioFile.exists()) {
                            FileManager.deleteFile(curAudioName);
                        }
                        prevAudioTsGap = 0;
                        curAudioTsGap = 0;
                        curAudioCount++;
                    }*/

                    FileManager.writeBytes(audioFile, data, true);
                    if (logger.isTraceEnabled()) {
                        logger.trace("[RtmpClient.StreamEventDispatcher] [prevAudioTsGap={}, curAudioTsGap={}] AUDIO(curAudioCount={}, curAudioName={}, audioTotalTs={})",
                                prevAudioTsGap, curAudioTsGap, curAudioCount, curAudioName, audioTotalTs
                        );
                    }
                } else if (rtmpEvent instanceof Notify) {
                    // MUST GET DECODED STREAM META DATA
                    // [2022-02-25 08:23:33.107][DEBUG] [NioProcessor-2] Map params:
                    //      {duration=0.0, width=1280.0, height=720.0,
                    //          videodatarate=1953.125, framerate=30.0, videocodecid=7.0,
                    //          audiodatarate=187.5, audiosamplerate=44100.0, audiosamplesize=16.0,
                    //          stereo=false, audiocodecid=10.0, encoder=Lavf59.16.100, filesize=0.0}
                    // - (RTMPProtocolDecoder.java:939)

                    IoBuffer streamMetaDataIoBuffer = IoBuffer.wrap(data);
                    Object decodeResult = decodeStreamData(streamMetaDataIoBuffer);

                    if (decodeResult instanceof Map) {
                        Map<Object, Object> decodeResultMap = (Map<Object, Object>) decodeResult;
                        for (Map.Entry<Object, Object> entry : decodeResultMap.entrySet()) {
                            if (entry == null) { continue; }

                            String entryKey = (String) entry.getKey();
                            if (entryKey == null) { continue; }

                            String entryValue = String.valueOf(entry.getValue());
                            streamMetaDataMap.putIfAbsent(entryKey, String.valueOf(entryValue));
                            logger.warn("[RtmpClient.StreamEventDispatcher] [{}] : [{}]", entryKey, entryValue);
                        }
                    }

                    if (videoInitFile.exists()) {
                        FileManager.deleteFile(FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsVideoInitFileName));
                    }
                    FileManager.writeBytes(videoInitFile, data, true);
                    //FileManager.writeBytes(videoFile, data, true);

                    if (audioInitFile.exists()) {
                        FileManager.deleteFile(FileManager.concatFilePath(configManager.getMediaBasePath(), saveAsAudioInitFileName));
                    }
                    FileManager.writeBytes(audioInitFile, data, true);
                    //FileManager.writeBytes(audioFile, data, true);

                    return;
                }

                /*if (prevTime > 0) { timeGap = curTime - prevTime; }
                else { prevTime = curTime; }
                if (timeGap >= (SEGMENT_DURATION * SEGMENT_GAP)) {
                    timeGap = 0;
                    prevTime = 0;
                }

                FileManager.writeBytes(mp4File, data, true);
                logger.debug("[RtmpClient.StreamEventDispatcher] MEDIA(curName={}, audioTotalTs={}, videoTotalTs={})",
                        curMp4Name, audioTotalTs, videoTotalTs
                );*/
                //////////////

                if (isManifestCreate) {
                    //////////////
                    // GENERATE MPD
                    if (curVideoCount >= SEGMENT_GAP && curAudioCount >= SEGMENT_GAP) {
                        //////////////
                        // SEGMENT LIST (URL)
                        //List<SegmentURL> videoSegmentURLs = new ArrayList<>();
                        for (int i = (curVideoSeqNum - 1) - SEGMENT_GAP; i < (curVideoSeqNum - 1) - SEGMENT_GAP + curVideoCount; i++) {
                            /*videoSegmentURLs.add(
                                    SegmentURL.builder()
                                            .withMedia(String.format(saveAsVideoFileName, i))
                                            .build()
                            );*/

                            deleteFiles.add(FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsVideoFileName, i)));
                        }

                        //List<SegmentURL> audioSegmentURLs = new ArrayList<>();
                        for (int i = (curAudioSeqNum - 1) - SEGMENT_GAP; i < (curAudioSeqNum - 1) - SEGMENT_GAP + curAudioCount; i++) {
                            /*audioSegmentURLs.add(
                                    SegmentURL.builder()
                                            .withMedia(String.format(saveAsAudioFileName, i))
                                            .build()
                            );*/

                            deleteFiles.add(FileManager.concatFilePath(configManager.getMediaBasePath(), String.format(saveAsAudioFileName, i)));
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
                        for (int i = 0; i < 1; i++) { // TODO:  화질 설정에 따른 값 설정 필요
                            videoRepresentations.add(
                                    Representation.builder()
                                            .withId(String.valueOf(i))
                                            .withBandwidth((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.VIDEO_DATA_RATE)) * 1000))
                                            .withCodecs(VideoCodec.valueOfById(Integer.parseInt(streamMetaDataMap.get(StreamMetaDataKey.VIDEO_CODEC_ID))).name())
                                            .withWidth((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.WIDTH))))
                                            .withHeight((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.HEIGHT))))
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
                                            /*.withSegmentList(
                                                    SegmentList.builder()
                                                            .withDuration((long) (SEGMENT_DURATION / 1000))
                                                            .withSegmentURLs(videoSegmentURLs)
                                                            .build()
                                            )*/
                                            .withSegmentTemplate(
                                                    SegmentTemplate.builder()
                                                            .withTimescale(90000L)
                                                            .withDuration(curVideoTotalTs)
                                                            .withInitialization(name + "_init$RepresentationID$.m4s")
                                                            .withMedia(name + "_chunk$RepresentationID$-$Number%05d$.m4s")
                                                            .withStartNumber((long) (curVideoSeqNum - 1) - SEGMENT_GAP)
                                                            //.withSegmentTimeline(videoSegments)
                                                            .build()
                                            )
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
                        for (int i = 0; i < 1; i++) { // TODO: 화질 설정에 따른 값 설정 필요
                            audioRepresentations.add(
                                    Representation.builder()
                                            .withId(String.valueOf(i))
                                            .withBandwidth((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.AUDIO_DATA_RATE)) * 1000))
                                            .withCodecs(AudioCodec.valueOfById(Integer.parseInt(streamMetaDataMap.get(StreamMetaDataKey.AUDIO_CODEC_ID))).name())
                                            .withAudioSamplingRate(streamMetaDataMap.get(StreamMetaDataKey.AUDIO_SAMPLE_RATE))
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
                                            /*.withSegmentList(
                                                    SegmentList.builder()
                                                            .withDuration((long) (SEGMENT_DURATION / 1000))
                                                            .withSegmentURLs(audioSegmentURLs)
                                                            .build()
                                            )*/
                                            .withSegmentTemplate(
                                                    SegmentTemplate.builder()
                                                            .withTimescale(44100L)
                                                            .withDuration(curAudioTotalTs)
                                                            //.withInitialization(name + "_init$RepresentationID$.aac")
                                                            .withMedia(name + "_chunk$RepresentationID$-$Number%05d$.aac")
                                                            .withStartNumber((long) (curAudioSeqNum - 1) - SEGMENT_GAP)
                                                            //.withSegmentTimeline(audioSegments)
                                                            .build()
                                            )
                                            .build()
                            );
                        }
                        //////////////

                        //////////////
                        // ADAPTATION-SETS
                        List<AdaptationSet> adaptationSets = new ArrayList<>();
                        adaptationSets.add(
                                AdaptationSet.builder()
                                        .withId(0) // 화질 설정에 따른 값 설정 필요
                                        .withBitstreamSwitching(true)
                                        .withContentType("video")
                                        .withMaxWidth((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.WIDTH)))) // 화질 설정에 따른 값 설정 필요
                                        .withMaxHeight((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.HEIGHT)))) // 화질 설정에 따른 값 설정 필요
                                        .withPar(new Ratio(16L, 9L))
                                        .withStartWithSAP(1L) // 세그먼트 순차 접근(1) or 세그먼트 랜덤 접근(2)
                                        .withFrameRate(new FrameRate((long) (Double.parseDouble(streamMetaDataMap.get(StreamMetaDataKey.FRAME_RATE))), 1L))
                                        .withSegmentAlignment("true")
                                        .withRepresentations(videoRepresentations)
                                        .build()
                        );
                        adaptationSets.add(
                                AdaptationSet.builder()
                                        .withId(1) // 화질 설정에 따른 값 설정 필요
                                        .withBitstreamSwitching(true)
                                        .withContentType("audio")
                                        .withStartWithSAP(1L) // 세그먼트 순차 접근 or 세그먼트 랜덤 접근(2)
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

                        List<BaseURL> baseUrls = new ArrayList<>();
                        baseUrls.add(
                                BaseURL.builder()
                                        .withValue(configManager.getMediaBasePath())
                                        .build()
                        );

                        List<Profile> profiles = new ArrayList<>();
                        profiles.add(Profile.MPEG_DASH_LIVE);

                        MPD mpd = MPD.builder()
                                .withProgramInformations(programInformations)
                                .withServiceDescriptions(serviceDescriptions)
                                .withBaseURLs(baseUrls)
                                .withProfiles(
                                        Profiles.builder()
                                                .withProfiles(profiles)
                                                .build()
                                )
                                .withPeriods(periods)
                                .withType(PresentationType.DYNAMIC)
                                //.withAvailabilityStartTime()
                                //.withAvailabilityEndTime()
                                //.withPublishTime()
                                .withMediaPresentationDuration(Duration.ofSeconds((long) SEGMENT_DURATION * SEGMENT_GAP / 1000, 0))
                                //.withMinimumUpdatePeriod()
                                .withMinBufferTime(Duration.ofSeconds((long) SEGMENT_DURATION * SEGMENT_GAP / 1000, 0))
                                .withMaxSegmentDuration(Duration.ofSeconds((long) SEGMENT_DURATION * SEGMENT_GAP / 1000, 0))
                                .withSchemaLocation("urn:mpeg:DASH:schema:MPD:2011 http://standards.iso.org/ittf/PubliclyAvailableStandards/MPEG-DASH_schema_files/DASH-MPD.xsd")
                                .build();

                        DashManager dashManager = ServiceManager.getInstance().getDashManager();
                        String mpdString = dashManager.getMpdParser().writeAsString(mpd);

                        BufferedWriter writer = new BufferedWriter(new FileWriter(manifestFileName));
                        writer.write(mpdString);
                        writer.close();
                        //////////////

                        curVideoTotalTs = 0;
                        curAudioTotalTs = 0;

                        curVideoCount = 0;
                        curAudioCount = 0;
                        manifestFileCount++;

                        if (isDelete) {
                            if (manifestFileCount >= FILE_DELETION_WATERMARK) {
                                for (String fileName : deleteFiles) {
                                    FileManager.deleteFile(fileName);
                                }
                                logger.debug("[RtmpClient.StreamEventDispatcher] CLEAR OLD FILES (count={})", deleteFiles.size());
                            }
                        }
                    }
                    //////////////
                }
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
            logger.debug("[RtmpClient.StreamEventHandler] onStreamEvent: {}", notify);
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
            String code = (String) map.get("code");
            logger.debug("[RtmpClient.StreamEventHandler] <:{}", code);
            if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
                logger.warn("[RtmpClient.StreamEventHandler] Requested stream was not found");
                streamEventDispatcher.stop();
                disconnect();
            } else if (StatusCodes.NS_PLAY_UNPUBLISHNOTIFY.equals(code) || StatusCodes.NS_PLAY_COMPLETE.equals(code)) {
                logger.warn("[RtmpClient.StreamEventHandler] Source has stopped publishing or play is complete");
                streamEventDispatcher.stop();
                disconnect();
            } else {
                logger.debug("[RtmpClient.StreamEventHandler] RECV {}", notify);
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

    // GET METHOD FROM RTMPProtocolDecoder.decodeStreamData
    public Object decodeStreamData(IoBuffer in) {
        logger.debug("decodeStreamData");

        Object ret;
        IConnection.Encoding encoding = Red5.getConnectionLocal().getEncoding();
        logger.debug("Encoding: {}", encoding);
        in.mark();
        org.red5.io.object.Input input = new Input(in);
        if (encoding == IConnection.Encoding.AMF3) {
            logger.debug("Client indicates its using AMF3");
        }

        byte dataType = input.readDataType();
        logger.debug("Data type: {}", dataType);
        if (dataType == 4) {
            String action = input.readString();
            byte object;
            if ("@setDataFrame".equals(action)) {
                object = input.readDataType();
                logger.debug("Dataframe method type: {}", object);
                String onCueOrOnMeta = input.readString();
                object = input.readDataType();
                if (object == -17) {
                    logger.debug("Switching decoding to AMF3");
                    input = new org.red5.io.amf3.Input(in);
                    ((org.red5.io.amf3.Input)input).enforceAMF3();
                    object = input.readDataType();
                }

                logger.debug("Dataframe params type: {}", object);
                Object params;
                if (object == 7) {
                    params = input.readMap();
                } else if (object == 6) {
                    params = input.readArray(Object[].class);
                } else if (object == 4) {
                    String str = input.readString();
                    logger.debug("String params: {}", str);
                    params = new HashMap();
                    ((Map)params).put("0", str);
                } else {
                    try {
                        params = input.readObject();
                    } catch (Exception var13) {
                        logger.debug("Dataframe decode error", var13);
                        params = Collections.EMPTY_MAP;
                    }
                }

                logger.debug("Dataframe: {} params: {}", onCueOrOnMeta, params.toString());

                IoBuffer buf = IoBuffer.allocate(64);
                buf.setAutoExpand(true);
                Output out = new Output(buf);
                out.writeString(onCueOrOnMeta);
                out.writeMap((Map)params);
                buf.flip();
                ret = new Notify(buf, onCueOrOnMeta);
            } else {
                object = input.readDataType();
                if (object == -17) {
                    logger.debug("Switching decoding to AMF3");
                    input = new org.red5.io.amf3.Input(in);
                    ((org.red5.io.amf3.Input)input).enforceAMF3();
                    object = input.readDataType();
                }

                logger.debug("Stream send: {}", action);
                Map<Object, Object> params = null;
                logger.debug("Params type: {}", object);
                if (object == 7) {
                    params = (Map) input.readMap();
                    logger.debug("Map params: {}", params.toString());
                } else if (object == 6) {
                    params = (Map) input.readArray(Object[].class);
                    logger.debug("Array params: {}", params);
                } else if (object == 4) {
                    String str = input.readString();
                    logger.debug("String params: {}", str);
                    params = new HashMap();
                    params.put("0", str);
                } else if (object == 9) {
                    params = (Map) input.readObject();
                    logger.debug("Object params: {}", params);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Stream send did not provide a parameter map");
                }

                in.reset();
                ret = params;
            }
        } else {
            in.reset();
            ret = new Notify(in.asReadOnlyBuffer());
        }

        return ret;
    }

}
