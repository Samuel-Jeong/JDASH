package dash.unit;

import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    private final StreamType type;
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

    public final String REMOTE_CAMERA_SERVICE_SCHEDULE_KEY;
    ///public final String OLD_FILE_CONTROL_SCHEDULE_KEY;
    private final ScheduleManager scheduleManager = new ScheduleManager();
    private RemoteStreamService remoteCameraService = null;
    //private OldFileController oldFileController = null;

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(StreamType type, String id, MPD mpd) {
        this.type = type;
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;

        this.REMOTE_CAMERA_SERVICE_SCHEDULE_KEY = "REMOTE_CAMERA_SERVICE_SCHEDULE_KEY:" + id;
        if (scheduleManager.initJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, 1, 1)) {
            logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
        }

        /*this.OLD_FILE_CONTROL_SCHEDULE_KEY = "OLD_FILE_CONTROL_SCHEDULE_KEY:" + id;
        if (scheduleManager.initJob(OLD_FILE_CONTROL_SCHEDULE_KEY, 1, 1)) {
            logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, OLD_FILE_CONTROL_SCHEDULE_KEY);
        }*/
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runRtmpStreaming(String uriFileName, String curRtmpUri, String mpdPath) {
        if (isRtmpStreaming.get()) {
            logger.warn("[DashUnit(id={})] runRtmpStreaming is already running...", id);
            return;
        }

        try {
            //////////////////////////////
            // REMOTE CAMERA SERVICE
            remoteCameraService = new RemoteStreamService(
                    scheduleManager,
                    RemoteStreamService.class.getSimpleName() + "_" + id,
                    0, 10, TimeUnit.MILLISECONDS,
                    1, 1, true,
                    id, configManager, uriFileName, curRtmpUri, mpdPath
            );

            if (remoteCameraService.init()) {
                scheduleManager.startJob(
                        REMOTE_CAMERA_SERVICE_SCHEDULE_KEY,
                        remoteCameraService
                );
                isRtmpStreaming.set(true);
                logger.debug("[DashUnit(id={})] [+RUN] RtmpStreaming", id);
            } else {
                logger.warn("[DashUnit(id={})] [-RUN FAIL] RtmpStreaming", id);
            }
            //////////////////////////////

            //////////////////////////////
            // OLD FILE CONTROLLER
            /*String dashPath = outputFilePath;
            String dashPathExtension = FileUtils.getExtension(dashPath);
            if (!dashPathExtension.isEmpty()) {
                dashPath = FileManager.getParentPathFromUri(dashPath);
                logger.debug("[DashUnit(id={})] DashPathExtension is [{}]. DashPath is [{}].", id, dashPathExtension, dashPath);
            }

            oldFileController = new OldFileController(
                    scheduleManager,
                    OldFileController.class.getSimpleName() + "_" + id,
                    0, 1000, TimeUnit.MILLISECONDS,
                    1, 1, true,
                    id, dashPath
            );
            scheduleManager.startJob(
                    OLD_FILE_CONTROL_SCHEDULE_KEY,
                    oldFileController
            );
            logger.debug("[DashUnit(id={})] [+RUN] OldFileController", id);*/
            //////////////////////////////
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runRtmpStreaming.Exception", id, e);
        }
    }

    public void finishRtmpStreaming() {
        if (isRtmpStreaming.get()) {
            /*if (oldFileController != null) {
                scheduleManager.stopJob(OLD_FILE_CONTROL_SCHEDULE_KEY, oldFileController);
                oldFileController = null;
                logger.debug("[DashUnit(id={})] [-FINISH] OldFileController", id);
            }*/

            if (remoteCameraService != null) {
                scheduleManager.stopJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, remoteCameraService);
                remoteCameraService.stop();
                remoteCameraService = null;
                logger.debug("[DashUnit(id={})] [-FINISH] RtmpStreaming", id);
                isRtmpStreaming.set(false);
            }
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
    public StreamType getType() {
        return type;
    }

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
                "type=" + type.name() +
                ", initiationTime=" + initiationTime +
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

    ////////////////////////////////////////////////////////////
    private static class OldFileController extends Job {

        private final String dashUnitId; // DashUnit ID
        private final String dashPath; // 현재 DASH Streaming 경로
        private final long limitTime; // 제한 시간

        public OldFileController(ScheduleManager scheduleManager, String name,
                                 int initialDelay, int interval, TimeUnit timeUnit,
                                 int priority, int totalRunCount, boolean isLasted,
                                 String dashUnitId, String dashPath) {
            super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

            this.dashUnitId = dashUnitId;
            this.dashPath = dashPath;

            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            this.limitTime = configManager.getChunkFileDeletionIntervalSeconds();

            logger.debug("[DashUnit(id={})] OldFileController is initiated. (dashPath={}, timeLimit={})",
                    dashUnitId, dashPath, limitTime
            );
        }

        @Override
        public void run() {
            if (dashPath == null) { return; }

            try {
                FileManager.deleteOldFilesBySecond(
                        dashPath,
                        "init",
                        limitTime
                );
            } catch (Exception e) {
                logger.warn("[DashUnit(id={})] OldFileController.run.Exception", dashUnitId, e);
            }
        }
    }
    ////////////////////////////////////////////////////////////

}
