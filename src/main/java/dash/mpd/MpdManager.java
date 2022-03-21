package dash.mpd;

import com.fasterxml.jackson.core.JsonProcessingException;
import config.ConfigManager;
import dash.mpd.parser.MPDParser;
import dash.mpd.parser.mpd.AdaptationSet;
import dash.mpd.parser.mpd.MPD;
import dash.mpd.parser.mpd.Period;
import dash.mpd.parser.mpd.Representation;
import dash.mpd.validator.MPDValidator;
import dash.mpd.validator.ManifestValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.module.FileManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private final AtomicLong audioSegmentSeqNum = new AtomicLong(0);
    //private final AtomicInteger videoSegmentSeqNum = new AtomicInteger(0);
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MpdManager(String dashUnitId) {
        this.dashUnitId = dashUnitId;

        this.mpdParser = new MPDParser();
        try {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            mpdValidator = new MPDValidator(mpdParser, configManager.getValidationXsdPath());
        } catch (Exception e) {
            logger.warn("[DashServer] Fail to make a mpd validator.");
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
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(targetMpdPath);
            } catch (Exception e) {
                logger.warn("[MpdManager] Fail to get the input stream. (filePath={})", targetMpdPath, e);
                return false;
            }

            mpd = mpdParser.parse(inputStream);
            if (mpd == null) {
                logger.warn("[MpdManager({})] Fail to parse the mpd. (path={})", dashUnitId, targetMpdPath);
            } else {
                logger.debug("[MpdManager({})] Success to parse the mpd. (path={}, mpd=\n{})", dashUnitId, targetMpdPath, mpd);
            }

            List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
            if (representations != null && !representations.isEmpty()) {
                Long startNumber = getStartNumber(representations.get(0));
                if (startNumber != null) {
                    setAudioSegmentSeqNum(startNumber);
                    logger.debug("[MpdManager({})] [AUDIO] Media Segment's start number is [{}].", dashUnitId, startNumber);
                }
            }
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

    public void makeMpd(String targetMpdPath, byte[] content) {
        FileManager.writeBytes(
                targetMpdPath,
                content,
                true
        );
    }

    public void makeInitSegment(String targetInitSegPath, byte[] content) {
        if (targetInitSegPath == null) {
            return;
        }

        FileManager.writeBytes(
                targetInitSegPath,
                content,
                true
        );
    }

    public void makeMediaSegment(String targetMediaSegPath, byte[] content) {
        if (targetMediaSegPath == null) {
            return;
        }

        FileManager.writeBytes(
                targetMediaSegPath,
                content,
                true
        );
    }

    public String getMediaSegmentName() {
        List<Representation> representations = getRepresentations(CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            String mediaSegmentName = getRawMediaSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    configManager.getRepresentationIdFormat(),
                    0 + ""
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

    public MPD getMpd() {
        return mpd;
    }

    public void setMpd(MPD mpd) {
        this.mpd = mpd;
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
