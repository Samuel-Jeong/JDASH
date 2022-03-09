package dash.unit;

import cam.RemoteCameraService;
import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    private final long initiationTime;
    private final String id;

    transient private MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String curSegmentName = null;

    private Duration duration = null;
    private Duration minBufferTime= null;

    private boolean isLiveStreaming = false;

    private final AtomicBoolean isRtmpStreaming = new AtomicBoolean(false);
    private boolean isRegistered = false;

    public final String DASH_UNIT_SCHEDULE_KEY;
    private final ScheduleManager scheduleManager = new ScheduleManager();
    private RemoteCameraService remoteCameraService = null;

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(String id, MPD mpd) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
        this.DASH_UNIT_SCHEDULE_KEY = "SCHEDULE_" + id;
        if (scheduleManager.initJob(DASH_UNIT_SCHEDULE_KEY, 1, 1)) {
            logger.debug("[DashUnit(id={})] Success to init job scheduler", id);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runRtmpStreaming(String uriFileName, String curRtmpUri, String mpdPath) {
        if (isRtmpStreaming.get()) {
            logger.warn("[DashUnit(id={})] runRtmpStreaming is already running...", id);
            return;
        }

        try {
            remoteCameraService = new RemoteCameraService(
                    scheduleManager,
                    RemoteCameraService.class.getSimpleName(),
                    0, 1, TimeUnit.MILLISECONDS,
                    1, 1, false,
                    id, configManager, uriFileName, curRtmpUri, mpdPath
            );
            scheduleManager.startJob(
                    DASH_UNIT_SCHEDULE_KEY,
                    remoteCameraService
            );
            isRtmpStreaming.set(true);
            logger.debug("[DashUnit(id={})] [+RUN] RtmpStreaming", id);
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runRtmpStreaming.Exception", id, e);
        }
    }

    public void finishRtmpStreaming() {
        if (isRtmpStreaming.get() && remoteCameraService != null) {
            scheduleManager.stopJob(DASH_UNIT_SCHEDULE_KEY, remoteCameraService);
            remoteCameraService = null;
            clearMpdPath();
            logger.debug("[DashUnit(id={})] [-FINISH] RtmpStreaming", id);
            isRtmpStreaming.set(false);
        }
    }

    public void clearMpdPath() {
        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = FileManager.getParentPathFromUri(outputFilePath);
            //logger.debug("[DashUnit(id={})] outputFilePath: {}, mpdParentPath: {}", id, outputFilePath, mpdParentPath);
            if (mpdParentPath != null) {
                FileManager.deleteFile(mpdParentPath);
                logger.debug("[DashUnit(id={})] DELETE ALL MPD Files. (path={})", id, mpdParentPath);
            }
        }
    }

    public byte[] getSegmentByteData(String uri) {
        return FileManager.readAllBytes(uri);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getInitiationTime() {
        return initiationTime;
    }

    public String getId() {
        return id;
    }

    public MPD getMpd() {
        return mpd;
    }

    public void setMpd(MPD mpd) {
        this.mpd = mpd;
    }

    public String getInputFilePath() {
        return inputFilePath;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public void setOutputFilePath(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Duration getMinBufferTime() {
        return minBufferTime;
    }

    public void setMinBufferTime(Duration minBufferTime) {
        this.minBufferTime = minBufferTime;
    }

    public String getCurSegmentName() {
        return curSegmentName;
    }

    public void setCurSegmentName(String curSegmentName) {
        this.curSegmentName = curSegmentName;
    }

    public boolean isLiveStreaming() {
        return isLiveStreaming;
    }

    public void setLiveStreaming(boolean liveStreaming) {
        isLiveStreaming = liveStreaming;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    @Override
    public String toString() {
        return "DashUnit{" +
                "initiationTime=" + initiationTime +
                ", id='" + id + '\'' +
                ", inputFilePath='" + inputFilePath + '\'' +
                ", outputFilePath='" + outputFilePath + '\'' +
                ", curSegmentName='" + curSegmentName + '\'' +
                ", duration=" + duration +
                ", minBufferTime=" + minBufferTime +
                ", isLiveStreaming=" + isLiveStreaming +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
