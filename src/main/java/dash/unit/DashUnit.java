package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.parser.mpd.MPD;
import dash.unit.tool.OldFileController;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    transient private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();

    private final StreamType type;
    private final long initiationTime;
    private final String id;
    private final long expires;

    transient private MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String curSegmentName = null;

    private final AtomicBoolean isLiveStreaming = new AtomicBoolean(false);
    private boolean isRegistered = false;

    public final String REMOTE_CAMERA_SERVICE_SCHEDULE_KEY;
    public final String OLD_FILE_CONTROL_SCHEDULE_KEY;

    transient private final ScheduleManager scheduleManager = new ScheduleManager();
    transient private RemoteStreamService remoteCameraService = null;

    transient private DashClient dashClient = null;
    transient private OldFileController oldFileController = null;
    transient private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(StreamType type, String id, MPD mpd, long expires, boolean isDynamic) {
        this.type = type;
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
        this.expires = expires;

        this.REMOTE_CAMERA_SERVICE_SCHEDULE_KEY = "REMOTE_CAMERA_SERVICE_SCHEDULE_KEY:" + id;
        this.OLD_FILE_CONTROL_SCHEDULE_KEY = "OLD_FILE_CONTROL_SCHEDULE_KEY:" + id;
        if (isDynamic) {
            if (scheduleManager.initJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, 1, 1)) {
                logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
            }

            if (scheduleManager.initJob(OLD_FILE_CONTROL_SCHEDULE_KEY, 1, 1)) {
                logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, OLD_FILE_CONTROL_SCHEDULE_KEY);
            }
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
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
                //////////////////////////////
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
                    logger.debug("[DashUnit(id={})] [+RUN] Rtmp client streaming", id);
                } else {
                    logger.warn("[DashUnit(id={})] [-RUN FAIL] Rtmp client streaming", id);
                }
                //////////////////////////////
            } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                dashClient = new DashClient(
                        id,
                        ServiceManager.getInstance().getDashServer().getBaseEnvironment(),
                        sourceUri,
                        fileManager.getParentPathFromUri(mpdPath)
                );
                if (!dashClient.start()) {
                    logger.warn("[DashUnit(id={})] [-RUN FAIL] Dash client streaming", id);
                    return;
                }
                dashClient.sendHttpGetRequest(sourceUri, MessageType.MPD);

                //////////////////////////////
                // OLD FILE CONTROLLER
                String dashPath = mpdPath;
                String dashPathExtension = FileUtils.getExtension(dashPath);
                if (!dashPathExtension.isEmpty()) {
                    dashPath = fileManager.getParentPathFromUri(dashPath);
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
                logger.debug("[DashUnit(id={})] [+RUN] OldFileController", id);
                //////////////////////////////

                logger.debug("[DashUnit(id={})] [+RUN] Dash client streaming", id);
            }

            isLiveStreaming.set(true);
            //////////////////////////////
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runLiveStreaming.Exception", id, e);
        }
    }

    public void finishLiveStreaming() {
        if (isLiveStreaming.get()) {
            //////////////////////////////
            // OLD FILE CONTROLLER
            if (oldFileController != null) {
                scheduleManager.stopJob(OLD_FILE_CONTROL_SCHEDULE_KEY, oldFileController);
                oldFileController = null;
                logger.debug("[DashUnit(id={})] [-FINISH] OldFileController", id);
            }
            //////////////////////////////

            //////////////////////////////
            // REMOTE CAMERA SERVICE with RTMP
            if (remoteCameraService != null) {
                scheduleManager.stopJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, remoteCameraService);
                remoteCameraService.stop();
                remoteCameraService = null;
                logger.debug("[DashUnit(id={})] [-FINISH] Rtmp client streaming", id);
            }
            // REMOTE CAMERA SERVICE with DASH
            else if (dashClient != null) {
                dashClient.stop();
                dashClient = null;
                logger.debug("[DashUnit(id={})] [-FINISH] Dash client streaming", id);
            }
            //////////////////////////////

            //////////////////////////////
            // CLEAR Dash data if session closed
            if (configManager.isClearDashDataIfSessionClosed()) {
                clearMpdPath();
            }
            //////////////////////////////

            isLiveStreaming.set(false);
        }
    }

    public void stop() {
        scheduleManager.stopAll(OLD_FILE_CONTROL_SCHEDULE_KEY);
        scheduleManager.stopAll(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
    }

    public void clearMpdPath() {
        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = fileManager.getParentPathFromUri(outputFilePath);
            //logger.debug("[DashUnit(id={})] outputFilePath: {}, mpdParentPath: {}", id, outputFilePath, mpdParentPath);
            if (mpdParentPath != null) {
                fileManager.deleteFile(mpdParentPath);
                logger.debug("[DashUnit(id={})] DELETE ALL MPD Files. (path={})", id, mpdParentPath);
            }
        }
    }

    public byte[] getSegmentByteData(String uri) {
        return fileManager.readAllBytes(uri);
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
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
