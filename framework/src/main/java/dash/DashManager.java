package dash;

import dash.component.AdaptationSet;
import dash.component.MPD;
import dash.component.Period;
import dash.component.Representation;
import dash.component.definition.MpdType;
import dash.component.segment.*;
import dash.component.segment.definition.InitializationSegment;
import dash.component.segment.definition.MediaSegment;
import dash.handler.HttpMessageManager;
import instance.BaseEnvironment;
import instance.DebugLevel;
import network.definition.NetAddress;
import network.socket.SocketManager;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import service.ResourceManager;
import service.scheduler.schedule.ScheduleManager;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;

public class DashManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashManager.class);

    private final BaseEnvironment baseEnvironment;
    private final SocketManager socketManager;
    private HttpMessageManager httpMessageManager;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashManager() {
        // 인스턴스 생성
        baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                new ResourceManager(5000, 7000),
                DebugLevel.DEBUG
        );

        // SocketManager 생성
        socketManager = new SocketManager(
                baseEnvironment,
                false, true,
                10, 500000, 500000
        );

        httpMessageManager = new HttpMessageManager(
                baseEnvironment.getScheduleManager(),
                socketManager
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void start() {
        httpMessageManager.start();
    }

    public void stop() {
        httpMessageManager.stop();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public HttpMessageManager getHttpMessageManager() {
        return httpMessageManager;
    }

    public void setHttpMessageManager(HttpMessageManager httpMessageManager) {
        this.httpMessageManager = httpMessageManager;
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

    /**
     * @fn public MPD parseXml(String filePath)
     * @brief MPD Parsing Function
     * @param filePath File absolute path
     * @return Media Presentation Description object
     */
    public MPD parseXml(String filePath) {
        if (filePath == null) { return null; }

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(filePath);
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to get the input stream. (filePath={})", filePath, e);
            return null;
        }

        //////////////////////////////
        // Media Presentation Description (MPD)
        MPD mpd = new MPD();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document manifest = documentBuilder.parse(inputStream);
            if (manifest == null) {
                logger.warn("[DashManager] Fail to get the MPD manifest (document). (filePath={})", filePath);
                return null;
            }
            mpd.setManifest(manifest);

            NodeList mpdNodeList = manifest.getElementsByTagName("MPD");
            if (mpdNodeList == null) {
                logger.warn("[DashManager] Fail to get the MPD node list. (filePath={})", filePath);
                return null;
            }

            if (mpdNodeList.getLength() != 1) {
                logger.warn("[DashManager] Duplicated MPD is defined. (filePath={}, mpdCount={})", filePath, mpdNodeList.getLength());
                return null;
            }

            Element mpdElement = (Element) mpdNodeList.item(0);

            String type = mpdElement.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                mpd.setMpdType(type.equals("static") ? MpdType.STATIC : MpdType.DYNAMIC);
                logger.debug("[DashManager] MPD Type: {}", type);
            }

            // MPD minBufferTime
            String minBufferTime = mpdElement.getAttribute("minBufferTime");
            if (minBufferTime != null && !minBufferTime.isEmpty()) {
                mpd.setMinBufferTime(parseTime(minBufferTime));
                logger.debug("[DashManager] MPD MinBufferTime: {} > {} sec", minBufferTime, mpd.getMinBufferTime());
            }

            // MPD duration
            String duration = mpdElement.getAttribute("duration");
            if (duration != null && !duration.isEmpty()) {
                mpd.setMediaPresentationDuration(parseTime(duration));
                logger.debug("[DashManager] MPD Duration: {} > {} sec", duration, mpd.getMediaPresentationDuration());
            }

            // MPD BaseURL
            NodeList baseUrlNodeList = mpdElement.getElementsByTagName("BaseURL");
            if (baseUrlNodeList != null) {
                Node baseUrlNode = baseUrlNodeList.item(0);
                if (baseUrlNode != null) {
                    if (baseUrlNode.getParentNode() == mpdElement) {
                        String url = baseUrlNode.getTextContent();
                        if (url != null && !url.isEmpty()) {
                            mpd.setBaseUrl(url);
                            logger.debug("[DashManager] MPD BaseUrl: {}", mpd.getBaseUrl());
                        }
                    }
                }
            }

            //////////////////////////////
            // PERIOD
            NodeList periodNodeList = mpdElement.getElementsByTagName("Period");
            if (periodNodeList == null) {
                logger.warn("[DashManager] MPD Period is not defined. (filePath={})", filePath);
                return null;
            }

            for (int periodIterator = 0; periodIterator < periodNodeList.getLength(); periodIterator++) {
                Element curPeriodElement = ((Element) periodNodeList.item(periodIterator));
                if (curPeriodElement == null) {
                    logger.debug("\t[DashManager] Period[{}] element is not exist.", periodIterator);
                    continue;
                }
                Period period = new Period(String.valueOf(periodIterator));

                // Period start
                String start = curPeriodElement.getAttribute("start");
                if (start != null && !start.isEmpty()) {
                    logger.debug("\t[DashManager] Period[{}] start: {} > {} sec", periodIterator, start, parseTime(start));
                }

                // Period duration
                duration = curPeriodElement.getAttribute("duration");
                if (duration != null && !duration.isEmpty()) {
                    logger.debug("\t[DashManager] Period[{}] duration: {} > {} sec", periodIterator, duration, parseTime(duration));
                }

                // Period BaseURL
                baseUrlNodeList = curPeriodElement.getElementsByTagName("BaseURL");
                if (baseUrlNodeList != null) {
                    Node baseUrlNode = baseUrlNodeList.item(0);
                    if (baseUrlNode != null) {
                        if (baseUrlNode.getParentNode() == curPeriodElement) {
                            String url = baseUrlNode.getTextContent();
                            if (url != null && !url.isEmpty()) {
                                period.setBaseUrl(url);
                                logger.debug("\t[DashManager] Period[{}] BaseURL: {}", periodIterator, period.getBaseUrl());
                            }
                        }
                    }
                } else {
                    logger.debug("\t[DashManager] Period[{}] BaseURL is not defined.", periodIterator);
                }

                //////////////////////////////
                // ADAPTATION SET
                NodeList adaptationSetNodeList = curPeriodElement.getElementsByTagName("AdaptationSet");
                if (adaptationSetNodeList == null || adaptationSetNodeList.getLength() == 0) {
                    logger.warn("[DashManager] Period[{}] AdaptationSet is not defined. (filePath={})", periodIterator, filePath);
                    continue;
                }

                for (int adapdationSetIterator = 0; adapdationSetIterator < adaptationSetNodeList.getLength(); adapdationSetIterator++) {
                    Element curAdaptationSetElement = ((Element) adaptationSetNodeList.item(adapdationSetIterator));
                    if (curAdaptationSetElement == null) {
                        logger.debug("\t\t[DashManager] AdaptationSet[{}] element is not exist.", periodIterator);
                        continue;
                    }
                    AdaptationSet adaptationSet = new AdaptationSet(String.valueOf(adapdationSetIterator));

                    // Adaptation mimeType
                    String mimeType = curAdaptationSetElement.getAttribute("mimeType");
                    if (mimeType != null && !mimeType.isEmpty()) {
                        adaptationSet.setMimeType(mimeType);
                        logger.debug("\t\t[DashManager] AdaptationSet[{}] mimeType: {}", adapdationSetIterator, adaptationSet.getMimeType());
                    }

                    // Adaptation bitstreamSwitching
                    String bitstreamSwitching = curAdaptationSetElement.getAttribute("bitstreamSwitching");
                    if (bitstreamSwitching != null && !bitstreamSwitching.isEmpty()) {
                        adaptationSet.setBitstreamSwitching(bitstreamSwitching.equals("true"));
                        logger.debug("\t\t[DashManager] AdaptationSet[{}] bitstreamSwitching: {}", adapdationSetIterator, adaptationSet.isBitstreamSwitching());
                    }

                    // Adaptation BaseURL
                    baseUrlNodeList = curAdaptationSetElement.getElementsByTagName("BaseURL");
                    if (baseUrlNodeList != null) {
                        Node baseUrlNode = baseUrlNodeList.item(0);
                        if (baseUrlNode != null) {
                            if (baseUrlNode.getParentNode() == curAdaptationSetElement) {
                                String url = baseUrlNode.getTextContent();
                                if (url != null && !url.isEmpty()) {
                                    period.setBaseUrl(url);
                                    logger.debug("\t\t[DashManager] AdaptationSet[{}] BaseURL: {}", adapdationSetIterator, period.getBaseUrl());
                                }
                            }
                        }
                    }

                    //////////////////////////////
                    // REPRESENTATION
                    NodeList representationNodeList = curAdaptationSetElement.getElementsByTagName("Representation");
                    if (representationNodeList == null || representationNodeList.getLength() == 0) {
                        logger.warn("\t\t[DashManager] AdaptationSet[{}] Representation is not defined. (filePath={})", adapdationSetIterator, filePath);
                        continue;
                    }

                    for (int representationIterator = 0; representationIterator < representationNodeList.getLength(); representationIterator++) {
                        Element curRepresentation = (Element) representationNodeList.item(representationIterator);
                        if (curRepresentation == null) {
                            logger.debug("\t\t\t[DashManager] Representation[{}] element is not exist.", periodIterator);
                            continue;
                        }

                        Representation representation;
                        SegmentInformation segmentInformation;

                        // Representation id
                        String id = curRepresentation.getAttribute("id");
                        if (id == null || id.isEmpty()) {
                            logger.warn("\t\t\t[DashManager] Representation[{}] id is not defined. (filePath={})", representationIterator, filePath);
                            continue;
                        } else {
                            representation = new Representation(id);
                            segmentInformation = new SegmentInformation();
                            representation.setSegmentInformation(segmentInformation);
                            logger.debug("\t\t\t[DashManager] Representation[{}] id: {}", representationIterator, id);
                        }

                        // Representation BaseURL
                        baseUrlNodeList = curRepresentation.getElementsByTagName("BaseURL");
                        if (baseUrlNodeList != null) {
                            Node baseUrlNode = baseUrlNodeList.item(0);
                            if (baseUrlNode != null) {
                                if (baseUrlNode.getParentNode() == curRepresentation) {
                                    String url = baseUrlNode.getTextContent();
                                    if (url != null && !url.isEmpty()) {
                                        period.setBaseUrl(url);
                                        logger.debug("\t\t\t[DashManager] Representation[{}] BaseURL: {}", representationIterator, period.getBaseUrl());
                                    }
                                }
                            }
                        }

                        // Representation codecs
                        String codecs = curRepresentation.getAttribute("codecs");
                        if (codecs != null && !codecs.isEmpty()) {
                            representation.setCodecs(codecs);
                            logger.debug("\t\t\t[DashManager] Representation[{}] codecs: {}", representationIterator, representation.getCodecs());
                        }

                        // Representation mimeType
                        mimeType = curRepresentation.getAttribute("mimeType");
                        if (mimeType != null && !mimeType.isEmpty()) {
                            representation.setMimeType(mimeType);
                            logger.debug("\t\t\t[DashManager] Representation[{}] mimeType: {}", representationIterator, representation.getMimeType());
                        }

                        // Representation width
                        String width = curRepresentation.getAttribute("width");
                        if (width != null && !width.isEmpty()) {
                            representation.setWidth(width);
                            logger.debug("\t\t\t[DashManager] Representation[{}] width: {}", representationIterator, representation.getWidth());
                        }

                        // Representation height
                        String height = curRepresentation.getAttribute("height");
                        if (height != null && !height.isEmpty()) {
                            representation.setHeight(height);
                            logger.debug("\t\t\t[DashManager] Representation[{}] height: {}", representationIterator, representation.getHeight());
                        }

                        // Representation startWithSAP
                        String startWitSAP = curRepresentation.getAttribute("startWithSAP");
                        if (startWitSAP != null && !startWitSAP.isEmpty()) {
                            representation.setStartWithSAP(startWitSAP);
                            logger.debug("\t\t\t[DashManager] Representation[{}] startWitSAP: {}", representationIterator, representation.getStartWithSAP());
                        }

                        // Representation bandwidth
                        String bandWidth = curRepresentation.getAttribute("bandwidth");
                        if (bandWidth != null && !bandWidth.isEmpty()) {
                            representation.setBandwidth(bandWidth);
                            logger.debug("\t\t\t[DashManager] Representation[{}] bandWidth: {}", representationIterator, representation.getBandwidth());
                        }

                        //////////////////////////////
                        // SegmentBase
                        NodeList segmentBaseNodeList = curRepresentation.getElementsByTagName("SegmentBase");
                        if (segmentBaseNodeList != null && segmentBaseNodeList.getLength() > 0) {
                            Element segmentBaseElement = (Element) segmentBaseNodeList.item(0);
                            if (segmentBaseElement != null) {
                                SegmentBase segmentBase = new SegmentBase();

                                // SegmentBase indexRange
                                String indexRange = segmentBaseElement.getAttribute("indexRange");
                                if (indexRange != null && !indexRange.isEmpty()) {
                                    segmentBase.setIndexRange(indexRange);
                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentBase indexRange: {}", representationIterator, segmentBase.getIndexRange());
                                }

                                // SegmentBase Initialization
                                NodeList initializationList = segmentBaseElement.getElementsByTagName("Initialization");
                                if (initializationList != null) {
                                    Node initializationNode = initializationList.item(0);
                                    if (initializationNode != null) {
                                        if (initializationNode.getParentNode() == segmentBaseElement) {
                                            String sourceUrl = ((Element) initializationNode).getAttribute("sourceURL");
                                            if (sourceUrl != null && !sourceUrl.isEmpty()) {
                                                InitializationSegment initializationSegment = new InitializationSegment(representation.getIndex(), sourceUrl);

                                                segmentBase.setInitializationSegment(initializationSegment);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentBase InitializationSegment sourceURL: {}", representationIterator, segmentBase.getInitializationSegment().getUrl());
                                            }
                                        }
                                    }
                                }

                                // SegmentBase RepresentationIndex
                                NodeList representationIndexList = segmentBaseElement.getElementsByTagName("RepresentationIndex");
                                if (representationIndexList != null) {
                                    Node representationIndexNode = representationIndexList.item(0);
                                    if (representationIndexNode != null) {
                                        if (representationIndexNode.getParentNode() == segmentBaseElement) {
                                            String sourceUrl = ((Element) representationIndexNode).getAttribute("sourceURL");
                                            if (sourceUrl != null && !sourceUrl.isEmpty()) {
                                                RepresentationIndex representationIndex = new RepresentationIndex();
                                                representationIndex.setSourceUrl(sourceUrl);

                                                segmentBase.setRepresentationIndex(representationIndex);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentBase RepresentationIndex sourceURL: {}", representationIterator, segmentBase.getRepresentationIndex().getSourceUrl());
                                            }
                                        }
                                    }
                                }

                                segmentInformation.setSegmentBase(segmentBase);
                                logger.debug("\t\t\t\t----");
                            }
                        }
                        // SegmentBase END
                        //////////////////////////////

                        //////////////////////////////
                        // SegmentList
                        NodeList segmentNodeList = curRepresentation.getElementsByTagName("SegmentList");
                        if (segmentNodeList != null) {
                            Element segmentListElement = (Element) segmentNodeList.item(0);
                            if (segmentListElement != null) {
                                SegmentList segmentList = new SegmentList();

                                // SegmentList timescale
                                String timeScale = segmentListElement.getAttribute("timescale");
                                if (timeScale != null && !timeScale.isEmpty()) {
                                    segmentList.setTimeScale(timeScale);
                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList timescale: {}", representationIterator, segmentList.getTimeScale());
                                }

                                // SegmentList duration
                                duration = segmentListElement.getAttribute("duration");
                                if (duration != null && !duration.isEmpty()) {
                                    segmentList.setDuration(duration);
                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList duration: {}", representationIterator, segmentList.getDuration());
                                }

                                // SegmentList Initialization
                                NodeList initializationList = segmentListElement.getElementsByTagName("Initialization");
                                if (initializationList != null) {
                                    Node initializationNode = initializationList.item(0);
                                    if (initializationNode != null) {
                                        if (initializationNode.getParentNode() == segmentListElement) {
                                            String sourceUrl = ((Element) initializationNode).getAttribute("sourceURL");
                                            if (sourceUrl != null && !sourceUrl.isEmpty()) {
                                                InitializationSegment initializationSegment = new InitializationSegment(representation.getIndex(), sourceUrl);

                                                segmentList.setInitializationSegment(initializationSegment);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList InitializationSegment sourceURL: {}", representationIterator, segmentList.getInitializationSegment().getUrl());
                                            }
                                        }
                                    }
                                }

                                // SegmentList RepresentationIndex
                                NodeList representationIndexList = segmentListElement.getElementsByTagName("RepresentationIndex");
                                if (representationIndexList != null) {
                                    Node representationIndexNode = representationIndexList.item(0);
                                    if (representationIndexNode != null) {
                                        if (representationIndexNode.getParentNode() == segmentListElement) {
                                            String sourceUrl = ((Element) representationIndexNode).getAttribute("sourceURL");
                                            if (sourceUrl != null && !sourceUrl.isEmpty()) {
                                                RepresentationIndex representationIndex = new RepresentationIndex();
                                                representationIndex.setSourceUrl(sourceUrl);

                                                segmentList.setRepresentationIndex(representationIndex);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList RepresentationIndex sourceURL: {}", representationIterator, segmentList.getRepresentationIndex().getSourceUrl());
                                            }
                                        }
                                    }
                                }

                                // SegmentList SegmentURL
                                NodeList segmentURLNodeList = segmentListElement.getElementsByTagName("SegmentURL");
                                if (segmentURLNodeList != null) {
                                    for (int segmentURLNodeIterator = 0; segmentURLNodeIterator < segmentURLNodeList.getLength(); segmentURLNodeIterator++) {
                                        Node curSegmentUrlNode = segmentURLNodeList.item(segmentURLNodeIterator);
                                        if (curSegmentUrlNode == null) {
                                            logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList SegmentURL[{}] is not defined.", representationIterator, segmentURLNodeIterator);
                                            continue;
                                        }

                                        if (curSegmentUrlNode.getParentNode() == segmentListElement) {
                                            String media = ((Element) curSegmentUrlNode).getAttribute("media");
                                            if (media != null && !media.isEmpty()) {
                                                MediaSegment mediaSegment = new MediaSegment(
                                                        String.valueOf(segmentURLNodeIterator),
                                                        media
                                                );
                                                segmentList.addSegmentLast(mediaSegment);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentList SegmentURL[{}] media: {}",
                                                        representationIterator, segmentURLNodeIterator, segmentList.getSegmentByIndex(String.valueOf(segmentURLNodeIterator)).getUrl()
                                                );
                                            }
                                        }
                                    }
                                }

                                segmentInformation.setSegmentList(segmentList);
                                logger.debug("\t\t\t\t----");
                            }
                        }
                        // SegmentList END
                        //////////////////////////////

                        //////////////////////////////
                        // SegmentTemplate
                        NodeList segmentTemplateNodeList = curRepresentation.getElementsByTagName("SegmentTemplate");
                        if (segmentTemplateNodeList != null) {
                            Element segmentTemplateNodeElement = (Element) segmentTemplateNodeList.item(0);
                            if (segmentTemplateNodeElement != null) {
                                SegmentTemplate segmentTemplate = new SegmentTemplate();

                                // SegmentTemplate RepresentationIndex
                                NodeList representationIndexList = segmentTemplateNodeElement.getElementsByTagName("RepresentationIndex");
                                if (representationIndexList != null) {
                                    Node representationIndexNode = representationIndexList.item(0);
                                    if (representationIndexNode != null) {
                                        if (representationIndexNode.getParentNode() == segmentTemplateNodeElement) {
                                            String sourceUrl = ((Element) representationIndexNode).getAttribute("sourceURL");
                                            if (sourceUrl != null && !sourceUrl.isEmpty()) {
                                                RepresentationIndex representationIndex = new RepresentationIndex();
                                                representationIndex.setSourceUrl(sourceUrl);

                                                segmentTemplate.setRepresentationIndex(representationIndex);
                                                logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentTemplate RepresentationIndex sourceURL: {}", representationIterator, segmentTemplate.getRepresentationIndex().getSourceUrl());
                                            }
                                        }
                                    }
                                }

                                // SegmentTemplate SegmentTimeline
                                NodeList segmentTimelineList = segmentTemplateNodeElement.getElementsByTagName("SegmentTimeline");
                                if (segmentTimelineList != null) {
                                    Node segmentTimelineNode = segmentTimelineList.item(0);
                                    if (segmentTimelineNode != null) {
                                        NodeList sList = segmentTemplateNodeElement.getElementsByTagName("S");
                                        if (sList != null) {
                                            Node sNode = sList.item(0);
                                            if (sNode != null) {
                                                SegmentTimeline segmentTimeline = new SegmentTimeline();

                                                String t = ((Element) sNode).getAttribute("t");
                                                if (t != null && !t.isEmpty()) {
                                                    segmentTimeline.setT(t);
                                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentTemplate SegmentTimeline t: {}",
                                                            representationIterator, segmentTimeline.getT()
                                                    );
                                                }

                                                String d = ((Element) sNode).getAttribute("d");
                                                if (d != null && !d.isEmpty()) {
                                                    segmentTimeline.setD(d);
                                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentTemplate SegmentTimeline d: {}",
                                                            representationIterator, segmentTimeline.getD()
                                                    );
                                                }

                                                String r = ((Element) sNode).getAttribute("r");
                                                if (r != null && !r.isEmpty()) {
                                                    segmentTimeline.setR(r);
                                                    logger.debug("\t\t\t\t[DashManager] Representation[{}] SegmentTemplate SegmentTimeline r: {}",
                                                            representationIterator, segmentTimeline.getR()
                                                    );
                                                }

                                                segmentTemplate.setSegmentTimeline(segmentTimeline);
                                            }
                                        }
                                    }
                                }

                                segmentInformation.setSegmentTemplate(segmentTemplate);
                                logger.debug("\t\t\t\t----");
                            }
                        }
                        // SegmentTemplate END
                        //////////////////////////////

                        adaptationSet.addRepresentationLast(representation);
                        logger.debug("\t\t\t----");
                    }
                    // Representation END
                    //////////////////////////////

                    period.addAdaptationSetLast(adaptationSet);
                    logger.debug("\t\t----");
                }
                // AdaptationSet END
                //////////////////////////////
                logger.debug("\t----");

                mpd.addPeriodLast(period);
            }
            // Period END
            //////////////////////////////
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to parse the mpd xml. (filePath={})", filePath, e);
            return null;
        }
        // MPD END
        //////////////////////////////

        return mpd;
    }
    ////////////////////////////////////////////////////////////

}
