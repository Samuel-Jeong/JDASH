package dash.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.DashHttpMessageSender;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import instance.BaseEnvironment;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.StreamConfigManager;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static final long TIMEOUT = 10000; // ms
    transient private final Timer mpdTimer = new HashedWheelTimer();
    transient private Timeout mpdTimeout = null;
    transient private final Timer audioTimer = new HashedWheelTimer();
    transient private Timeout audioTimeout = null;
    transient private final Timer videoTimer = new HashedWheelTimer();
    transient private Timeout videoTimeout = null;

    private final AtomicInteger audioRetryCount = new AtomicInteger(0);
    private final AtomicBoolean isAudioRetrying = new AtomicBoolean(false);
    private final AtomicInteger videoRetryCount = new AtomicInteger(0);
    private final AtomicBoolean isVideoRetrying = new AtomicBoolean(false);

    transient private final DashClientFsmManager dashClientAudioFsmManager = new DashClientFsmManager();
    transient private final DashClientFsmManager dashClientVideoFsmManager;
    private final String dashClientStateUnitId;

    transient private final DashHttpMessageSender dashHttpMessageSender;

    transient private final MpdManager mpdManager;
    transient private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClient(String dashUnitId, BaseEnvironment baseEnvironment, String srcPath, String targetBasePath) {
        this.dashUnitId = dashUnitId;
        this.dashClientStateUnitId = "DASH_CLIENT_STATE:" + dashUnitId;

        this.srcPath = srcPath;
        this.srcBasePath = fileManager.getParentPathFromUri(srcPath);
        this.uriFileName = fileManager.getFileNameFromUri(srcPath);
        this.targetBasePath = targetBasePath;
        this.targetMpdPath = fileManager.concatFilePath(this.targetBasePath, uriFileName + StreamConfigManager.DASH_POSTFIX);

        this.dashHttpMessageSender = new DashHttpMessageSender(dashUnitId, baseEnvironment, false); // SSL 아직 미지원
        this.mpdManager = new MpdManager(dashUnitId, targetMpdPath);

        if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
            dashClientVideoFsmManager = new DashClientFsmManager();
        } else {
            dashClientVideoFsmManager = null;
        }

        logger.debug("[DashClient({})] Created. (dashClientStateUnitId={}, srcPath={}, uriFileName={}, targetBasePath={}, targetMpdPath={})",
                this.dashUnitId, this.dashClientStateUnitId,
                this.srcPath, this.uriFileName,
                this.targetBasePath, this.targetMpdPath
        );
    }

    public boolean start() {
        //////////////////////////////
        // SETTING : HTTP
        if (!this.dashHttpMessageSender.start(this)) {
            return false;
        }
        //////////////////////////////

        //////////////////////////////
        // SETTING : FSM
        this.dashClientAudioFsmManager.init(this);
        this.dashClientAudioFsmManager.getStateManager().addStateUnit(
                dashClientStateUnitId,
                this.dashClientAudioFsmManager.getStateManager().getStateHandler(DashClientState.NAME).getName(),
                DashClientState.IDLE,
                null
        );
        StateUnit dashClientStateUnit = this.dashClientAudioFsmManager.getStateManager().getStateUnit(dashClientStateUnitId);
        dashClientStateUnit.setData(this);

        if (this.dashClientVideoFsmManager != null) {
            this.dashClientVideoFsmManager.init(this);
            this.dashClientVideoFsmManager.getStateManager().addStateUnit(
                    dashClientStateUnitId,
                    this.dashClientVideoFsmManager.getStateManager().getStateHandler(DashClientState.NAME).getName(),
                    DashClientState.IDLE,
                    null
            );
            dashClientStateUnit = this.dashClientVideoFsmManager.getStateManager().getStateUnit(dashClientStateUnitId);
            dashClientStateUnit.setData(this);
        }
        //////////////////////////////

        //////////////////////////////
        // SETTING : TARGET PATH
        if (!fileManager.isExist(targetBasePath)) {
            fileManager.mkdirs(targetBasePath);
        }
        //////////////////////////////

        logger.debug("[DashClient({})] START", dashUnitId);
        return true;
    }

    public void stop() {
        stopMpdTimeout();
        stopAudioTimeout();
        stopVideoTimeout();

        this.dashClientAudioFsmManager.getStateManager().removeStateUnit(dashClientStateUnitId);
        if (this.dashClientVideoFsmManager != null) {
            dashClientVideoFsmManager.getStateManager().removeStateUnit(dashClientStateUnitId);
        }

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
            if (logger.isTraceEnabled()) {
                logger.trace("[DashClient({})] [SEND] Request=\n{}", dashUnitId, httpRequest);
            }
        }

        switch (messageType) {
            case MPD:
                dashHttpMessageSender.sendMessageForMpd(httpRequest);
                startMpdTimeout();
                break;
            case AUDIO:
                dashHttpMessageSender.sendMessageForAudio(httpRequest);
                startAudioTimeout();
                break;
            case VIDEO:
                dashHttpMessageSender.sendMessageForVideo(httpRequest);
                startVideoTimeout();
                break;
            default:
                break;
        }
    }

    public String getSourcePath(String additionalPath) {
        if (additionalPath == null) { return null; }

        return fileManager.concatFilePath(
                getSrcBasePath(),
                additionalPath
        );
    }

    public String getTargetPath(String additionalPath) {
        if (additionalPath == null) { return null; }

        return fileManager.concatFilePath(
                getTargetBasePath(),
                additionalPath
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean isStopped() {
        return isStopped;
    }

    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }

    public DashClientFsmManager getDashClientAudioFsmManager() {
        return dashClientAudioFsmManager;
    }

    public DashClientFsmManager getDashClientVideoFsmManager() {
        return dashClientVideoFsmManager;
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

    public int getAudioRetryCount() {
        return audioRetryCount.get();
    }

    public void setAudioRetryCount(int retryCount) {
        int curRetryCount = audioRetryCount.get();
        audioRetryCount.set(retryCount);
        logger.debug("[DashClient({})] SET audioRetryCount: {} > {}", dashUnitId, curRetryCount, audioRetryCount.get());
    }

    public int incAndGetAudioRetryCount() {
        return audioRetryCount.incrementAndGet();
    }

    public int getVideoRetryCount() {
        return videoRetryCount.get();
    }

    public void setVideoRetryCount(int retryCount) {
        int curRetryCount = videoRetryCount.get();
        videoRetryCount.set(retryCount);
        logger.debug("[DashClient({})] SET videoRetryCount: {} > {}", dashUnitId, curRetryCount, videoRetryCount.get());
    }

    public int incAndGetVideoRetryCount() {
        return videoRetryCount.incrementAndGet();
    }

    public boolean isAudioRetrying() {
        return isAudioRetrying.get();
    }

    public void setIsAudioRetrying(boolean isRetrying) {
        isAudioRetrying.set(isRetrying);
    }

    public boolean isVideoRetrying() {
        return isVideoRetrying.get();
    }

    public void setIsVideoRetrying(boolean isRetrying) {
        isVideoRetrying.set(isRetrying);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public Timeout getMpdTimeout() {
        return mpdTimeout;
    }

    public void startMpdTimeout() {
        if (mpdTimeout != null) { return; }

        mpdTimeout = mpdTimer.newTimeout(
                timeout -> {
                    DashUnit dashUnit = ServiceManager.getInstance().getDashServer().getDashUnitById(dashUnitId);
                    if (dashUnit != null) {
                        if (dashUnit.getType().equals(StreamType.STATIC)) {
                            stop();
                        } else {
                            ServiceManager.getInstance().getDashServer().deleteDashUnit(dashUnitId);
                        }
                    }
                    logger.warn("[DashClient({})] MPD REQUEST TIMEOUT. ({})", dashUnitId, TIMEOUT);
                }, TIMEOUT, TimeUnit.MILLISECONDS
        );
        //logger.debug("[DashClient({})] MPD REQUEST TIMER ON.", dashUnitId);
    }

    public void stopMpdTimeout() {
        if (mpdTimeout != null) {
            mpdTimeout.cancel();
            mpdTimeout = null;
            //logger.debug("[DashClient({})] MPD REQUEST TIMER OFF.", dashUnitId);
        }
    }

    public Timeout getAudioTimeout() {
        return audioTimeout;
    }

    public void startAudioTimeout() {
        if (audioTimeout != null) { return; }

        audioTimeout = audioTimer.newTimeout(
                timeout -> {
                    DashUnit dashUnit = ServiceManager.getInstance().getDashServer().getDashUnitById(dashUnitId);
                    if (dashUnit != null) {
                        if (dashUnit.getType().equals(StreamType.STATIC)) {
                            stop();
                        } else {
                            ServiceManager.getInstance().getDashServer().deleteDashUnit(dashUnitId);
                        }
                    }
                    logger.warn("[DashClient({})] AUDIO REQUEST TIMEOUT. ({})", dashUnitId, TIMEOUT);
                }, TIMEOUT, TimeUnit.MILLISECONDS
        );
        //logger.debug("[DashClient({})] AUDIO REQUEST TIMER ON.", dashUnitId);
    }

    public void stopAudioTimeout() {
        if (audioTimeout != null) {
            audioTimeout.cancel();
            audioTimeout = null;
            //logger.debug("[DashClient({})] AUDIO REQUEST TIMER OFF.", dashUnitId);
        }
    }

    public Timeout getVideoTimeout() {
        return videoTimeout;
    }

    public void startVideoTimeout() {
        if (videoTimeout != null) { return; }

        videoTimeout = videoTimer.newTimeout(
                timeout -> {
                    DashUnit dashUnit = ServiceManager.getInstance().getDashServer().getDashUnitById(dashUnitId);
                    if (dashUnit != null) {
                        if (dashUnit.getType().equals(StreamType.STATIC)) {
                            stop();
                        } else {
                            ServiceManager.getInstance().getDashServer().deleteDashUnit(dashUnitId);
                        }
                    }
                    logger.warn("[DashClient({})] VIDEO REQUEST TIMEOUT. ({})", dashUnitId, TIMEOUT);
                }, TIMEOUT, TimeUnit.MILLISECONDS
        );
        //logger.debug("[DashClient({})] VIDEO REQUEST TIMER ON.", dashUnitId);
    }

    public void stopVideoTimeout() {
        if (videoTimeout != null) {
            videoTimeout.cancel();
            videoTimeout = null;
            //logger.debug("[DashClient({})] VIDEO REQUEST TIMER OFF.", dashUnitId);
        }
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
