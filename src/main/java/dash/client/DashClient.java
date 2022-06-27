package dash.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.DashHttpMessageSender;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.unit.DashUnit;
import dash.unit.MediaType;
import dash.unit.StreamType;
import dash.unit.segment.MediaSegmentController;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import network.definition.NetAddress;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.schedule.ScheduleManager;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [DASH Client] : [Remote Dash Unit] = 1 : 1
 */
public class DashClient {

    ////////////////////////////////////////////////////////////
    private transient static final Logger logger = LoggerFactory.getLogger(DashClient.class);
    private final transient ConfigManager configManager = AppInstance.getInstance().getConfigManager();

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
    private final transient Timer mpdTimer = new HashedWheelTimer();
    private transient Timeout mpdTimeout = null;
    private final transient Timer audioTimer = new HashedWheelTimer();
    private transient Timeout audioTimeout = null;
    private final transient Timer videoTimer = new HashedWheelTimer();
    private transient Timeout videoTimeout = null;

    private final AtomicInteger mpdRetryCount = new AtomicInteger(0);
    private final AtomicBoolean isMpdRetrying = new AtomicBoolean(false);

    private final AtomicInteger audioRetryCount = new AtomicInteger(0);
    private final AtomicBoolean isAudioRetrying = new AtomicBoolean(false);
    private final AtomicLong audioCompensationTime = new AtomicLong(0);

    private final AtomicInteger videoRetryCount = new AtomicInteger(0);
    private final AtomicBoolean isVideoRetrying = new AtomicBoolean(false);
    private final AtomicLong videoCompensationTime = new AtomicLong(0);

    private final transient DashClientFsmManager dashClientAudioFsmManager = new DashClientFsmManager();
    private final transient DashClientFsmManager dashClientVideoFsmManager;
    private final String dashClientStateUnitId;

    private final transient DashHttpMessageSender dashHttpMessageSender;

    private final transient MpdManager mpdManager;
    private final transient FileManager fileManager = new FileManager();

    private transient MediaSegmentController audioSegmentController = null;
    private transient MediaSegmentController videoSegmentController = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClient(String dashUnitId, String targetMpdPath, String srcPath, String targetBasePath) {
        this.dashUnitId = dashUnitId;
        this.dashClientStateUnitId = "DASH_CLIENT_STATE:" + dashUnitId;

        this.srcPath = srcPath;
        this.srcBasePath = fileManager.getParentPathFromUri(srcPath);
        this.uriFileName = fileManager.getFileNameFromUri(srcPath);
        this.targetBasePath = targetBasePath;
        this.targetMpdPath = targetMpdPath;

        this.dashHttpMessageSender = new DashHttpMessageSender(dashUnitId);
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

    public boolean start(ScheduleManager scheduleManager, NetAddress targetAddress) {
        //////////////////////////////
        // SETTING : HTTP
        if (!this.dashHttpMessageSender.start(this, targetAddress)) {
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

        //////////////////////////////
        // SETTING : Segment Controller for audio & video
        String dashPath = targetMpdPath;
        String dashPathExtension = FileUtils.getExtension(dashPath);
        if (!dashPathExtension.isEmpty()) {
            dashPath = fileManager.getParentPathFromUri(dashPath);
            logger.debug("[DashUnit(id={})] DashPathExtension is [{}]. DashPath is [{}].", dashUnitId, dashPathExtension, dashPath);
        }

        /**
         * # 문제
         * - 일정 시간이 지나면 오디오가 들리지 않는다.
         *
         * # 원인
         * - 비디오가 오디오보다 느리게 수신되기 때문이다.
         * > 비디오 세그먼트 번호가 오디오 세그먼트 번호보다 느려진다.
         * > 일정한 간격이 없이 계속해서 느려진다.
         * > 결국 해당 미디어(음성)는 스트림 참여자가 수신하기 전에 삭제된다. (데이터 유실, 소리가 안들림)
         * - 흘러가는 시간은 동일하지만 생성되는 비디오 세그먼트 번호는 점점 느려지고, 오디오 세그먼트 번호는 점점 빨라진다.
         *
         * # 해결 방안
         * - 다른 미디어 세그먼트 종류이므로 각 미디어마다 독립적인 삭제를 적용해야 한다. 즉, 흘러가는 시간을 각 미디어 종류마다 적용해야 한다.
         * - 요청받은 미디어 세그먼트 번호가 첫 번째 세그먼트 번호와 가깝다면(전체 세그먼트 파일 개수의 중간보다 작거나 같으면), 삭제하지 않도록 해서 미디어 연속성을 유지하도록 한다.
         */
        audioSegmentController = new MediaSegmentController(dashUnitId, MediaType.AUDIO, scheduleManager);
        audioSegmentController.start(getMpdManager(), dashPath);
        if (!configManager.isAudioOnly()) {
            videoSegmentController = new MediaSegmentController(dashUnitId, MediaType.VIDEO, scheduleManager);
            videoSegmentController.start(getMpdManager(), dashPath);
        }
        //////////////////////////////

        logger.debug("[DashClient({})] START", dashUnitId);
        return true;
    }

    public void stop() {
        if (audioSegmentController != null) {
            audioSegmentController.stop();
        }

        if (videoSegmentController != null) {
            videoSegmentController.stop();
        }

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

    public int getMpdRetryCount() {
        return mpdRetryCount.get();
    }

    public void setMpdRetryCount(int retryCount) {
        mpdRetryCount.set(retryCount);
    }

    public int incAndGetMpdRetryCount() {
        return mpdRetryCount.incrementAndGet();
    }

    public int getAudioRetryCount() {
        return audioRetryCount.get();
    }

    public void setAudioRetryCount(int retryCount) {
        audioRetryCount.set(retryCount);
    }

    public int incAndGetAudioRetryCount() {
        return audioRetryCount.incrementAndGet();
    }

    public long getAudioCompensationTime() {
        return audioCompensationTime.get();
    }

    public void setAudioCompensationTime(long audioCompensationTime) {
        this.audioCompensationTime.set(audioCompensationTime);
    }

    public int getVideoRetryCount() {
        return videoRetryCount.get();
    }

    public void setVideoRetryCount(int retryCount) {
        videoRetryCount.set(retryCount);
    }

    public int incAndGetVideoRetryCount() {
        return videoRetryCount.incrementAndGet();
    }

    public long getVideoCompensationTime() {
        return videoCompensationTime.get();
    }

    public void setVideoCompensationTime(long videoCompensationTime) {
        this.videoCompensationTime.set(videoCompensationTime);
    }

    public boolean isMpdRetrying() {
        return isMpdRetrying.get();
    }

    public void setIsMpdRetrying(boolean isRetrying) {
        isMpdRetrying.set(isRetrying);
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

    public MediaSegmentController getAudioSegmentController() {
        return audioSegmentController;
    }

    public MediaSegmentController getVideoSegmentController() {
        return videoSegmentController;
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
