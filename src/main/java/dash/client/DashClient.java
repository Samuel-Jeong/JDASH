package dash.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.DashHttpMessageSender;
import instance.BaseEnvironment;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.MPDParser;
import tool.parser.mpd.AdaptationSet;
import tool.parser.mpd.MPD;
import tool.parser.mpd.Period;
import tool.parser.mpd.Representation;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [DASH Client] : [Remote Dash Unit] = 1 : 1
 */
public class DashClient {

    ////////////////////////////////////////////////////////////
    transient private static final Logger logger = LoggerFactory.getLogger(DashClient.class);

    transient public static final String CONTENT_AUDIO_TYPE = "audio";
    transient public static final String CONTENT_VIDEO_TYPE = "video";
    transient public static final String REPRESENTATION_ID_POSTFIX = "$RepresentationID$";
    transient public static final String NUMBER_POSTFIX = "$Number%05d$";

    private boolean isStopped = false;
    private MPD mpd = null;
    private final AtomicLong audioSegmentSeqNum = new AtomicLong(0);
    //private final AtomicInteger videoSegmentSeqNum = new AtomicInteger(0);

    private final String dashUnitId;
    transient private final MPDParser mpdParser;
    private final String srcPath;
    private final String srcBasePath;
    private final String uriFileName;
    private final String targetBasePath;
    private final String targetMpdPath;
    private String targetAudioInitSegPath;

    transient private final DashClientFsmManager dashClientFsmManager = new DashClientFsmManager();
    private final String dashClientStateUnitId;

    transient private final DashHttpMessageSender dashHttpMessageSender;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClient(String dashUnitId, BaseEnvironment baseEnvironment, String srcPath, String targetBasePath) {
        this.dashUnitId = dashUnitId;
        this.dashClientStateUnitId = "DASH_CLIENT_STATE:" + dashUnitId;

        this.mpdParser = new MPDParser();
        this.srcPath = srcPath;
        this.srcBasePath = FileManager.getParentPathFromUri(srcPath);
        this.uriFileName = FileManager.getFileNameFromUri(srcPath);
        this.targetBasePath = FileManager.concatFilePath(targetBasePath, uriFileName);
        this.targetMpdPath = FileManager.concatFilePath(this.targetBasePath, uriFileName + ".mpd");

        this.dashHttpMessageSender = new DashHttpMessageSender(baseEnvironment, false); // SSL 아직 미지원

        logger.debug("[DashClient({})] Created. (dashClientStateUnitId={}, srcPath={}, uriFileName={}, targetBasePath={}, targetMpdPath={})",
                this.dashUnitId, this.dashClientStateUnitId,
                this.srcPath, this.uriFileName,
                this.targetBasePath, this.targetMpdPath
        );
    }

    public void start() {
        //////////////////////////////
        // SETTING : FSM
        this.dashClientFsmManager.init(this);
        this.dashClientFsmManager.getStateManager().addStateUnit(
                dashClientStateUnitId,
                this.dashClientFsmManager.getStateManager().getStateHandler(DashClientState.NAME).getName(),
                DashClientState.IDLE,
                null
        );
        StateUnit dashClientStateUnit = this.dashClientFsmManager.getStateManager().getStateUnit(dashClientStateUnitId);
        dashClientStateUnit.setData(this);
        //////////////////////////////

        //////////////////////////////
        // SETTING : HTTP
        this.dashHttpMessageSender.start(this);
        //////////////////////////////

        //////////////////////////////
        // SETTING : TARGET PATH
        if (FileManager.isExist(targetBasePath)) {
            FileManager.deleteFile(targetBasePath);
        }
        FileManager.mkdirs(targetBasePath);
        makeMpd("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        //////////////////////////////
    }

    public void stop() {
        this.dashClientFsmManager.getStateManager().removeStateUnit(dashClientStateUnitId);
        this.dashHttpMessageSender.stop();
        isStopped = true;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void sendHttpGetRequest(String path) {
        HttpRequest httpRequest = dashHttpMessageSender.makeHttpGetRequestMessage(path);
        if (httpRequest == null) {
            logger.warn("[DashClient({})] Fail to send the http request. (path={})", dashUnitId, path);
            return;
        } else {
            logger.debug("[DashClient({})] [SEND] Request=\n{}", dashUnitId, httpRequest);
        }

        dashHttpMessageSender.sendMessage(httpRequest);
    }

    public void makeMpd(String content) {
        FileManager.writeString(
                targetMpdPath,
                content,
                true
        );
    }

    public void parseMpd() {
        try {
            mpd = mpdParser.parse(new FileInputStream(targetMpdPath));
            if (mpd == null) {
                logger.warn("[DashClient({})] Fail to parse the mpd. (path={})", dashUnitId, targetMpdPath);
            } else {
                logger.debug("[DashClient({})] Success to parse the mpd. (path={}, mpd=\n{})", dashUnitId, targetMpdPath, mpd);
            }

            List<Representation> representations = getRepresentations(DashClient.CONTENT_AUDIO_TYPE);
            if (representations != null && !representations.isEmpty()) {
                Long startNumber = getStartNumber(representations.get(0));
                if (startNumber != null) {
                    setAudioSegmentSeqNum(startNumber);
                    logger.debug("[DashClient({})] [AUDIO] Media Segment's start number is [{}].", dashUnitId, startNumber);
                }
            }
        } catch (Exception e) {
            logger.warn("[DashClient({})] (targetMpdPath={}) parseMpd.Exception", dashUnitId, targetMpdPath, e);
        }
    }

    public void makeInitSegment(String targetInitSegPath, String content) {
        if (targetInitSegPath == null) { return; }

        FileManager.writeString(
                targetInitSegPath,
                content,
                true
        );
    }

    public void makeMediaSegment(String targetMediaSegPath, String content) {
        if (targetMediaSegPath == null) { return; }

        FileManager.writeString(
                targetMediaSegPath,
                content,
                true
        );
    }

    public String getMediaSegmentName() {
        List<Representation> representations = getRepresentations(DashClient.CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            String mediaSegmentName = getRawMediaSegmentName(representations.get(0));
            // outdoor_market_ambiance_Dolby_chunk$RepresentationID$_$Number%05d$.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    DashClient.REPRESENTATION_ID_POSTFIX,
                    0 + ""
            );
            // outdoor_market_ambiance_Dolby_chunk0_00001.m4s
            mediaSegmentName = mediaSegmentName.replace(
                    DashClient.NUMBER_POSTFIX,
                    String.format("%05d", getAudioSegmentSeqNum())
            );
            return mediaSegmentName;
        }

        return null;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean isStopped() {
        return isStopped;
    }

    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }

    public DashClientFsmManager getDashClientFsmManager() {
        return dashClientFsmManager;
    }

    public String getDashClientStateUnitId() {
        return dashClientStateUnitId;
    }

    public String getDashUnitId() {
        return dashUnitId;
    }

    public String getSrcPath() {
        return srcPath;
    }

    public String getSrcBasePath() {
        return srcBasePath;
    }

    public String getUriFileName() {
        return uriFileName;
    }

    public String getTargetBasePath() {
        return targetBasePath;
    }

    public String getTargetMpdPath() {
        return targetMpdPath;
    }

    public MPD getMpd() {
        return mpd;
    }

    public void setMpd(MPD mpd) {
        this.mpd = mpd;
    }

    public String getTargetAudioInitSegPath() {
        return targetAudioInitSegPath;
    }

    public void setTargetAudioInitSegPath(String targetAudioInitSegPath) {
        this.targetAudioInitSegPath = targetAudioInitSegPath;
    }

    public long getAudioSegmentSeqNum() {
        return audioSegmentSeqNum.get();
    }

    public void setAudioSegmentSeqNum(long number) {
        audioSegmentSeqNum.set(number);
    }

    public long incAndGetAudioSegmentSeqNum() {
        return audioSegmentSeqNum.incrementAndGet();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getMediaPresentationTimeAsSec() {
        return mpd.getMediaPresentationDuration().getSeconds();
    }

    public List<Long> getBitRates(List<Representation> representations) {
        List<Long> bitRates = new ArrayList<>();

        try {
            for (Representation representation : representations) {
                bitRates.add(representation.getBandwidth());
            }
        } catch (Exception e) {
            logger.warn("[DashClient({})] getBitRates.Exception", dashUnitId, e);
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
        return representation.getSegmentTemplate().getDuration();
    }

    public List<Representation> getRepresentations(String contentType) {
        if (mpd == null) { return null; }

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
    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
