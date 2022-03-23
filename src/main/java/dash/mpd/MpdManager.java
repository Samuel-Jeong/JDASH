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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MpdManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MpdManager.class);

    transient public static final String CONTENT_AUDIO_TYPE = "audio";
    transient public static final String CONTENT_VIDEO_TYPE = "video";

    private final String dashUnitId;

    private final MPDParser mpdParser;
    private MPDValidator mpdValidator = null;
    private MPD mpd = null;
    private final AtomicBoolean isMpdDone = new AtomicBoolean(false);

    private final AtomicLong audioSegmentSeqNum = new AtomicLong(0);
    private final AtomicLong videoSegmentSeqNum = new AtomicLong(0);

    private final int videoRepresentationId;
    private final int audioRepresentationId;

    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MpdManager(String dashUnitId) {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        this.dashUnitId = dashUnitId;
        this.mpdParser = new MPDParser();
        try {
            mpdValidator = new MPDValidator(mpdParser, configManager.getValidationXsdPath());
        } catch (Exception e) {
            logger.warn("[DashServer] Fail to make a mpd validator.");
        }

        if (configManager.isAudioOnly()) {
            videoRepresentationId = -1;
            audioRepresentationId = 0;
        } else {
            videoRepresentationId = 0;
            audioRepresentationId = 1;
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getMediaPresentationTimeAsSec() {
        if (mpd == null) {
            return 0;
        }
        return mpd.getMediaPresentationDuration().getSeconds();
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

    public double getAvailabilityTimeOffset() {
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        Representation audioRepresentation = representations.get(0);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            return audioRepresentation.getSegmentTemplate().getAvailabilityTimeOffset();
        } else {
            return 0;
        }
    }

    public long getAudioSegmentDuration() {
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        Representation audioRepresentation = representations.get(0);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            Long duration = getDurationOfTemplate(audioRepresentation);
            if (duration == null) {
                // GET from SegmentTimeline
                duration = audioRepresentation.getSegmentTemplate()
                        .getSegmentTimeline()
                        .get((int) getAudioSegmentDuration())
                        .getD(); // micro-sec
            }
            return duration;
        } else {
            return 0;
        }
    }

    public long getVideoSegmentDuration() {
        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        Representation audioRepresentation = representations.get(0);
        if (audioRepresentation != null) {
            // GET from SegmentTemplate
            Long duration = getDurationOfTemplate(audioRepresentation);
            if (duration == null) {
                // GET from SegmentTimeline
                duration = audioRepresentation.getSegmentTemplate()
                        .getSegmentTimeline()
                        .get((int) getVideoSegmentDuration())
                        .getD(); // micro-sec
            }
            return duration;
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
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean parseMpd(String targetMpdPath) {
        try {
            /////////////////////////////////////////
            // 1) CHECK FILE STREAM
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(targetMpdPath);
            } catch (Exception e) {
                logger.warn("[MpdManager] Fail to get the input stream. (filePath={})", targetMpdPath);
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
            // 3) DYNAMIC STREAM 인 경우 MPD 수정
            if (mpd.getType().equals(PresentationType.DYNAMIC)) {
                int adaptationSetIndex = 0;
                if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
                    setCustomRepresentationOptions(adaptationSetIndex, 0, CONTENT_VIDEO_TYPE); // TODO : Set Video Representation ID
                    adaptationSetIndex++;
                }
                setCustomRepresentationOptions(adaptationSetIndex, 0, CONTENT_AUDIO_TYPE); // TODO : Set Audio Representation ID

                setCustomMpdOptions();
            }
            /////////////////////////////////////////
        } catch (Exception e) {
            logger.warn("[MpdManager({})] (targetMpdPath={}) parseMpd.Exception", dashUnitId, targetMpdPath, e);
            return false;
        }

        return true;
    }

    public boolean validate() {
        if (mpd == null || mpdValidator == null) { return false; }

        try {
            mpdValidator.validate(mpd);
        } catch (ManifestValidationException e) {
            logger.warn("DashServer.validate.ManifestValidationException", e);
            logger.warn("{}", e.getViolations());
            return false;
        } catch (Exception e) {
            logger.warn("DashServer.validate.Exception", e);
            return false;
        }

        return true;
    }

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

    public void makeInitSegment(FileManager fileManager, String targetInitSegPath, byte[] content) {
        if (targetInitSegPath == null) {
            return;
        }

        logger.debug("[makeInitSegment] > [targetInitSegPath: {}]", targetInitSegPath);
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

    public String getAudioInitSegmentName() {
        List<Representation> representations = getRepresentations(MpdManager.CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String audioInitSegmentName = getRawInitializationSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_init1.m4s
            audioInitSegmentName = audioInitSegmentName.replace(
                    AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                    audioRepresentationId + ""
            );
            return audioInitSegmentName;
        }

        return null;
    }

    public String getAudioMediaSegmentName() {
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            String mediaSegmentName = getRawMediaSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getRepresentationIdFormat(),
                    audioRepresentationId + ""
            );
            // outdoor_market_ambiance_Dolby_chunk0_00001.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getChunkNumberFormat(),
                    String.format("%05d", getAudioSegmentSeqNum())
            );
            return mediaSegmentName;
        }

        return null;
    }

    public String getVideoInitSegmentName() {
        if (videoRepresentationId < 0) { return null; }

        List<Representation> representations = getRepresentations(MpdManager.CONTENT_VIDEO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String videoInitSegmentName = getRawInitializationSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_init0.m4s
            videoInitSegmentName = videoInitSegmentName.replace(
                    AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                    videoRepresentationId + ""
            );
            return videoInitSegmentName;
        }

        return null;
    }

    public String getVideoMediaSegmentName() {
        if (videoRepresentationId < 0) { return null; }

        List<Representation> representations = getRepresentations(CONTENT_VIDEO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            String mediaSegmentName = getRawMediaSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getRepresentationIdFormat(),
                    videoRepresentationId + ""
            );
            // outdoor_market_ambiance_Dolby_chunk0_00001.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getChunkNumberFormat(),
                    String.format("%05d", getVideoSegmentSeqNum())
            );
            return mediaSegmentName;
        }

        return null;
    }

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
        List<Representation> representations = getRepresentations(contentType);
        if (representations == null || representations.isEmpty()) { return; }
        Representation representation = representations.get(representationId);

        // 1-1) GET Media segment start number
        Long startNumber = getStartNumber(representation);
        if (startNumber != null) {
            if (contentType.equals(CONTENT_VIDEO_TYPE)) {
                setVideoSegmentSeqNum(startNumber);
            } else {
                setAudioSegmentSeqNum(startNumber);
            }
            logger.debug("[MpdManager({})] [{}] Media Segment's start number is [{}].", dashUnitId, contentType, startNumber);
        }

        // 1-2) SET Media segment duration & availabilityTimeOffset
        double segmentDurationSec = AppInstance.getInstance().getConfigManager().getSegmentDuration(); // seconds
        long segmentDurationMicroSec = (long) (segmentDurationSec * 1000000); // to micro-seconds;
        //logger.debug("curSegmentDuration: {}", (long) segmentDurationSec);

        SegmentTemplate newSegmentTemplate = representation.getSegmentTemplate().buildUpon()
                .withDuration(segmentDurationMicroSec)
                .withAvailabilityTimeOffset(segmentDurationSec - StreamConfigManager.AVAILABILITY_TIME_OFFSET_FACTOR)
                .build();
        representation = representation.buildUpon().withSegmentTemplate(newSegmentTemplate).build();
        logger.debug("[MpdManager({})] [{}] Representation > [duration: {}, ato: {}]",
                dashUnitId, contentType,
                representation.getSegmentTemplate().getDuration(),
                representation.getSegmentTemplate().getAvailabilityTimeOffset()
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
        List<Period> newPeriods = setCustomPeriodOptions(0, newAdaptationSets); // TODO : Set Period ID > 전달할 미디어 개수에 따라 변동됨
        ////////////////////////////////

        ////////////////////////////////
        // 4) SET MPD
        mpd = mpd.buildUpon().withPeriods(newPeriods).build();
        ////////////////////////////////
    }

    private List<AdaptationSet> setCustomAdaptationSetOptions(int adaptationSetIndex, List<Representation> representations) {
        if (adaptationSetIndex < 0 || representations == null || representations.isEmpty()) { return null; }

        List<AdaptationSet> adaptationSets =  mpd.getPeriods().get(0).getAdaptationSets();
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
        mpd = mpd.buildUpon()
                .withMediaPresentationDuration(Duration.ofSeconds(StreamConfigManager.MEDIA_PRESENTATION_DURATION))
                .withMinBufferTime(Duration.ofSeconds(StreamConfigManager.MIN_BUFFER_TIME))
                .withMaxSegmentDuration(Duration.ofSeconds((long) AppInstance.getInstance().getConfigManager().getSegmentDuration()))
                .build();
    }

    public String writeAsString() throws JsonProcessingException {
        return mpdParser.writeAsString(mpd);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getAudioSegmentSeqNum() {
        return audioSegmentSeqNum.get();
    }

    public void setAudioSegmentSeqNum(long number) {
        audioSegmentSeqNum.set(number);
    }

    public long incAndGetAudioSegmentSeqNum() {
        return audioSegmentSeqNum.incrementAndGet();
    }

    public long getVideoSegmentSeqNum() {
        return videoSegmentSeqNum.get();
    }

    public void setVideoSegmentSeqNum(long number) {
        videoSegmentSeqNum.set(number);
    }

    public long incAndGetVideoSegmentSeqNum() {
        return videoSegmentSeqNum.incrementAndGet();
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
