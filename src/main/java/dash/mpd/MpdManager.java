package dash.mpd;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
    transient public static final String CONTENT_AUDIO_TYPE = "audio";
    transient public static final String CONTENT_VIDEO_TYPE = "video";
    transient public static final long MICRO_SEC = 1000000;

    private final String dashUnitId;

    private final MPDParser mpdParser;
    private MPDValidator mpdValidator = null;
    private MPD mpd = null;
    private final String targetMpdPath;
    private final AtomicBoolean isMpdDone = new AtomicBoolean(false);

    private List<AtomicLong> videoSegmentSeqNumList;
    private List<AtomicLong> audioSegmentSeqNumList;

    private List<Integer> videoRepresentationIdList;
    private List<Integer> audioRepresentationIdList;

    private int curVideoIndex; // 비디오 Representation List 중 현재 비디오 ID
    private int curAudioIndex; // 오디오 Representation List 중 현재 오디오 ID

    private final int curPeriodId = 0;
    private boolean isVideoDefinitionFirst = false;
    private int curAudioRepresentationCount = 0;
    private int curVideoRepresentationCount = 0;

    transient private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    transient private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MpdManager(String dashUnitId, String targetMpdPath) {
        this.dashUnitId = dashUnitId;
        this.targetMpdPath = targetMpdPath;
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
            } else {
                logger.trace("[MpdManager({})] Success to parse the mpd. (path={}, mpd=\n{})", dashUnitId, targetMpdPath, mpd);
            }
            /////////////////////////////////////////

            /////////////////////////////////////////
            // 3) GET FILED INFORMATION (비디오, 오디오 정의 순서)
            List<AdaptationSet> adaptationSets = mpd.getPeriods().get(curPeriodId).getAdaptationSets();
            if (adaptationSets == null || adaptationSets.isEmpty()) {
                logger.warn("[MpdManager({})] Fail to get the adaptationSets. (path={})", dashUnitId, targetMpdPath);
                return false;
            }

            int adaptationSetIndex = 0;
            int audioRepresentationCount = 0;
            int videoRepresentationCount = 0;
            for (AdaptationSet adaptationSet : adaptationSets) {
                if (adaptationSet == null) { continue; }

                if (adaptationSet.getMimeType() != null) {
                    if (adaptationSet.getMimeType().contains(CONTENT_AUDIO_TYPE)) {
                        audioRepresentationCount += adaptationSet.getRepresentations().size();
                        if (adaptationSetIndex == 0) { isVideoDefinitionFirst = false; }
                    } else if (adaptationSet.getMimeType().contains(CONTENT_VIDEO_TYPE)) {
                        videoRepresentationCount += adaptationSet.getRepresentations().size();
                        if (adaptationSetIndex == 0) { isVideoDefinitionFirst = true; }
                    }
                } else if (adaptationSet.getContentType() != null) {
                    if (adaptationSet.getContentType().contains(CONTENT_AUDIO_TYPE)) {
                        audioRepresentationCount += adaptationSet.getRepresentations().size();
                        if (adaptationSetIndex == 0) { isVideoDefinitionFirst = false; }
                    } else if (adaptationSet.getContentType().contains(CONTENT_VIDEO_TYPE)) {
                        videoRepresentationCount += adaptationSet.getRepresentations().size();
                        if (adaptationSetIndex == 0) { isVideoDefinitionFirst = true; }
                    }
                }
                adaptationSetIndex++;
            }
            logger.trace("[MpdManager({})] audioRepresentationCount: {}, videoRepresentationCount: {}",
                    dashUnitId, audioRepresentationCount, videoRepresentationCount
            );

            if (isVideoDefinitionFirst) {
                // VIDEO REPRESENTATION
                int audioRepresentationStartIndex = 0;
                if (configManager.isAudioOnly()) {
                    videoRepresentationIdList = null;
                    videoSegmentSeqNumList = null;
                    curVideoIndex = -1;
                } else {
                    if (videoRepresentationCount > 0) {
                        if (videoRepresentationIdList != null && !videoRepresentationIdList.isEmpty()) {
                            if (videoRepresentationCount != curVideoRepresentationCount) {
                                List<Integer> newVideoRepresentationIdList;
                                List<AtomicLong> newVideoSegmentSeqNumList;
                                if (videoRepresentationCount > curVideoRepresentationCount) {
                                    newVideoRepresentationIdList = new ArrayList<>(videoRepresentationIdList);
                                    newVideoSegmentSeqNumList = new ArrayList<>(videoSegmentSeqNumList);
                                    for (int i = curVideoRepresentationCount; i < videoRepresentationCount; i++) {
                                        newVideoRepresentationIdList.add(i);
                                        newVideoSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                                    }
                                } else {
                                    newVideoRepresentationIdList = new ArrayList<>();
                                    newVideoSegmentSeqNumList = new ArrayList<>();
                                    for (int i = 0; i < curVideoRepresentationCount; i++) {
                                        newVideoRepresentationIdList.add(i);
                                        newVideoSegmentSeqNumList.add(videoSegmentSeqNumList.get(i));
                                    }
                                }
                                videoRepresentationIdList = newVideoRepresentationIdList;
                                videoSegmentSeqNumList = newVideoSegmentSeqNumList;
                            }
                            audioRepresentationStartIndex = videoRepresentationIdList.size();
                        } else {
                            videoRepresentationIdList = new ArrayList<>();
                            int videoRepresentationIndex = 0;
                            for (; videoRepresentationIndex < videoRepresentationCount; videoRepresentationIndex++) {
                                videoRepresentationIdList.add(videoRepresentationIndex);
                            }
                            audioRepresentationStartIndex = videoRepresentationIndex;

                            videoSegmentSeqNumList = new ArrayList<>();
                            for (int i = 0; i < videoRepresentationIdList.size(); i++) {
                                videoSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                            }
                        }
                        curVideoRepresentationCount = videoRepresentationCount;
                        curVideoIndex = videoRepresentationIdList.get(0);
                    } else {
                        videoRepresentationIdList = null;
                        videoSegmentSeqNumList = null;
                        curVideoIndex = -1;
                    }
                }

                // AUDIO REPRESENTATION
                if (audioRepresentationCount > 0) {
                    if (audioRepresentationIdList != null && !audioRepresentationIdList.isEmpty()) {
                        if (audioRepresentationCount != curAudioRepresentationCount) {
                            List<Integer> newAudioRepresentationIdList;
                            List<AtomicLong> newAudioSegmentSeqNumList;
                            if (audioRepresentationCount > curAudioRepresentationCount) {
                                newAudioRepresentationIdList = new ArrayList<>(audioRepresentationIdList);
                                newAudioSegmentSeqNumList = new ArrayList<>(audioSegmentSeqNumList);
                                for (int i = curAudioRepresentationCount; i < audioRepresentationCount; i++) {
                                    newAudioRepresentationIdList.add(audioRepresentationStartIndex);
                                    newAudioSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                                    audioRepresentationStartIndex++;
                                }
                            } else {
                                newAudioRepresentationIdList = new ArrayList<>();
                                newAudioSegmentSeqNumList = new ArrayList<>();
                                for (int i = 0; i < audioRepresentationCount; i++) {
                                    newAudioRepresentationIdList.add(audioRepresentationStartIndex);
                                    newAudioSegmentSeqNumList.add(audioSegmentSeqNumList.get(i));
                                    audioRepresentationStartIndex++;
                                }
                            }
                            audioRepresentationIdList = newAudioRepresentationIdList;
                            audioSegmentSeqNumList = newAudioSegmentSeqNumList;
                        }
                    } else {
                        audioRepresentationIdList = new ArrayList<>();
                        for (int i = 0; i < audioRepresentationCount; i++) {
                            audioRepresentationIdList.add(audioRepresentationStartIndex);
                            audioRepresentationStartIndex++;
                        }
                        audioSegmentSeqNumList = new ArrayList<>();
                        for (int i = 0; i < audioRepresentationIdList.size(); i++) {
                            audioSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                        }
                    }

                    curAudioRepresentationCount = audioRepresentationCount;
                    curAudioIndex = audioRepresentationIdList.get(0);
                } else {
                    audioRepresentationIdList = null;
                    audioSegmentSeqNumList = null;
                    curAudioIndex = -1;
                }
            } else { // audio definition first
                // AUDIO REPRESENTATION
                int videoRepresentationStartIndex = 0;
                if (audioRepresentationCount > 0) {
                    if (audioRepresentationIdList != null && !audioRepresentationIdList.isEmpty()) {
                        if (audioRepresentationCount != curAudioRepresentationCount) {
                            List<Integer> newAudioRepresentationIdList;
                            List<AtomicLong> newAudioSegmentSeqNumList;
                            if (audioRepresentationCount > curAudioRepresentationCount) {
                                newAudioRepresentationIdList = new ArrayList<>(audioRepresentationIdList);
                                newAudioSegmentSeqNumList = new ArrayList<>(audioSegmentSeqNumList);
                                for (int i = curAudioRepresentationCount; i < audioRepresentationCount; i++) {
                                    newAudioRepresentationIdList.add(i);
                                    newAudioSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                                }
                            } else {
                                newAudioRepresentationIdList = new ArrayList<>();
                                newAudioSegmentSeqNumList = new ArrayList<>();
                                for (int i = 0; i < audioRepresentationCount; i++) {
                                    newAudioRepresentationIdList.add(i);
                                    newAudioSegmentSeqNumList.add(audioSegmentSeqNumList.get(i));
                                }
                            }
                            audioRepresentationIdList = newAudioRepresentationIdList;
                            audioSegmentSeqNumList = newAudioSegmentSeqNumList;
                        }

                        videoRepresentationStartIndex = audioRepresentationIdList.size();
                    } else {
                        audioRepresentationIdList = new ArrayList<>();
                        int audioRepresentationIndex = 0;
                        for (; audioRepresentationIndex < audioRepresentationCount; audioRepresentationIndex++) {
                            audioRepresentationIdList.add(audioRepresentationIndex);
                        }
                        audioSegmentSeqNumList = new ArrayList<>();
                        for (int i = 0; i < audioRepresentationIdList.size(); i++) {
                            audioSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                        }
                        videoRepresentationStartIndex = audioRepresentationIndex;
                    }

                    curAudioRepresentationCount = audioRepresentationCount;
                    curAudioIndex = audioRepresentationIdList.get(0);
                } else {
                    audioRepresentationIdList = null;
                    audioSegmentSeqNumList = null;
                    curAudioIndex = -1;
                }

                // VIDEO REPRESENTATION
                if (configManager.isAudioOnly()) {
                    videoRepresentationIdList = null;
                    videoSegmentSeqNumList = null;
                    curVideoIndex = -1;
                } else {
                    if (videoRepresentationCount > 0) {
                        if (videoRepresentationIdList != null && !videoRepresentationIdList.isEmpty()) {
                            if (videoRepresentationCount != curVideoRepresentationCount) {
                                List<Integer> newVideoRepresentationIdList;
                                List<AtomicLong> newVideoSegmentSeqNumList;
                                if (videoRepresentationCount > curVideoRepresentationCount) {
                                    newVideoRepresentationIdList = new ArrayList<>(videoRepresentationIdList);
                                    newVideoSegmentSeqNumList = new ArrayList<>(videoSegmentSeqNumList);
                                    for (int i = curVideoRepresentationCount; i < videoRepresentationCount; i++) {
                                        newVideoRepresentationIdList.add(videoRepresentationStartIndex);
                                        newVideoSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                                        videoRepresentationStartIndex++;
                                    }
                                } else {
                                    newVideoRepresentationIdList = new ArrayList<>();
                                    newVideoSegmentSeqNumList = new ArrayList<>();
                                    for (int i = 0; i < videoRepresentationCount; i++) {
                                        newVideoRepresentationIdList.add(videoRepresentationStartIndex);
                                        newVideoSegmentSeqNumList.add(videoSegmentSeqNumList.get(i));
                                        videoRepresentationStartIndex++;
                                    }
                                }
                                videoRepresentationIdList = newVideoRepresentationIdList;
                                videoSegmentSeqNumList = newVideoSegmentSeqNumList;
                            }
                        } else {
                            videoRepresentationIdList = new ArrayList<>();
                            for (int i = 0; i < videoRepresentationCount; i++) {
                                videoRepresentationIdList.add(videoRepresentationStartIndex);
                                videoRepresentationStartIndex++;
                            }

                            videoSegmentSeqNumList = new ArrayList<>();
                            for (int i = 0; i < videoRepresentationIdList.size(); i++) {
                                videoSegmentSeqNumList.add(new AtomicLong(StreamConfigManager.DEFAULT_SEGMENT_START_NUMBER));
                            }
                        }

                        curVideoIndex = videoRepresentationIdList.get(0);
                    } else {
                        videoRepresentationIdList = null;
                        videoSegmentSeqNumList = null;
                        curVideoIndex = -1;
                    }
                }
            }
            /*logger.debug("[MpdManager({})] Audio ID list: {}, Cur Audio ID: {} / Video ID list: {}, Cur Video ID: {}",
                    dashUnitId,
                    audioRepresentationIdList, curAudioId,
                    videoRepresentationIdList == null? "NONE" : videoRepresentationIdList, curVideoId
            );*/
            /////////////////////////////////////////

            /////////////////////////////////////////
            // 4) DYNAMIC STREAM 인 경우 MPD 수정
            if (isRemote) {
                if (mpd.getType().equals(PresentationType.DYNAMIC)) {
                    ///////////////////////////////////
                    // 현재 세그먼트 번호 명시 (비디오 & 오디오)

                    /*int adaptationId = 0;
                    if (!configManager.isAudioOnly() && getVideoSegmentSeqNum() != 1) {
                        for (int curVideoId : videoRepresentationIdList) {
                            setCustomRepresentationOptions(adaptationId, curVideoId, CONTENT_VIDEO_TYPE);
                        }
                        adaptationId++;
                    }

                    if (getAudioSegmentSeqNum() != 1) {
                        for (int curAudioId : audioRepresentationIdList) {
                            setCustomRepresentationOptions(adaptationId, curAudioId, CONTENT_AUDIO_TYPE);
                        }
                    }*/
                    ///////////////////////////////////

                    ///////////////////////////////////
                    setCustomMpdOptions();
                    writeMpd();
                    ///////////////////////////////////
                }
            }
            /////////////////////////////////////////

            //logger.debug("[MpdManager({})] MPD PARSE DONE (path={})", dashUnitId, targetMpdPath);
        } catch (Exception e) {
            logger.warn("[MpdManager({})] (targetMpdPath={}) parseMpd.Exception", dashUnitId, targetMpdPath, e);
            return false;
        }

        return true;
    }

    public void calculateSegmentNumber(String contentType) {
        // [Current Time] - [MPD.ast] = [미디어 스트림 생성 후 경과 시간] = T
        // T / [segment.timescale * segment.duration] = segment number
        long segmentTimeScale = getVideoSegmentTimeScale() / 1000; // milli-sec
        long segmentDuration = getVideoSegmentDuration() / 1000; // milli-sec
        long segmentTime = segmentTimeScale * segmentDuration; // milli-sec
        logger.debug("[MpdManager({})] segmentTimeScale: {}, segmentDuration: {}, segmentTime: {}", dashUnitId, segmentTimeScale, segmentDuration, segmentTime);

        OffsetDateTime mpdAvailabilityStartTime = mpd.getAvailabilityStartTime(); // OffsetDateTime
        long mediaStartTime = mpdAvailabilityStartTime.toInstant().toEpochMilli();
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - mediaStartTime;
        logger.debug("[MpdManager({})] mediaStartTime: {}, currentTime: {}, elapsedTime: {}", dashUnitId, mediaStartTime, currentTime, elapsedTime);

        int segmentNumber = (int) (elapsedTime / segmentTime);
        if (segmentNumber != 0) {
            logger.debug("[MpdManager({})] [{}] Segment Start Number: {}", dashUnitId, contentType, segmentNumber);
            if (contentType.equals(CONTENT_VIDEO_TYPE)) {
                setVideoSegmentSeqNum(segmentNumber);
            } else {
                setAudioSegmentSeqNum(segmentNumber);
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
                    targetMpdPath,
                    mpdString.getBytes(),
                    false
            );
        } catch (Exception e) {
            logger.warn("[MpdManager({})] Fail to write the mpd. (path={})", dashUnitId, targetMpdPath);
        }
    }

    public String writeAsString() throws JsonProcessingException {
        return mpdParser.writeAsString(mpd);
    }

    public void makeInitSegment(FileManager fileManager, String targetInitSegPath, byte[] content) {
        if (targetInitSegPath == null) {
            return;
        }

        logger.debug("[MpdManager({})] [makeInitSegment] > [targetInitSegPath: {}]", dashUnitId, targetInitSegPath);
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

    public double getAvailabilityTimeOffset(String contentType) {
        List<Representation> representations = getRepresentations(contentType);
        int representationId = getRepresentationIndex(contentType);
        Representation audioRepresentation = representations.get(representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return audioRepresentation.getSegmentTemplate().getAvailabilityTimeOffset();
        } else {
            return 0;
        }
    }

    public long applyAtoIntoDuration(long segmentDuration, String contentType) {
        if (segmentDuration <= 0 || contentType == null) { return segmentDuration; }

        double availabilityTimeOffset = getAvailabilityTimeOffset(contentType); // 0.8
        if (availabilityTimeOffset > 0) {
            // 1000000 > 800000
            segmentDuration = (long) (availabilityTimeOffset * MpdManager.MICRO_SEC);
        }

        return segmentDuration;
    }

    public long getAudioSegmentDuration() {
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        int representationId = getRepresentationIndex(CONTENT_AUDIO_TYPE);
        Representation audioRepresentation = representations.get(representationId);
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

    public long getAudioSegmentTimeTimeScale() {
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        int representationId = getRepresentationIndex(CONTENT_VIDEO_TYPE);
        Representation audioRepresentation = representations.get(representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return getTimeScale(audioRepresentation);
        } else {
            return 0;
        }
    }

    public long getVideoSegmentDuration() {
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        int representationId = getRepresentationIndex(CONTENT_VIDEO_TYPE);
        Representation audioRepresentation = representations.get(representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            Long duration = getDurationOfTemplate(audioRepresentation);
            if (duration == null) {
                // GET from SegmentTimeline
                duration = audioRepresentation.getSegmentTemplate()
                        .getSegmentTimeline()
                        .get((int) getVideoSegmentSeqNum())
                        .getD(); // micro-sec
            }
            return duration;
        } else {
            return 0;
        }
    }

    public long getVideoSegmentTimeScale() {
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        int representationId = getRepresentationIndex(CONTENT_VIDEO_TYPE);
        Representation audioRepresentation = representations.get(representationId);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return getTimeScale(audioRepresentation);
        } else {
            return 0;
        }
    }

    public List<Representation> getRepresentations(String contentType) {
        if (mpd == null || contentType == null) {
            return null;
        }

        for (Period period : mpd.getPeriods()) {
            for (AdaptationSet adaptationSet : period.getAdaptationSets()) {
                if (adaptationSet.getContentType().equals(contentType)) {
                    return adaptationSet.getRepresentations();
                }
            }
        }

        return null;
    }

    public String getAudioInitSegmentName() {
        if (curAudioIndex < 0) { return null; }

        int representationId = getRepresentationIndex(CONTENT_AUDIO_TYPE);
        List<Representation> representations = getRepresentations(MpdManager.CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String audioInitSegmentName = getRawInitializationSegmentName(representations.get(representationId));
            audioInitSegmentName = audioInitSegmentName.replace(
                    AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                    curAudioIndex + ""
            );
            // outdoor_market_ambiance_Dolby_init1.m4s
            return audioInitSegmentName;
        }

        return null;
    }

    public String getAudioMediaSegmentName() {
        if (curAudioIndex < 0) { return null; }

        int representationId = getRepresentationIndex(CONTENT_AUDIO_TYPE);
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            String mediaSegmentName = getRawMediaSegmentName(representations.get(representationId));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getRepresentationIdFormat(),
                    curAudioIndex + ""
            );
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getChunkNumberFormat(),
                    String.format("%05d", getAudioSegmentSeqNum())
            );
            // outdoor_market_ambiance_Dolby_chunk0_00001.m4s
            return mediaSegmentName;
        }

        return null;
    }

    public String getVideoInitSegmentName() {
        if (curVideoIndex < 0) { return null; }

        int representationId = getRepresentationIndex(CONTENT_VIDEO_TYPE);
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String videoInitSegmentName = getRawInitializationSegmentName(representations.get(representationId));
            videoInitSegmentName = videoInitSegmentName.replace(
                    AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                    curVideoIndex + ""
            );
            // outdoor_market_ambiance_Dolby_init0.m4s
            return videoInitSegmentName;
        }

        return null;
    }

    public String getVideoMediaSegmentName() {
        if (curVideoIndex < 0) { return null; }

        int representationId = getRepresentationIndex(CONTENT_VIDEO_TYPE);
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            String mediaSegmentName = getRawMediaSegmentName(representations.get(representationId));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getRepresentationIdFormat(),
                    curVideoIndex + ""
            );
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getChunkNumberFormat(),
                    String.format("%05d", getVideoSegmentSeqNum())
            );
            // outdoor_market_ambiance_Dolby_chunk0_00001.m4s
            return mediaSegmentName;
        }

        return null;
    }

    /**
     * @ Order correction
     *      Video AdaptationSet 이 먼저 정의된 경우
     *          audio first representation 가 4 이고, curAudioId 가 5 이면,
     *          representations.get(representationId) 에 들어갈 representationId 값이 1 이 되서,
     *          [두 번째] Representation 을 찾는 것으로 계산되어야 한다.
     */
    public int getRepresentationIndex(String contentType) {
        int representationId;
        if (isVideoDefinitionFirst) {
            if (contentType.equals(CONTENT_AUDIO_TYPE)) {
                representationId = curAudioIndex;
                representationId -= audioRepresentationIdList.get(0);
            } else {
                representationId = curVideoIndex;
            }
        } else {
            if (contentType.equals(CONTENT_AUDIO_TYPE)) {
                representationId = curAudioIndex;
            } else {
                representationId = curVideoIndex;
                representationId -= videoRepresentationIdList.get(0);
            }
        }
        return representationId;
    }

    public void setSegmentStartNumber(String contentType) {
        if (contentType == null) { return; }

        int representationId = getRepresentationIndex(contentType);

        List<Representation> representations = getRepresentations(contentType);
        if (representations == null || representations.isEmpty()) {
            logger.warn("[MpdManager({})] [{}] Fail to set segment start number. Representations is not defined.", dashUnitId, contentType);
            return;
        }
        Representation representation = representations.get(representationId);
        if (representation == null) {
            logger.warn("[MpdManager({})] [{}] Fail to set segment start number. Representation({}) is not defined.", dashUnitId, contentType, representationId);
            return;
        }

        calculateSegmentNumber(contentType);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    /**
     * @fn private void setRepresentation(int adaptationSetIndex, List<Representation> representations, Representation representation, int representationId)
     * @brief MPD 에 특정 Representation 에 프로그램 설정에 따라 옵션들을 설정하는 함수
     * @param adaptationSetIndex Media type (generally, video: 0, audio: 1)
     * @param representationId 설정할 Representation ID
     * @param contentType 미디어 타입 (video or audio)
     *
     * (* [()] 안의 숫자는 최소 필드 개수)
     * MPD (1)
     *    >> List<Period> (1)
     *          >> List<AdaptationSet> (2)
     *                >> List<Representation> (1)
     *                        >> SegmentTemplate (1)
     *                              (>> SegmentTimeline) (1)
     */
    private void setCustomRepresentationOptions(int adaptationSetIndex, int representationId, String contentType) {
        if (adaptationSetIndex < 0 || representationId < 0 || contentType == null) { return; }

        ////////////////////////////////
        // 1) SET Representation
        // @ Order correction
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        if (isVideoDefinitionFirst) {
            if (contentType.equals(CONTENT_AUDIO_TYPE)) {
                representationId -= audioRepresentationIdList.get(0);
            }
        } else {
            if (!configManager.isAudioOnly()) {
                if (contentType.equals(CONTENT_AUDIO_TYPE)) {
                    representationId -= videoRepresentationIdList.get(0);
                }
            }
        }

        List<Representation> representations = getRepresentations(contentType);
        if (representations == null || representations.isEmpty()) {
            logger.warn("[MpdManager({})] [{}] Fail to set segment start number. Representations is not defined.", dashUnitId, contentType);
            return;
        }
        Representation representation = representations.get(representationId);
        if (representation == null) {
            logger.warn("[MpdManager({})] [{}] Fail to set segment start number. Representation({}) is not defined.",
                    dashUnitId, contentType, contentType.equals(CONTENT_AUDIO_TYPE)? curAudioIndex : curVideoIndex
            );
            return;
        }

        // 1-1) SET Media segment duration & availabilityTimeOffset
        //double segmentDurationOffsetSec = AppInstance.getInstance().getConfigManager().getTimeOffset(); // seconds
        //long segmentDurationOffsetMicroSec = (long) (segmentDurationOffsetSec * MICRO_SEC); // to micro-seconds;

        //long curSegmentDuration = representation.getSegmentTemplate().getDuration(); // micro-seconds
        //long newSegmentDuration = curSegmentDuration + segmentDurationOffsetMicroSec; // micro-seconds

        SegmentTemplate newSegmentTemplate = representation.getSegmentTemplate().buildUpon()
                //.withDuration(newSegmentDuration) // duration
                .withStartNumber(contentType.equals(CONTENT_VIDEO_TYPE)? getVideoSegmentSeqNum() : getAudioSegmentSeqNum())
                /*.withAvailabilityTimeOffset(
                        (((double) newSegmentDuration) / MICRO_SEC)
                                - StreamConfigManager.AVAILABILITY_TIME_OFFSET_FACTOR
                )*/ // availabilityTimeOffset
                .build();
        representation = representation.buildUpon().withSegmentTemplate(newSegmentTemplate).build();
        //logger.debug("[MpdManager({})] [{}] Representation({}) > [duration: {}, ato: {}]",
        logger.debug("[MpdManager({})] [{}] Representation({}) > [startNumber: {}]",
                dashUnitId, contentType, contentType.equals(CONTENT_AUDIO_TYPE)? curAudioIndex : curVideoIndex,
                representation.getSegmentTemplate().getStartNumber()
                //representation.getSegmentTemplate().getDuration(),
                //representation.getSegmentTemplate().getAvailabilityTimeOffset()
        );

        List<Representation> newVideoRepresentations = new ArrayList<>(representations);
        newVideoRepresentations.set(representationId, representation);
        ////////////////////////////////

        ////////////////////////////////
        // 2) SET AdaptationSet
        List<AdaptationSet> newAdaptationSets = setCustomAdaptationSetOptions(adaptationSetIndex, newVideoRepresentations);
        ////////////////////////////////

        ////////////////////////////////
        // 3) SET Period
        List<Period> newPeriods = setCustomPeriodOptions(curPeriodId, newAdaptationSets); // TODO : Set Period ID > 전달할 미디어 개수에 따라 변동됨
        ////////////////////////////////

        ////////////////////////////////
        // 4) SET MPD
        mpd = mpd.buildUpon().withPeriods(newPeriods).build();
        ////////////////////////////////
    }

    private List<AdaptationSet> setCustomAdaptationSetOptions(int adaptationSetIndex, List<Representation> representations) {
        if (adaptationSetIndex < 0 || representations == null || representations.isEmpty()) { return null; }

        List<AdaptationSet> adaptationSets =  mpd.getPeriods().get(curPeriodId).getAdaptationSets();
        if (adaptationSets == null || adaptationSets.isEmpty()) { return null; }

        AdaptationSet newVideoSet = adaptationSets.get(adaptationSetIndex)
                .buildUpon()
                .withRepresentations(representations)
                .build();
        List<AdaptationSet> newAdaptationSets = new ArrayList<>(adaptationSets);
        newAdaptationSets.set(adaptationSetIndex, newVideoSet);

        return newAdaptationSets;
    }

    private List<Period> setCustomPeriodOptions(int periodIndex, List<AdaptationSet> adaptationSets) {
        if (periodIndex < 0 || adaptationSets == null || adaptationSets.isEmpty()) { return null; }

        List<Period> periods = mpd.getPeriods();
        if (periods == null || periods.isEmpty()) { return null; }

        Period newPeriod = periods.get(periodIndex);
        newPeriod = newPeriod
                .buildUpon()
                .withAdaptationSets(adaptationSets)
                .build();
        List<Period> newPeriods = new ArrayList<>(periods);
        newPeriods.set(periodIndex, newPeriod);

        return newPeriods;
    }

    private void setCustomMpdOptions() {
        //Duration curMpdMaxSegmentDuration = getMaxSegmentDuration();
        long segmentDurationOffsetSec = (long) AppInstance.getInstance().getConfigManager().getTimeOffset(); // seconds
        //curMpdMaxSegmentDuration = curMpdMaxSegmentDuration.plusSeconds(segmentDurationOffsetSec);

        OffsetDateTime curAst = mpd.getAvailabilityStartTime();
        OffsetDateTime newAst = curAst.plusSeconds(segmentDurationOffsetSec);
        logger.debug("[MpdManager({})] AvailabilityStartTime has changed. ({} > {}, offset={})",
                dashUnitId,
                curAst, newAst,
                segmentDurationOffsetSec
        );

        mpd = mpd.buildUpon()
                .withAvailabilityStartTime(newAst)
                .withMediaPresentationDuration(Duration.ofSeconds(configManager.getChunkFileDeletionIntervalSeconds()))
                //.withMinBufferTime(Duration.ofSeconds(StreamConfigManager.MIN_BUFFER_TIME))
                //.withMaxSegmentDuration(curMpdMaxSegmentDuration)
                .build();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getAudioSegmentSeqNum() {
        return audioSegmentSeqNumList.get(getRepresentationIndex(CONTENT_AUDIO_TYPE)).get();
    }

    public void setAudioSegmentSeqNum(long number) {
        audioSegmentSeqNumList.get(getRepresentationIndex(CONTENT_AUDIO_TYPE)).set(number);
    }

    public long incAndGetAudioSegmentSeqNum() {
        return audioSegmentSeqNumList.get(getRepresentationIndex(CONTENT_AUDIO_TYPE)).incrementAndGet();
    }

    public long getVideoSegmentSeqNum() {
        return videoSegmentSeqNumList.get(getRepresentationIndex(CONTENT_VIDEO_TYPE)).get();
    }

    public void setVideoSegmentSeqNum(long number) {
        videoSegmentSeqNumList.get(getRepresentationIndex(CONTENT_VIDEO_TYPE)).set(number);
    }

    public long incAndGetVideoSegmentSeqNum() {
        return videoSegmentSeqNumList.get(getRepresentationIndex(CONTENT_VIDEO_TYPE)).incrementAndGet();
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
