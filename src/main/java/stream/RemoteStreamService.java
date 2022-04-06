package stream;

import config.ConfigManager;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;

public class RemoteStreamService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(RemoteStreamService.class);

    private final String dashUnitId;

    private final ConfigManager configManager;
    private final ScheduleManager scheduleManager;
    private static final String REMOTE_STREAM_SCHEDULE_KEY = "REMOTE_STREAM_SCHEDULE_KEY";
    private final ConcurrentCyclicFIFO<Frame> frameQueue = new ConcurrentCyclicFIFO<>();
    private CameraCanvasController remoteCameraCanvasController = null;

    private static String URI_FILE_NAME = null;
    private final String RTMP_PATH;
    private final String DASH_PATH;

    private static long startTime = 0;
    private boolean exit = false;

    private FFmpegFrameGrabber fFmpegFrameGrabber = null;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public RemoteStreamService(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted,
                               String dashUnitId, ConfigManager configManager, String uriFileName, String rtmpPath, String dashPath) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = dashUnitId;
        this.scheduleManager = scheduleManager;
        this.configManager = configManager;

        URI_FILE_NAME = uriFileName;
        RTMP_PATH = rtmpPath;
        DASH_PATH = dashPath;
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // FOR TEST
    public RemoteStreamService(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = null;
        this.scheduleManager = scheduleManager;
        this.configManager = new ConfigManager("/Users/jamesj/GIT_PROJECTS/JDASH/src/main/resources/config/user_conf.ini");

        String networkPath = StreamConfigManager.RTMP_PREFIX + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        FileManager fileManager = new FileManager();
        RTMP_PATH = fileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = fileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + StreamConfigManager.DASH_POSTFIX);
        URI_FILE_NAME = fileManager.getFileNameFromUri(DASH_PATH);
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public boolean init() {
        try {
            /////////////////////////////////
            // [INPUT] FFmpegFrameGrabber
            fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(RTMP_PATH);
            if (!configManager.isAudioOnly()) {
                fFmpegFrameGrabber.setImageWidth(StreamConfigManager.CAPTURE_WIDTH);
                fFmpegFrameGrabber.setImageHeight(StreamConfigManager.CAPTURE_HEIGHT);
            }
            fFmpegFrameGrabber.start();
            /////////////////////////////////

            /////////////////////////////////
            // [OUTPUT] REMOTE CAMERA CANVAS CONTROLLER
            if (configManager.isEnableClient() && configManager.isEnableGui() && !configManager.isAudioOnly()) {
                if (scheduleManager.initJob(REMOTE_STREAM_SCHEDULE_KEY, 1, 1)) {
                    logger.debug("[RemoteStreamService] Success to init [{}]", REMOTE_STREAM_SCHEDULE_KEY);

                    remoteCameraCanvasController = new CameraCanvasController(
                            scheduleManager,
                            CameraCanvasController.class.getSimpleName(),
                            0, 1, TimeUnit.MILLISECONDS,
                            1, 1, true,
                            false, frameQueue, fFmpegFrameGrabber.getGamma()
                    );
                    if (scheduleManager.startJob(REMOTE_STREAM_SCHEDULE_KEY, remoteCameraCanvasController)) {
                        logger.debug("[RemoteStreamService({})] [+RUN] Success to start the remote camera.", dashUnitId);
                    } else {
                        logger.warn("[RemoteStreamService({})] [-RUN FAIL] Fail to start the remote camera.", dashUnitId);
                        return false;
                    }
                }
            }
            /////////////////////////////////
        } catch (Exception e) {
            logger.warn("RemoteStreamService.init.Exception", e);
            return false;
        }

        logger.debug("[RemoteStreamService] [INIT] RTMP_PATH=[{}], DASH_PATH=[{}]", RTMP_PATH, DASH_PATH);
        return true;
    }

    public void stop() {
        exit = true;

        try {
            if (remoteCameraCanvasController != null) {
                scheduleManager.stopJob(REMOTE_STREAM_SCHEDULE_KEY, remoteCameraCanvasController);
                remoteCameraCanvasController = null;
            }

            if (fFmpegFrameGrabber != null) {
                fFmpegFrameGrabber.stop();
                fFmpegFrameGrabber.release();
                fFmpegFrameGrabber = null;
            }
        } catch (Exception e) {
            logger.warn("RemoteStreamService.run.finally.Exception", e);
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        //logger.info("[RemoteStreamService] RUNNING...");

        FFmpegFrameRecorder audioFrameRecorder = null;
        FFmpegFrameRecorder videoFrameRecorder = null;
        try {
            /////////////////////////////////
            // [OUTPUT] FFmpegFrameRecorder
            if (configManager.isAudioOnly()) {
                audioFrameRecorder = new FFmpegFrameRecorder(
                        DASH_PATH,
                        AudioService.CHANNEL_NUM
                );
                StreamConfigManager.setRemoteStreamAudioOptions(audioFrameRecorder);
                StreamConfigManager.setDashOptions(audioFrameRecorder,
                        URI_FILE_NAME,
                        configManager.getSegmentDuration(), configManager.getWindowSize()
                );
                audioFrameRecorder.start();
            } else {
                videoFrameRecorder = new FFmpegFrameRecorder(
                        DASH_PATH,
                        StreamConfigManager.CAPTURE_WIDTH, StreamConfigManager.CAPTURE_HEIGHT,
                        AudioService.CHANNEL_NUM
                );
                StreamConfigManager.setRemoteStreamVideoOptions(videoFrameRecorder);
                StreamConfigManager.setRemoteStreamAudioOptions(videoFrameRecorder);
                StreamConfigManager.setDashOptions(videoFrameRecorder,
                        URI_FILE_NAME,
                        configManager.getSegmentDuration(), configManager.getWindowSize()
                );
                videoFrameRecorder.start();
            }
            avutil.av_log_set_level(AV_LOG_ERROR);
            /////////////////////////////////

            /////////////////////////////////
            while (!exit) {
                //////////////////////////////////////
                // GRAB FRAME [AUDIO ONLY]
                Frame capturedFrame;
                if (configManager.isAudioOnly()) {
                    capturedFrame = fFmpegFrameGrabber.grabSamples();
                } else {
                    capturedFrame = fFmpegFrameGrabber.grab();
                }
                if(capturedFrame == null) {
                    // 세션 삭제
                    ServiceManager.getInstance().getDashServer().deleteDashUnit(dashUnitId);
                    logger.debug("[RemoteStreamService({})] Remote stream is disconnected.", dashUnitId);
                    break;
                }
                //////////////////////////////////////

                //////////////////////////////////////
                if (configManager.isAudioOnly() && audioFrameRecorder != null) {
                    // AUDIO DATA ONLY
                    if (capturedFrame.samples != null && capturedFrame.samples.length > 0) {
                        audioFrameRecorder.record(capturedFrame);
                    }
                } else if (videoFrameRecorder != null) {
                    //////////////////////////////////////
                    // Check for AV drift
                    if (startTime == 0) { startTime = System.currentTimeMillis(); }
                    long curTimeStamp = 1000 * (System.currentTimeMillis() - startTime);
                    if (curTimeStamp > videoFrameRecorder.getTimestamp()) { // Lip-flap correction
                        videoFrameRecorder.setTimestamp(curTimeStamp);
                    }
                    //////////////////////////////////////

                    //////////////////////////////////////
                    // INTERLEAVED DATA
                    if (capturedFrame.image != null && capturedFrame.samples != null) {
                        //logger.warn("[INTERLEAVED] FRAME: {} {}", capturedFrame.timestamp, capturedFrame.getTypes());
                        videoFrameRecorder.record(capturedFrame);
                        if (remoteCameraCanvasController != null) { frameQueue.offer(capturedFrame); }
                    }
                    //////////////////////////////////////
                    // VIDEO DATA
                    else if (capturedFrame.image != null && capturedFrame.image.length > 0) {
                        videoFrameRecorder.record(capturedFrame);
                        if (remoteCameraCanvasController != null) { frameQueue.offer(capturedFrame); }
                    }
                    //////////////////////////////////////
                    // AUDIO DATA
                    else if (capturedFrame.samples != null && capturedFrame.samples.length > 0) {
                        videoFrameRecorder.record(capturedFrame);
                    }
                    /////////////////////////////////////
                }
                /////////////////////////////////////
            }
            /////////////////////////////////
        } catch (Exception e) {
            // ignore
            //logger.warn("RemoteStreamService.run.Exception", e);
        } finally {
            try {
                if (videoFrameRecorder != null) {
                    videoFrameRecorder.stop();
                    videoFrameRecorder.release();
                }

                if (audioFrameRecorder != null) {
                    audioFrameRecorder.stop();
                    audioFrameRecorder.release();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        //logger.info("[RemoteStreamService] STOPPING...");
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public String getDashUnitId() {
        return dashUnitId;
    }
    ///////////////////////////////////////////////////////////////////////////

}
