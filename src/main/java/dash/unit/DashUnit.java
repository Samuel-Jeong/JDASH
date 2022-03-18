package dash.unit;

import config.ConfigManager;
import dash.client.DashClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
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

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();

    private final StreamType type;
    private final long initiationTime;
    private final String id;
    private final long expires;

    transient private MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String curSegmentName = null;

    private Duration duration = null;
    private Duration minBufferTime= null;

    private final AtomicBoolean isLiveStreaming = new AtomicBoolean(false);
    private boolean isRegistered = false;

    public final String REMOTE_CAMERA_SERVICE_SCHEDULE_KEY;

    transient private final ScheduleManager scheduleManager = new ScheduleManager();
    transient private RemoteStreamService remoteCameraService = null;

    private DashClient dashClient = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(StreamType type, String id, MPD mpd, long expires) {
        this.type = type;
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
        this.expires = expires;

        this.REMOTE_CAMERA_SERVICE_SCHEDULE_KEY = "REMOTE_CAMERA_SERVICE_SCHEDULE_KEY:" + id;
        if (scheduleManager.initJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, 1, 1)) {
            logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runLiveStreaming(String uriFileName, String sourceUri, String mpdPath) {
        if (isLiveStreaming.get()) {
            logger.warn("[DashUnit(id={})] runLiveStreaming is already running...", id);
            return;
        }

        try {
            //////////////////////////////
            if (configManager.getStreaming().equals("rtmp")) {
                // REMOTE CAMERA SERVICE with RTMP
                remoteCameraService = new RemoteStreamService(
                        scheduleManager,
                        RemoteStreamService.class.getSimpleName() + "_" + id,
                        0, 10, TimeUnit.MILLISECONDS,
                        1, 1, true,
                        id, configManager, uriFileName, sourceUri, mpdPath
                );

                if (remoteCameraService.init()) {
                    scheduleManager.startJob(
                            REMOTE_CAMERA_SERVICE_SCHEDULE_KEY,
                            remoteCameraService
                    );
                    isLiveStreaming.set(true);
                    logger.debug("[DashUnit(id={})] [+RUN] Rtmp client streaming", id);
                } else {
                    logger.warn("[DashUnit(id={})] [-RUN FAIL] Rtmp client streaming", id);
                }
            } else if (configManager.getStreaming().equals("dash")) {
                dashClient = new DashClient(
                        id,
                        ServiceManager.getInstance().getDashManager().getBaseEnvironment(),
                        sourceUri,
                        FileManager.getParentPathFromUri(mpdPath)
                );
                dashClient.start();
                logger.debug("[DashUnit(id={})] [+RUN] Dash client streaming", id);
            }
            //////////////////////////////
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runLiveStreaming.Exception", id, e);
        }
    }

    public void finishLiveStreaming() {
        if (isLiveStreaming.get()) {
            if (remoteCameraService != null) {
                scheduleManager.stopJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, remoteCameraService);
                remoteCameraService.stop();
                remoteCameraService = null;
                logger.debug("[DashUnit(id={})] [-FINISH] Rtmp client streaming", id);
            } else if (dashClient != null) {
                dashClient.stop();
                dashClient = null;
                logger.debug("[DashUnit(id={})] [-FINISH] Dash client streaming", id);
            }
            isLiveStreaming.set(false);
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
        return isLiveStreaming.get();
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    public long getExpires() {
        return expires;
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
                ", isLiveStreaming=" + isLiveStreaming.get() +
                '}';
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClient getDashClient() {
        return dashClient;
    }

    public void setDashClient(DashClient dashClient) {
        this.dashClient = dashClient;
    }
    ////////////////////////////////////////////////////////////

}
