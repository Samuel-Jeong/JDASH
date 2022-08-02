package dash.mpd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.mpd.parser.MPDParser;
import dash.mpd.parser.mpd.*;
import dash.mpd.validator.MPDValidator;
import dash.mpd.validator.ManifestValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @ Adaptation-Sets attributes
 *
 *      Segment alignment
 *          - Permits non-overlapping decoding and presentation of segments from different representations
 *          (중복되지 않는 디코딩 및 다양한 representations 세그먼트 표시 가능)
 *
 *      Stream Access Points (SAPs)
 *          - Presentation time and position in segments at which random access and switching can occur
 *          (랜덤 액세스 및 전환이 발생할 수 있는 세그먼트에서의 프레젠테이션 시간과 위치)
 *
 *      BitstreamSwitching
 *          - Concatenation of segments from different representations results in conforming bitstream
 *          (하나의 비트스트림에 서로 다른 representations 의 세그먼트를 연결할 수 있게 하는 옵션)
 *
 */
public class MpdManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MpdManager.class);

    //transient public static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
    public static final String CONTENT_AUDIO_TYPE = "audio";
    public static final String CONTENT_VIDEO_TYPE = "video";
    public static final long MICRO_SEC = 1000000;

    private final String dashUnitId;

    private final MPDParser mpdParser;
    private MPDValidator mpdValidator = null;
    private MPD mpd = null;
    private final AtomicBoolean isMpdDone = new AtomicBoolean(false);
    private OffsetDateTime remoteMpdAvailabilityStartTime = null;
    private final AtomicLong lastMpdParsedTime = new AtomicLong(0); // milli-sec

    private final Map<String, AtomicLong> videoSegmentSeqNumMap = new HashMap<>();
    private final Map<String, AtomicLong> audioSegmentSeqNumMap = new HashMap<>();

    private final AtomicInteger curVideoIndex = new AtomicInteger(0); // 비디오 Representation List 중 현재 비디오 ID
    private final AtomicInteger curAudioIndex = new AtomicInteger(0); // 오디오 Representation List 중 현재 오디오 ID

    private final int REPRESENTATION_LIMIT_COUNT = 1;

    private final int curPeriodId = 0;

    private final transient ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    private final transient FileManager fileManager = new FileManager();

    private final String localMpdPath;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MpdManager(String dashUnitId, String localMpdPath) {
        this.dashUnitId = dashUnitId;
        this.localMpdPath = localMpdPath;
        this.mpdParser = new MPDParser();
        if (configManager.isEnableValidation()) {
            try {
                mpdValidator = new MPDValidator(mpdParser, configManager.getValidationXsdPath());
            } catch (Exception e) {
                logger.warn("[MpdManager({})] Fail to make a mpd validator.", dashUnitId, e);
            }
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean parseMpd(String targetMpdPath, boolean isRemote) {
        if (targetMpdPath == null) { return false; }

        try {
            /////////////////////////////////////////
            // 1) CHECK FILE STREAM
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(targetMpdPath);
            } catch (Exception e) {
                logger.warn("[MpdManager({})] Fail to get the input stream. (filePath={})", dashUnitId, targetMpdPath);
                return false;
            }
            /////////////////////////////////////////

            /////////////////////////////////////////
            // 2) PARSE MPD
            mpd = mpdParser.parse(inputStream);
            if (mpd == null) {
                logger.warn("[MpdManager({})] Fail to parse the mpd. (path={})", dashUnitId, targetMpdPath);
                return false;
            }
            /////////////////////////////////////////

            /////////////////////////////////////////
            // 3) GET FILED INFORMATION (비디오, 오디오 정의 순서)
            List<AdaptationSet> adaptationSets = mpd.getPeriods().get(curPeriodId).getAdaptationSets();
            if (adaptationSets == null || adaptationSets.isEmpty()) {
                logger.warn("[MpdManager({})] Fail to get the adaptationSets. (path={})", dashUnitId, targetMpdPath);
                return false;
            }

            int audioRepresentationCount = 0;
            int videoRepresentationCount = 0;
            for (AdaptationSet adaptationSet : adaptationSets) {
                if (adaptationSet == null) { continue; }

                limitRepresentations(adaptationSet);
                if (adaptationSet.getMimeType() != null) {
                    if (adaptationSet.getMimeType().contains(CONTENT_AUDIO_TYPE)) {
                        initAudioSegmentSeqNumMap(adaptationSet);
                        audioRepresentationCount += adaptationSet.getRepresentations().size();
                    } else if (adaptationSet.getMimeType().contains(CONTENT_VIDEO_TYPE)) {
                        initVideoSegmentSeqNumMap(adaptationSet);
                        videoRepresentationCount += adaptationSet.getRepresentations().size();
                    }
                } else if (adaptationSet.getContentType() != null) {
                    if (adaptationSet.getContentType().contains(CONTENT_AUDIO_TYPE)) {
                        initAudioSegmentSeqNumMap(adaptationSet);
                        audioRepresentationCount += adaptationSet.getRepresentations().size();
                    } else if (adaptationSet.getContentType().contains(CONTENT_VIDEO_TYPE)) {
                        initVideoSegmentSeqNumMap(adaptationSet);
                        videoRepresentationCount += adaptationSet.getRepresentations().size();
                    }
                }

                logger.debug("adaptationSet.getRepresentations(): {}", adaptationSet.getRepresentations());
            }

            logger.debug("audioSegmentSeqNumMap: {}", gson.toJson(audioSegmentSeqNumMap));
            logger.debug("videoSegmentSeqNumMap: {}", gson.toJson(videoSegmentSeqNumMap));

            if (logger.isTraceEnabled()) {
                logger.trace("[MpdManager({})] audioRepresentationCount: {}, videoRepresentationCount: {}",
                        dashUnitId, audioRepresentationCount, videoRepresentationCount
                );
            }
            /////////////////////////////////////////

            /////////////////////////////////////////
            // 4) DYNAMIC STREAM 인 경우 MPD 수정
            if (isRemote) {
                if (mpd.getType().equals(PresentationType.DYNAMIC)) {
                    setCustomRemoteMpdOptions();
                    writeMpd();
                    ///////////////////////////////////
                }
            } else {
                setCustomLocalMpdOptions();
            }
            /////////////////////////////////////////

            setLastMpdParsedTime(OffsetDateTime.now().toInstant().toEpochMilli());
            logger.debug("[MpdManager({})] MPD PARSE DONE (path={})", dashUnitId, targetMpdPath);
            logger.trace("[MpdManager({})] MPD=\n{}", dashUnitId, gson.toJson(mpd));
        } catch (Exception e) {
            logger.warn("[MpdManager({})] (targetMpdPath={}) parseMpd.Exception", dashUnitId, targetMpdPath, e);
            return false;
        }

        return true;
    }

    private void limitRepresentations(AdaptationSet adaptationSet) {
        List<Representation> newRepresentations = new ArrayList<>();
        List<Representation> representations = adaptationSet.getRepresentations();
        for (int i = 0; i < representations.size(); i++) {
            if (REPRESENTATION_LIMIT_COUNT == i) {
                break;
            }
            newRepresentations.add(representations.get(i));
        }
        adaptationSet.setRepresentations(newRepresentations);
    }

    private void initAudioSegmentSeqNumMap(AdaptationSet adaptationSet) {
        List<Representation> audioRepresentations = adaptationSet.getRepresentations();
        audioRepresentations.forEach(
                audioRepresentation -> {
                    audioRepresentation.setSegmentTemplate(adaptationSet.getSegmentTemplate());
                    audioSegmentSeqNumMap.putIfAbsent(
                            audioRepresentation.getId(),
                            new AtomicLong(getSegmentStartNumber(CONTENT_AUDIO_TYPE))
                    );
                }
        );
    }

    private void initVideoSegmentSeqNumMap(AdaptationSet adaptationSet) {
        List<Representation> videoRepresentations = adaptationSet.getRepresentations();
        videoRepresentations.forEach(
                videoRepresentation ->  {
                    videoRepresentation.setSegmentTemplate(adaptationSet.getSegmentTemplate());
                    videoSegmentSeqNumMap.putIfAbsent(
                            videoRepresentation.getId(),
                            new AtomicLong(getSegmentStartNumber(CONTENT_VIDEO_TYPE))
                    );
                }
        );
    }

    public void calculateSegmentNumber(String contentType, String representationId) {
        if (representationId == null) { return; }

        // [Current Time] - [MPD.ast] = [미디어 스트림 생성 후 경과 시간] = T
        // T / [segment.timescale * segment.duration] = segment number
        long segmentDuration;
        long segmentTimeScale;
        if (contentType.equals(CONTENT_VIDEO_TYPE)) {
            segmentDuration = getVideoSegmentDuration(representationId); // micro-sec
            segmentTimeScale = getVideoSegmentTimeScale(representationId); // micro-sec
        } else {
            segmentDuration = getAudioSegmentDuration(representationId); // micro-sec
            segmentTimeScale = getAudioSegmentTimeScale(representationId); // micro-sec
        }

        long segmentTime = segmentDuration / segmentTimeScale; // sec
        if (segmentTime <= 0) { segmentTime = 1; }
        logger.debug("[MpdManager({})] segmentTimeScale: [{}]ms, segmentDuration: [{}]ms, segmentTime: [{}]s", dashUnitId, segmentTimeScale, segmentDuration, segmentTime);

        //OffsetDateTime mpdAvailabilityStartTime = mpd.getAvailabilityStartTime(); // OffsetDateTime
        OffsetDateTime mpdAvailabilityStartTime = remoteMpdAvailabilityStartTime; // OffsetDateTime
        if (mpdAvailabilityStartTime == null) {
            mpdAvailabilityStartTime = OffsetDateTime.now();
            /*logger.debug("[MpdManager({})] [{}] Fail to get the mpd availability start time. Fail to calculate the segment number.", dashUnitId, contentType);
            return;*/
        }

        long mediaStartTime = mpdAvailabilityStartTime.toInstant().toEpochMilli(); // milli-sec
        long currentTime = getLastMpdParsedTime(); // milli-sec
        long elapsedTime = (currentTime - mediaStartTime) / 1000; // sec
        if (elapsedTime <= 0) { elapsedTime = 1; }
        elapsedTime += (long) configManager.getRemoteTimeOffset();
        logger.debug("[MpdManager({})] mediaStartTime: [{}]ms, currentTime: [{}]ms, elapsedTime: [{}]s, timeOffset=[{}]",
                dashUnitId, mediaStartTime, currentTime, elapsedTime, configManager.getRemoteTimeOffset()
        );

        int segmentNumber = (int) (elapsedTime / segmentTime);
        if (segmentNumber > 0) {
            logger.debug("[MpdManager({})] [{}] Segment Start Number: [{}]", dashUnitId, contentType, segmentNumber);
            if (contentType.equals(CONTENT_VIDEO_TYPE)) {
                setVideoSegmentSeqNum(representationId, segmentNumber);
            } else {
                setAudioSegmentSeqNum(representationId, segmentNumber);
            }
        }
    }

    public boolean validate() {
        if (mpd == null || mpdValidator == null) { return false; }

        try {
            mpdValidator.validate(mpd);
        } catch (ManifestValidationException e) {
            logger.warn("[MpdManager({})] validate.ManifestValidationException", dashUnitId, e);
            logger.warn("{}", e.getViolations());
            return false;
        } catch (Exception e) {
            logger.warn("[MpdManager({})] validate.Exception", dashUnitId, e);
            return false;
        }

        return true;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void makeMpd(FileManager fileManager, String targetMpdPath, byte[] content) {
        boolean isMpdDone = getIsMpdDone();
        fileManager.writeBytes(
                targetMpdPath,
                content,
                !isMpdDone
        );
        if (isMpdDone) {
            setIsMpdDone(false);
        }
    }

    private void writeMpd() {
        try {
            String mpdString = writeAsString();
            if (mpdString == null) { return; }

            fileManager.writeBytes(
                    localMpdPath,
                    mpdString.getBytes(),
                    false
            );
        } catch (Exception e) {
            logger.warn("[MpdManager({})] Fail to write the mpd. (path={})", dashUnitId, localMpdPath);
        }
    }

    public String writeAsString() throws JsonProcessingException {
        logger.debug("### mpd: {}", gson.toJson(mpd));
        return mpdParser.writeAsString(mpd);
    }

    public void makeInitSegment(FileManager fileManager, String targetInitSegPath, byte[] content) {
        if (targetInitSegPath == null) {
            return;
        }

        logger.debug("[MpdManager({})] [makeInitSegment] > [targetInitSegPath: {}]", dashUnitId, targetInitSegPath);
        fileManager.mkdirs(fileManager.getParentPathFromUri(targetInitSegPath));

        fileManager.writeBytes(
                targetInitSegPath,
                content,
                true
        );
    }

    public void makeMediaSegment(FileManager fileManager, String targetMediaSegPath, byte[] content) {
        if (targetMediaSegPath == null) {
            return;
        }

        fileManager.writeBytes(
                targetMediaSegPath,
                content,
                true
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getLastMpdParsedTime() {
        return lastMpdParsedTime.get();
    }

    public void setLastMpdParsedTime(long lastMpdParsedTime) {
        this.lastMpdParsedTime.set(lastMpdParsedTime);
    }

    public List<Long> getBitRates(List<Representation> representations) {
        List<Long> bitRates = new ArrayList<>();

        try {
            for (Representation representation : representations) {
                bitRates.add(representation.getBandwidth());
            }
        } catch (Exception e) {
            return null;
        }

        Collections.sort(bitRates);
        Collections.reverse(bitRates);
        return bitRates;
    }

    public String getRawInitializationSegmentName(Representation representation) {
        return representation.getSegmentTemplate().getInitialization();
    }

    public String getRawMediaSegmentName(Representation representation) {
        return representation.getSegmentTemplate().getMedia();
    }

    public Long getStartNumber(Representation representation) {
        return representation.getSegmentTemplate().getStartNumber();
    }

    public Duration getMaxSegmentDuration() {
        return mpd.getMaxSegmentDuration();
    }

    public Duration getMediaPresentationDuration() {
        return mpd.getMediaPresentationDuration();
    }

    public Long getDurationOfTemplate(Representation representation) {
        return representation.getSegmentTemplate().getDuration(); // micro-sec
    }

    public Long getTimeScale(Representation representation) {
        return representation.getSegmentTemplate().getTimescale(); // micro-sec
    }

    public double getAvailabilityTimeOffset(String representationId, String contentType) {
        Representation audioRepresentation = getRepresentation(representationId, contentType);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return audioRepresentation.getSegmentTemplate().getAvailabilityTimeOffset();
        } else {
            return 0;
        }
    }

    public long applyAtoIntoDuration(String representationId, long segmentDuration, String contentType) {
        if (representationId == null || segmentDuration <= 0 || contentType == null) { return segmentDuration; }

        double availabilityTimeOffset = getAvailabilityTimeOffset(representationId, contentType); // 0.8
        if (availabilityTimeOffset > 0) {
            // 1000000 > 800000
            segmentDuration = (long) (availabilityTimeOffset * MpdManager.MICRO_SEC);
        }

        return segmentDuration;
    }

    public long getAudioSegmentDuration(String representationId) {
        Representation audioRepresentation = getRepresentation(CONTENT_AUDIO_TYPE, representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            Long duration = getDurationOfTemplate(audioRepresentation);
            if (duration == null) {
                // GET from SegmentTimeline
                duration = audioRepresentation.getSegmentTemplate()
                        .getSegmentTimeline()
                        .get((int) getAudioSegmentSeqNum())
                        .getD(); // micro-sec
            }
            return duration;
        } else {
            return 0;
        }
    }

    public long getAudioSegmentTimeScale(String representationId) {
        Representation audioRepresentation = getRepresentation(CONTENT_AUDIO_TYPE, representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return getTimeScale(audioRepresentation);
        } else {
            return 0;
        }
    }

    public long getVideoSegmentDuration(String representationId) {
        Representation videoRepresentation = getRepresentation(CONTENT_VIDEO_TYPE, representationId);
        if (videoRepresentation != null) {
            // GET from SegmentTemplate
            Long duration = getDurationOfTemplate(videoRepresentation);
            if (duration == null) {
                // GET from SegmentTimeline
                duration = videoRepresentation.getSegmentTemplate()
                        .getSegmentTimeline()
                        .get((int) getVideoSegmentSeqNum())
                        .getD(); // micro-sec
            }
            return duration;
        } else {
            return 0;
        }
    }

    public long getVideoSegmentTimeScale(String representationId) {
        Representation videoRepresentation = getRepresentation(CONTENT_VIDEO_TYPE, representationId);
        if (videoRepresentation != null) {
            // GET from SegmentTemplate
            return getTimeScale(videoRepresentation);
        } else {
            return 0;
        }
    }

    public String getFirstRepresentationId(String contentType) {
        return getRepresentationId(contentType);
    }

    public List<Representation> getRepresentations(String contentType) {
        if (mpd == null || contentType == null) {
            return Collections.emptyList();
        }

        for (Period period : mpd.getPeriods()) {
            for (AdaptationSet adaptationSet : period.getAdaptationSets()) {
                if (adaptationSet.getContentType().equals(contentType)) {
                    return adaptationSet.getRepresentations();
                }
            }
        }

        return Collections.emptyList();
    }

    private String getInitSegmentName(Representation representation, String contentType) {
        if (representation == null) { return null; }

        // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
        String initSegmentName = getRawInitializationSegmentName(representation);
        initSegmentName = initSegmentName.replace(
                AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                getRepresentationId(contentType) + ""
        );
        // outdoor_market_ambiance_Dolby_init1.m4s
        return initSegmentName;
    }

    public String getAudioInitSegmentName(String representationId) {
        return getInitSegmentName(getRepresentation(CONTENT_AUDIO_TYPE, representationId), CONTENT_AUDIO_TYPE);
    }

    public String getAudioMediaSegmentName(String representationId) {
        return getSegmentName(getRepresentation(CONTENT_AUDIO_TYPE, representationId), getAudioSegmentSeqNum(representationId));
    }

    public String getAudioMediaSegmentName(String representationId, long audioSegmentSeqNum) {
        return getSegmentName(getRepresentation(CONTENT_AUDIO_TYPE, representationId), audioSegmentSeqNum);
    }

    public String getAudioMediaSegmentName(long audioSegmentSeqNum) { // first representation
        return getAudioMediaSegmentName(getRepresentationId(CONTENT_AUDIO_TYPE), audioSegmentSeqNum);
    }

    private String getSegmentName(Representation representation, long segmentSeqNum) {
        if (representation == null) { return null; }

        String segmentName = getRawMediaSegmentName(representation);
        // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
        segmentName = segmentName.replace(
                configManager.getRepresentationIdFormat(),
                representation.getId() + ""
        );

        int chunkNumberFormatIndex = segmentName.indexOf(configManager.getChunkNumberFormat());
        if (chunkNumberFormatIndex >= 0) {
            char startCharacter = configManager.getChunkNumberFormat().charAt(0);
            String chunkNumberFormat = segmentName.substring(chunkNumberFormatIndex);
            int finishCharacterIndex = chunkNumberFormat.lastIndexOf(startCharacter);
            chunkNumberFormat = chunkNumberFormat.substring(0, finishCharacterIndex + 1); // 마지막 문자까지 포함

            char lastConfigFormatChar = configManager.getChunkNumberFormat().charAt(configManager.getChunkNumberFormat().length() - 1);
            int lastConfigFormatCharIndex = chunkNumberFormat.indexOf(lastConfigFormatChar);
            if (lastConfigFormatCharIndex >= 0 && lastConfigFormatCharIndex < (chunkNumberFormat.length() - 2)) {
                String segmentNumberFormat = chunkNumberFormat.substring(lastConfigFormatCharIndex + 1, chunkNumberFormat.length() - 2);
                segmentName = segmentName.replace(
                        segmentNumberFormat,
                        String.format(segmentNumberFormat, segmentSeqNum)
                );
            } else {
                segmentName = segmentName.replace(
                        chunkNumberFormat,
                        String.valueOf(segmentSeqNum)
                );
            }
        }
        // outdoor_market_ambiance_Dolby_chunk0_00001.m4s

        return segmentName;
    }

    public String getVideoInitSegmentName(String representationId) {
        return getInitSegmentName(getRepresentation(CONTENT_VIDEO_TYPE, representationId), CONTENT_VIDEO_TYPE);
    }

    public String getVideoMediaSegmentName(String representationId) {
        return getSegmentName(getRepresentation(CONTENT_VIDEO_TYPE, representationId), getVideoSegmentSeqNum(representationId));
    }

    public String getVideoMediaSegmentName(String representationId, long videoSegmentSeqNum) {
        return getSegmentName(getRepresentation(CONTENT_VIDEO_TYPE, representationId), videoSegmentSeqNum);
    }

    public String getVideoMediaSegmentName(long videoSegmentSeqNum) {
        return getVideoMediaSegmentName(getRepresentationId(CONTENT_VIDEO_TYPE), videoSegmentSeqNum);
    }

    public String getRepresentationId(String contentType) {
        List<Representation> representations = getRepresentations(contentType);
        if (representations == null || representations.isEmpty()) {
            return null;
        }
        return representations.get(0).getId();
    }

    public String getRepresentationId(String contentType, int index) {
        if (isOutOfRepresentations(contentType, index)) {
            return null;
        }

        List<Representation> representations = getRepresentations(contentType);
        if (representations == null || representations.isEmpty()) {
            return null;
        }

        return representations.get(index).getId();
    }

    public boolean isOutOfRepresentations(String contentType, int index) {
        return getRepresentations(contentType).size() >= index;
    }

    public void setSegmentStartNumber(String representationId, String contentType) {
        if (contentType == null) { return; }

        // MPD 최초 수신할 때만 경과 시간에 따라 Segment start number 를 설정한다.
        if (contentType.equals(CONTENT_VIDEO_TYPE)) {
            if (getVideoSegmentSeqNum() > 1) { return; }
        } else {
            if (getAudioSegmentSeqNum() > 1) { return; }
        }

        Representation representation = getRepresentation(contentType, representationId);
        if (representation == null) {
            logger.warn("[MpdManager({})] [{}] Fail to set segment start number. Representation is not defined.", dashUnitId, contentType);
            return;
        }

        calculateSegmentNumber(contentType, representation.getId());
    }
    ////////////////////////////////////////////////////////////

    private void setCustomRemoteMpdOptions() {
        //Duration curMpdMaxSegmentDuration = getMaxSegmentDuration();
        long segmentDurationOffsetSec = (long) AppInstance.getInstance().getConfigManager().getRemoteTimeOffset(); // seconds
        //curMpdMaxSegmentDuration = curMpdMaxSegmentDuration.plusSeconds(segmentDurationOffsetSec);

        OffsetDateTime curAst = mpd.getAvailabilityStartTime();
        if (curAst == null) {
            curAst = OffsetDateTime.now();
        }

        remoteMpdAvailabilityStartTime = curAst;
        OffsetDateTime newAst = curAst.plusSeconds(segmentDurationOffsetSec);
        logger.debug("[MpdManager({})] [REMOTE] AvailabilityStartTime has changed. ({} > {}, offset={})",
                dashUnitId,
                curAst, newAst,
                segmentDurationOffsetSec
        );

        mpd = mpd.buildUpon()
                .withAvailabilityStartTime(newAst)
                .withMediaPresentationDuration(Duration.ofSeconds(configManager.getChunkFileDeletionWindowSize()))
                //.withMinBufferTime(Duration.ofSeconds(StreamConfigManager.MIN_BUFFER_TIME))
                //.withMaxSegmentDuration(curMpdMaxSegmentDuration)
                .build();
    }

    private void setCustomLocalMpdOptions() {
        long segmentDurationOffsetSec = (long) AppInstance.getInstance().getConfigManager().getLocalTimeOffset(); // seconds

        OffsetDateTime curAst = mpd.getAvailabilityStartTime();
        if (curAst == null) {
            curAst = OffsetDateTime.now();
        }

        remoteMpdAvailabilityStartTime = curAst;
        OffsetDateTime newAst = curAst.plusSeconds(segmentDurationOffsetSec);
        logger.debug("[MpdManager({})] [LOCAL] AvailabilityStartTime has changed. ({} > {}, offset={})",
                dashUnitId,
                curAst, newAst,
                segmentDurationOffsetSec
        );

        mpd = mpd.buildUpon()
                .withAvailabilityStartTime(newAst)
                .build();
    }

    private Representation getRepresentation(String contentType) {
        List<Representation> representations = getRepresentations(contentType);
        String representationId = getRepresentationId(contentType);
        return representations.stream().filter(representation -> representation.getId().equals(representationId)).findFirst().orElse(null);
    }

    private Representation getRepresentation(String contentType, String representationId) {
        List<Representation> representations = getRepresentations(contentType);
        return representations.stream().filter(representation -> representation.getId().equals(representationId)).findFirst().orElse(null);
    }

    private long getSegmentStartNumber(String contentType) {
        Representation representation = getRepresentation(contentType);
        if (representation == null) { return -1; }

        Long startNumber = representation.getSegmentTemplate().getStartNumber();
        return startNumber != 0 ? startNumber : StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getAudioSegmentSeqNum() { // first representation
        AtomicLong audioSegmentSeqNum = audioSegmentSeqNumMap.get(getRepresentationId(CONTENT_AUDIO_TYPE));
        if (audioSegmentSeqNum == null) {
            return 0;
        }
        return audioSegmentSeqNum.get();
    }

    public long getAudioSegmentSeqNum(String representationId) {
        AtomicLong audioSegmentSeqNum = audioSegmentSeqNumMap.get(representationId);
        if (audioSegmentSeqNum == null) {
            logger.debug("representationId: {}", representationId);
            return 0;
        }
        return audioSegmentSeqNum.get();
    }

    public void setAudioSegmentSeqNum(String representationId, long number) { // first representation
        AtomicLong audioSegmentSeqNum = audioSegmentSeqNumMap.get(representationId);
        if (audioSegmentSeqNum != null) {
            audioSegmentSeqNum.set(number);
        }
    }

    public long incAndGetAudioSegmentSeqNum(String representationId) {
        AtomicLong audioSegmentSeqNum = audioSegmentSeqNumMap.get(representationId);
        if (audioSegmentSeqNum != null) {
            return audioSegmentSeqNum.incrementAndGet();
        }
        return 0;
    }

    public long getVideoSegmentSeqNum() { // first representation
        AtomicLong videoSegmentSeqNum = videoSegmentSeqNumMap.get(getRepresentationId(CONTENT_VIDEO_TYPE));
        if (videoSegmentSeqNum == null) {
            return 0;
        }
        return videoSegmentSeqNum.get();
    }

    public long getVideoSegmentSeqNum(String representationId) {
        AtomicLong videoSegmentSeqNum = videoSegmentSeqNumMap.get(representationId);
        if (videoSegmentSeqNum == null) {
            return 0;
        }
        return videoSegmentSeqNum.get();
    }

    public void setVideoSegmentSeqNum(long number) { // first representation
        AtomicLong videoSegmentSeqNum = videoSegmentSeqNumMap.get(getRepresentationId(CONTENT_VIDEO_TYPE));
        if (videoSegmentSeqNum != null) {
            videoSegmentSeqNum.set(number);
        }
    }

    public void setVideoSegmentSeqNum(String representationId, long number) {
        AtomicLong videoSegmentSeqNum = videoSegmentSeqNumMap.get(representationId);
        if (videoSegmentSeqNum != null) {
            videoSegmentSeqNum.set(number);
        }
    }

    public long incAndGetVideoSegmentSeqNum(String representationId) {
        return videoSegmentSeqNumMap.get(representationId).incrementAndGet();
    }

    public MPD getMpd() {
        return mpd;
    }

    public void setMpd(MPD mpd) {
        this.mpd = mpd;
    }

    public boolean getIsMpdDone() {
        return isMpdDone.get();
    }

    public void setIsMpdDone(boolean isMpdDone) {
        this.isMpdDone.set(isMpdDone);
    }

    public int getCurVideoIndex() {
        return curVideoIndex.get();
    }

    public int getCurAudioIndex() {
        return curAudioIndex.get();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    /**
     * Durations define the amount of intervening time in a time interval and
     *      are represented by the format P[n]Y[n]M[n]DT[n]H[n]M[n]S or P[n]W as shown on the aside.
     *
     * In these representations, the [n] is replaced by the value for each of the date and time elements that follow the [n].
     *      Leading zeros are not required,
     *              but the maximum number of digits for each element should be agreed to by the communicating parties.
     *      The capital letters P, Y, M, W, D, T, H, M, and S are designators
     *              for each of the date and time elements and are not replaced.
     *
     *      P is the duration designator (for period) placed at the start of the duration representation.
     *      Y is the year designator that follows the value for the number of years.
     *      M is the month designator that follows the value for the number of months.
     *       W is the week designator that follows the value for the number of weeks.
     *      D is the day designator that follows the value for the number of days.
     *      T is the time designator that precedes the time components of the representation.
     *      H is the hour designator that follows the value for the number of hours.
     *      M is the minute designator that follows the value for the number of minutes.
     *      S is the second designator that follows the value for the number of seconds.
     *
     * For example,
     *      "P3Y6M4DT12H30M5S" represents a duration of
     *              "three years, six months, four days, twelve hours, thirty minutes, and five seconds".
     *
     * Date and time elements including their designator may be omitted if their value is zero,
     *      and lower-order elements may also be omitted for reduced precision.
     *
     *      For example, "P23DT23H" and "P4Y" are both acceptable duration representations.
     *      However, at least one element must be present,
     *              thus "P" is not a valid representation for a duration of 0 seconds.
     *      "PT0S" or "P0D", however, are both valid and represent the same duration.
     *
     * To resolve ambiguity,
     *      "P1M" is a one-month duration and "PT1M" is a one-minute duration
     *              (note the time designator, T, that precedes the time value).
     *      The smallest value used may also have a decimal fraction, as in "P0.5Y" to indicate half a year.
     *      This decimal fraction may be specified with either a comma or a full stop, as in "P0,5Y" or "P0.5Y".
     *      The standard does not prohibit date and time values in a duration representation
     *      from exceeding their "carry over points" except as noted below.
     *
     *      Thus, "PT36H" could be used as well as "P1DT12H" for representing the same duration.
     *      But keep in mind that "PT36H" is not the same as "P1DT12H" when switching from or to Daylight saving time.
     *
     * Alternatively,
     *      a format for duration based on combined date and time representations
     *      may be used by agreement between the communicating parties either
     *      in the basic format PYYYYMMDDThhmmss or in the extended format P[YYYY]-[MM]-[DD]T[hh]:[mm]:[ss].
     *
     *      For example, the first duration shown above would be "P0003-06-04T12:30:05".
     *      However, individual date and time values cannot exceed their moduli
     *              (e.g. a value of 13 for the month or 25 for the hour would not be permissible).
     *
     * @param timeString duration
     * @return real time
     */
    private double parseTime(String timeString) {
        double time = 0;
        int begin = timeString.indexOf("PT") + 2;
        int end;
        end = timeString.indexOf("Y");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 365 * 24 * 60 * 60;
            begin = end + 1;
        }
        if (timeString.indexOf("M") != timeString.lastIndexOf("M")) {
            end = timeString.indexOf("M");
            if (end != -1) {
                time += Double.parseDouble(timeString.substring(begin, end)) * 30 * 24 * 60 * 60;
                begin = end + 1;
            }
        }
        end = timeString.indexOf("DT");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 24 * 60 * 60;
            begin = end + 2;
        }
        end = timeString.indexOf("H");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 60 * 60;
            begin = end + 1;
        }
        end = timeString.lastIndexOf("M");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 60;
            begin = end + 1;
        }
        end = timeString.indexOf("S");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end));
        }
        return time;
    }
    ////////////////////////////////////////////////////////////

}
