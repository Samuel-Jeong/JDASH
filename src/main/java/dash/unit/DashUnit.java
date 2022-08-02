package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.mpd.parser.mpd.MPD;
import network.definition.NetAddress;
import network.socket.SocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.job.JobBuilder;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    private final transient ConfigManager configManager = AppInstance.getInstance().getConfigManager();

    private final StreamType type;
    private final long initiationTime;
    private final String id;
    private final long expires;

    private transient MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String mpdParentPath = null;

    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicBoolean isLiveStreaming = new AtomicBoolean(false);

    public final String REMOTE_CAMERA_SERVICE_SCHEDULE_KEY;

    private final transient ScheduleManager scheduleManager = new ScheduleManager();
    private transient RemoteStreamService remoteStreamService = null;

    private transient DashClient dashClient = null;
    private final transient FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(StreamType type, String id, MPD mpd, long expires, boolean isDynamic) {
        this.type = type;
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
        this.expires = expires;

        this.REMOTE_CAMERA_SERVICE_SCHEDULE_KEY = "REMOTE_CAMERA_SERVICE_SCHEDULE_KEY:" + id;
        //if (isDynamic) {
            if (scheduleManager.initJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, 1, 1)) {
                logger.debug("[DashUnit(id={})] Success to init job scheduler ({})", id, REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
            }
        //}
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean runLiveStreaming(String uriFileName, String sourceUri, String mpdPath, MpdManager mpdManager) {
        if (isLiveStreaming.get()) {
            logger.warn("[DashUnit(id={})] runLiveStreaming is already running...", id);
            return false;
        }

        try {
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
                // REMOTE CAMERA SERVICE with RTMP
                Job remoteStreamServiceJob = new JobBuilder()
                        .setScheduleManager(scheduleManager)
                        .setName(RemoteStreamService.class.getSimpleName() + "_" + id)
                        .setInitialDelay(0)
                        .setInterval(10)
                        .setTimeUnit(TimeUnit.MILLISECONDS)
                        .setPriority(1)
                        .setTotalRunCount(1)
                        .setIsLasted(true)
                        .build();
                remoteStreamService = new RemoteStreamService(
                        remoteStreamServiceJob,
                        id, configManager, uriFileName, sourceUri, mpdPath
                );

                if (remoteStreamService.init()) {
                    remoteStreamService.start();
                    if (scheduleManager.startJob(
                            REMOTE_CAMERA_SERVICE_SCHEDULE_KEY,
                            remoteStreamService.getJob())) {
                        logger.debug("[DashUnit(id={})] [+RUN] Rtmp client streaming", id);
                    } else {
                        logger.warn("[DashUnit(id={})] [-RUN FAIL] Rtmp client streaming", id);
                        return false;
                    }
                } else {
                    logger.warn("[DashUnit(id={})] [-RUN FAIL] Rtmp client streaming", id);
                    return false;
                }

                isLiveStreaming.set(true);
            } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                dashClient = new DashClient(
                        id,
                        mpdPath, sourceUri, fileManager.getParentPathFromUri(mpdPath),
                        mpdManager
                );

                NetAddress targetAddress = new NetAddress(
                        configManager.getHttpTargetIp(),
                        configManager.getHttpTargetPort(),
                        true, SocketProtocol.TCP
                );
                if (!dashClient.start(scheduleManager, targetAddress)) {
                    logger.warn("[DashUnit(id={})] [-RUN FAIL] Dash client streaming", id);
                    return false;
                }

                new Thread(() -> {
                    TimeUnit timeUnit = TimeUnit.SECONDS;
                    try {
                        timeUnit.sleep((long) configManager.getRemoteTimeOffset());
                        String curUri = fileManager.concatFilePath(sourceUri, uriFileName + StreamConfigManager.DASH_POSTFIX);
                        dashClient.sendHttpGetRequest(curUri, MessageType.MPD);
                    } catch (Exception e) {
                        logger.warn("[DashUnit(id={})] [FAIL] (timeUnit.sleep) or (dashClient.sendHttpGetRequest)", id, e);
                    }
                }).start();

                logger.debug("[DashUnit(id={})] [+RUN] Dash client streaming", id);
                isLiveStreaming.set(true);
            } else {
                logger.warn("[DashUnit(id={})] Unknown stream type is detected. Fail to stream the live streaming.", id);
                return false;
            }

            return true;
            //////////////////////////////
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runLiveStreaming.Exception", id, e);
            return false;
        }
    }

    public void finishLiveStreaming() {
        if (isLiveStreaming.get()) {
            //////////////////////////////
            // REMOTE CAMERA SERVICE with RTMP
            if (remoteStreamService != null) {
                scheduleManager.stopJob(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY, remoteStreamService.getJob());
                remoteStreamService.stop();
                remoteStreamService = null;
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
        scheduleManager.stopAll(REMOTE_CAMERA_SERVICE_SCHEDULE_KEY);
    }

    public void clearMpdPath() {
        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = fileManager.getParentPathFromUri(outputFilePath);
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

    public String getMpdParentPath() {
        return mpdParentPath;
    }

    public void setMpdParentPath(String mpdParentPath) {
        this.mpdParentPath = mpdParentPath;
    }

    public boolean getIsRegistered() {
        return isRegistered.get();
    }

    public void setIsRegistered(boolean isRegistered) {
        this.isRegistered.set(isRegistered);
    }

    public boolean isLiveStreaming() {
        return isLiveStreaming.get();
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
