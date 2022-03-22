package dash.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.DashHttpMessageSender;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import instance.BaseEnvironment;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.StreamConfigManager;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [DASH Client] : [Remote Dash Unit] = 1 : 1
 */
public class DashClient {

    ////////////////////////////////////////////////////////////
    transient private static final Logger logger = LoggerFactory.getLogger(DashClient.class);

    private boolean isStopped = false;

    private final String dashUnitId;
    private final String srcPath;
    private final String srcBasePath;
    private final String uriFileName;
    private final String targetBasePath;
    private final String targetMpdPath;

    private String targetAudioInitSegPath;
    private String targetVideoInitSegPath;

    private AtomicInteger audioRetryCount = new AtomicInteger(0);
    private AtomicInteger videoRetryCount = new AtomicInteger(0);

    transient private final DashClientFsmManager dashClientFsmManager = new DashClientFsmManager();
    private final String dashClientStateUnitId;

    transient private final DashHttpMessageSender dashHttpMessageSender;

    transient private final MpdManager mpdManager;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClient(String dashUnitId, BaseEnvironment baseEnvironment, String srcPath, String targetBasePath) {
        this.dashUnitId = dashUnitId;
        this.dashClientStateUnitId = "DASH_CLIENT_STATE:" + dashUnitId;

        this.srcPath = srcPath;
        this.srcBasePath = FileManager.getParentPathFromUri(srcPath);
        this.uriFileName = FileManager.getFileNameFromUri(srcPath);
        this.targetBasePath = targetBasePath;
        this.targetMpdPath = FileManager.concatFilePath(this.targetBasePath, uriFileName + StreamConfigManager.DASH_POSTFIX);

        this.dashHttpMessageSender = new DashHttpMessageSender(dashUnitId, baseEnvironment, false); // SSL 아직 미지원
        this.mpdManager = new MpdManager(dashUnitId);

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
        if (!FileManager.isExist(targetBasePath)) {
            //FileManager.deleteFile(targetBasePath);
            FileManager.mkdirs(targetBasePath);
            mpdManager.makeMpd(
                    targetMpdPath,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes()
            );
        }
        //////////////////////////////

        logger.debug("[DashClient({})] START", dashUnitId);
    }

    public void stop() {
        this.dashClientFsmManager.getStateManager().removeStateUnit(dashClientStateUnitId);
        this.dashHttpMessageSender.stop();

        isStopped = true;
        logger.debug("[DashClient({})] STOP", dashUnitId);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void sendHttpGetRequest(String path, MessageType messageType) {
        HttpRequest httpRequest = dashHttpMessageSender.makeHttpGetRequestMessage(path);
        if (httpRequest == null) {
            logger.warn("[DashClient({})] Fail to send the http request. (path={})", dashUnitId, path);
            return;
        } else {
            logger.trace("[DashClient({})] [SEND] Request=\n{}", dashUnitId, httpRequest);
        }

        switch (messageType) {
            case MPD:
                dashHttpMessageSender.sendMessageForMpd(httpRequest);
                break;
            case VIDEO:
                dashHttpMessageSender.sendMessageForVideo(httpRequest);
                break;
            case AUDIO:
                dashHttpMessageSender.sendMessageForAudio(httpRequest);
                break;
            default:
                break;
        }
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

    public String getTargetAudioInitSegPath() {
        return targetAudioInitSegPath;
    }

    public void setTargetAudioInitSegPath(String targetAudioInitSegPath) {
        this.targetAudioInitSegPath = targetAudioInitSegPath;
    }

    public String getTargetVideoInitSegPath() {
        return targetVideoInitSegPath;
    }

    public void setTargetVideoInitSegPath(String targetVideoInitSegPath) {
        this.targetVideoInitSegPath = targetVideoInitSegPath;
    }

    public MpdManager getMpdManager() {
        return mpdManager;
    }

    public int incAndGetAudioRetryCount() {
        return audioRetryCount.incrementAndGet();
    }

    public int incAndGetVideoRetryCount() {
        return videoRetryCount.incrementAndGet();
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
