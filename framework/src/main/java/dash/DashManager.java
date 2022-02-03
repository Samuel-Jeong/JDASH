package dash;

import dash.component.AdaptationSet;
import dash.component.MPD;
import dash.component.Period;
import dash.component.Representation;
import dash.component.definition.MpdType;
import javafx.beans.WeakInvalidationListener;
import jdk.internal.org.xml.sax.SAXException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DashManager {

    private static final Logger logger = LoggerFactory.getLogger(DashManager.class);

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

    // TODO
    public MPD parseXml(String filePath) {
        if (filePath == null) { return null; }

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(filePath);
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to get the input stream. (filePath={})", filePath, e);
            return null;
        }

        MPD mpd = new MPD();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document manifest = documentBuilder.parse(inputStream);
            NodeList mpdList = manifest.getElementsByTagName("MPD");
            if (mpdList == null) {
                logger.warn("[DashManager] Fail to get the MPD node list. (filePath={})", filePath);
                return null;
            }

            if (mpdList.getLength() != 1) {
                logger.warn("[DashManager] Duplicated MPD is defined. (filePath={}, mpdCount={})", filePath, mpdList.getLength());
                return null;
            }

            String type = ((Element) mpdList.item(0)).getAttribute("type");
            if (type != null && !type.isEmpty()) {
                mpd.setMpdType(type.equals("static") ? MpdType.STATIC : MpdType.DYNAMIC);
                logger.debug("[DashManager] MPD Type: {}", type);
            } else {
                logger.warn("[DashManager] MPD type is not defined. (filePath={})", filePath);
                return null;
            }

            String minBufferTime = ((Element) mpdList.item(0)).getAttribute("mediaPresentationDuration");
            if (minBufferTime != null && !minBufferTime.isEmpty()) {
                mpd.setMinBufferTime(parseTime(minBufferTime));
                logger.debug("[DashManager] MPD MinBufferTime: {} > {} sec", minBufferTime, mpd.getMinBufferTime());
            }

            String duration = ((Element) mpdList.item(0)).getAttribute("mediaPresentationDuration");
            if (duration != null && !duration.isEmpty()) {
                mpd.setMediaPresentationDuration(parseTime(duration));
                logger.debug("[DashManager] MPD Duration: {} > {} sec", duration, mpd.getMediaPresentationDuration());
            } else {
                logger.warn("[DashManager] MPD Duration is not defined. (filePath={})", filePath);
                return null;
            }

            NodeList baseUrlList = ((Element) mpdList.item(0)).getElementsByTagName("BaseURL");
            if (baseUrlList != null) {
                List<String> baseUrls = new ArrayList<>();
                for (int i = 0; i < baseUrlList.getLength(); i++) {
                    String url = baseUrlList.item(i).getTextContent();
                    if (url != null && !url.isEmpty()) {
                        baseUrls.add(url);
                    }
                }
                mpd.setBaseUrlList(baseUrls);
                logger.debug("[DashManager] MPD BaseUrl: {}", baseUrls);
            } else {
                logger.warn("[DashManager] MPD BaseUrl is not defined. (filePath={})", filePath);
            }

            NodeList periodList = ((Element) mpdList.item(0)).getElementsByTagName("Period");
            if (periodList == null) {
                logger.warn("[DashManager] MPD Period is not defined. (filePath={})", filePath);
                return null;
            }

            //////////////////////////////
            // PERIOD
            for (int periodIndex = 0; periodIndex < periodList.getLength(); periodIndex++) {
                Period period = new Period(String.valueOf(periodIndex));

                String start = ((Element) periodList.item(periodIndex)).getAttribute("start");
                if (start != null && !start.isEmpty()) {
                    logger.debug("\t[DashManager] Period[{}] start: {} > {} sec", periodIndex, start, parseTime(start));
                }

                //////////////////////////////
                // ADAPTATION SET
                NodeList adaptationList = ((Element) mpdList.item(0)).getElementsByTagName("AdaptationSet");
                if (adaptationList == null || adaptationList.getLength() == 0) {
                    logger.warn("[DashManager] Period[{}] AdaptationSet is not defined. (filePath={})", periodIndex, filePath);
                    continue;
                }

                for (int adapdationSetIndex = 0; adapdationSetIndex < adaptationList.getLength(); adapdationSetIndex++) {
                    AdaptationSet adaptationSet = new AdaptationSet(String.valueOf(adapdationSetIndex));

                    String mimeType = ((Element) adaptationList.item(adapdationSetIndex)).getAttribute("mimeType");
                    if (mimeType != null && !mimeType.isEmpty()) {
                        adaptationSet.setMimeType(mimeType);
                        logger.debug("\t\t[DashManager] AdaptationSet[{}] mimeType: {}", adapdationSetIndex, adaptationSet.getMimeType());
                    }

                    String bitstreamSwitching = ((Element) adaptationList.item(adapdationSetIndex)).getAttribute("bitstreamSwitching");
                    if (bitstreamSwitching != null && !bitstreamSwitching.isEmpty()) {
                        adaptationSet.setBitstreamSwitching(bitstreamSwitching.equals("true"));
                        logger.debug("\t\t[DashManager] AdaptationSet[{}] bitstreamSwitching: {}", adapdationSetIndex, adaptationSet.isBitstreamSwitching());
                    }

                    //////////////////////////////
                    // REPRESENTATION
                    NodeList representationList = ((Element) adaptationList.item(adapdationSetIndex)).getElementsByTagName("Representation");
                    if (representationList == null || representationList.getLength() == 0) {
                        logger.warn("\t\t[DashManager] AdaptationSet[{}] Representation is not defined. (filePath={})", adapdationSetIndex, filePath);
                        continue;
                    }

                    for (int representationIndex = 0; representationIndex < representationList.getLength(); representationIndex++) {
                        Element curRepresentation = (Element) representationList.item(representationIndex);
                        if (curRepresentation == null) { continue; }

                        Representation representation;
                        String id = curRepresentation.getAttribute("id");
                        if (id == null || id.isEmpty()) {
                            logger.warn("\t\t\t[DashManager] Representation[{}] id is not defined. (filePath={})", representationIndex, filePath);
                            continue;
                        } else {
                            representation = new Representation(id);
                            logger.debug("\t\t\t[DashManager] Representation[{}] id: {}", representationIndex, id);
                        }

                        String codecs = curRepresentation.getAttribute("codecs");
                        if (codecs != null && !codecs.isEmpty()) {
                            representation.setCodecs(codecs);
                            logger.debug("\t\t\t[DashManager] Representation[{}] codecs: {}", representationIndex, representation.getCodecs());
                        }

                        mimeType = curRepresentation.getAttribute("mimeType");
                        if (mimeType != null && !mimeType.isEmpty()) {
                            representation.setMimeType(mimeType);
                            logger.debug("\t\t\t[DashManager] Representation[{}] mimeType: {}", representationIndex, representation.getMimeType());
                        }

                        String width = curRepresentation.getAttribute("width");
                        if (width != null && !width.isEmpty()) {
                            representation.setWidth(width);
                            logger.debug("\t\t\t[DashManager] Representation[{}] width: {}", representationIndex, representation.getWidth());
                        }

                        String height = curRepresentation.getAttribute("height");
                        if (height != null && !height.isEmpty()) {
                            representation.setHeight(height);
                            logger.debug("\t\t\t[DashManager] Representation[{}] height: {}", representationIndex, representation.getHeight());
                        }

                        String startWitSAP = curRepresentation.getAttribute("startWithSAP");
                        if (startWitSAP != null && !startWitSAP.isEmpty()) {
                            representation.setStartWithSAP(startWitSAP);
                            logger.debug("\t\t\t[DashManager] Representation[{}] startWitSAP: {}", representationIndex, representation.getStartWithSAP());
                        }

                        String bandWidth = curRepresentation.getAttribute("bandwidth");
                        if (bandWidth != null && !bandWidth.isEmpty()) {
                            representation.setBandwidth(bandWidth);
                            logger.debug("\t\t\t[DashManager] Representation[{}] bandWidth: {}", representationIndex, representation.getBandwidth());
                        }

                        logger.debug("\t\t\t----");
                    }
                    //////////////////////////////

                    period.addLast(adaptationSet);
                    logger.debug("\t\t----");
                }
                //////////////////////////////
                logger.debug("\t----");
            }
            //////////////////////////////
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to parse the mpd xml. (filePath={})", filePath, e);
            return null;
        }

        return mpd;
    }

}
