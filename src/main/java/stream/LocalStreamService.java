package stream;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.dynamic.PreProcessMediaManager;
import dash.server.dynamic.message.PreLiveMediaProcessRequest;
import dash.server.dynamic.message.base.MessageHeader;
import dash.server.dynamic.message.base.MessageType;
import network.definition.DestinationRecord;
import network.socket.GroupSocket;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;
import util.module.FileManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;

public class LocalStreamService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(LocalStreamService.class);

    private final ScheduleManager scheduleManager;
    private static final String LOCAL_STREAM_SCHEDULE_KEY = "LOCAL_STREAM_SCHEDULE_KEY";
    private final ConcurrentCyclicFIFO<Frame> frameQueue = new ConcurrentCyclicFIFO<>();
    private CameraCanvasController localCameraCanvasController = null;

    protected static final int CAMERA_INDEX = 0;
    //protected static final int MIKE_INDEX = 4;

    private final String URI;

    private final ConfigManager configManager;

    private OpenCVFrameGrabber openCVFrameGrabber = null;
    private final AudioService audioService = new AudioService();

    private static long startTime = 0;
    private boolean isPreMediaReqSent = false;
    private boolean exit = false;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public LocalStreamService(ScheduleManager scheduleManager,
                              String name,
                              int initialDelay, int interval, TimeUnit timeUnit,
                              int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.scheduleManager = scheduleManager;
        this.configManager = AppInstance.getInstance().getConfigManager();

        if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
            String networkPath = StreamConfigManager.RTMP_PREFIX + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
            URI = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
            String uriFileName = FileManager.getFileNameFromUri(configManager.getCameraPath());
            String uri = FileManager.concatFilePath(configManager.getCameraPath(), uriFileName + StreamConfigManager.DASH_POSTFIX);
            URI = FileManager.concatFilePath(configManager.getMediaBasePath(), uri);
        } else {
            URI = null;
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public boolean start() {
        try {
            audioService.initSampleService(scheduleManager);

            /////////////////////////////////
            // [INPUT] OpenCVFrameGrabber
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                if (!configManager.isAudioOnly()) {
                    openCVFrameGrabber = new OpenCVFrameGrabber(CAMERA_INDEX);
                    openCVFrameGrabber.setImageWidth(StreamConfigManager.CAPTURE_WIDTH);
                    openCVFrameGrabber.setImageHeight(StreamConfigManager.CAPTURE_HEIGHT);
                    openCVFrameGrabber.start();
                }
            } else {
                openCVFrameGrabber = new OpenCVFrameGrabber(CAMERA_INDEX);
                openCVFrameGrabber.setImageWidth(StreamConfigManager.CAPTURE_WIDTH);
                openCVFrameGrabber.setImageHeight(StreamConfigManager.CAPTURE_HEIGHT);
                openCVFrameGrabber.start();
            }
            /////////////////////////////////

            /////////////////////////////////
            // [OUTPUT] LOCAL CAMERA CANVAS CONTROLLER
            if (configManager.isEnableGui()
                    && openCVFrameGrabber != null
                    && !configManager.isAudioOnly()) {
                if (scheduleManager.initJob(LOCAL_STREAM_SCHEDULE_KEY, 1, 1)) {
                    logger.debug("[LocalStreamService] Success to init [{}]", LOCAL_STREAM_SCHEDULE_KEY);

                    localCameraCanvasController = new CameraCanvasController(
                            scheduleManager,
                            CameraCanvasController.class.getSimpleName(),
                            0, 1, TimeUnit.MILLISECONDS,
                            1, 1, true,
                            true, frameQueue, openCVFrameGrabber.getGamma()
                    );
                    scheduleManager.startJob(LOCAL_STREAM_SCHEDULE_KEY, localCameraCanvasController);
                }
            }
            /////////////////////////////////
        } catch (Exception e) {
            logger.warn("LocalStreamService.start.Exception", e);
            System.exit(1);
        }

        logger.debug("[LocalStreamService] [START] URI=[{}]", URI);
        return URI != null;
    }

    public void stop() {
        exit = true;

        try {
            if (localCameraCanvasController != null) {
                scheduleManager.stopJob(LOCAL_STREAM_SCHEDULE_KEY, localCameraCanvasController);
                localCameraCanvasController = null;
            }

            if (openCVFrameGrabber != null) {
                openCVFrameGrabber.close();
                openCVFrameGrabber = null;
            }

            String mpdParentPath = FileManager.getParentPathFromUri(URI);
            if (mpdParentPath != null) {
                FileManager.deleteFile(mpdParentPath);
                logger.debug("[LocalStreamService] DELETE ALL MPD Files. (path={})", mpdParentPath);
            }
        } catch (Exception e) {
            logger.warn("LocalStreamService.stop.Exception", e);
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        logger.info("[LocalStreamService] RUNNING...");

        FFmpegFrameRecorder fFmpegFrameRecorder = null;
        try {
            /////////////////////////////////
            // DASH 스트리밍일 경우 (비디오, 오디오 선택 포함)
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                if (configManager.isAudioOnly()) {
                    fFmpegFrameRecorder = new FFmpegFrameRecorder(
                            URI,
                            AudioService.CHANNEL_NUM
                    );
                } else {
                    fFmpegFrameRecorder = new FFmpegFrameRecorder(
                            URI,
                            StreamConfigManager.CAPTURE_WIDTH, StreamConfigManager.CAPTURE_HEIGHT,
                            AudioService.CHANNEL_NUM
                    );
                    StreamConfigManager.setLocalStreamVideoOptions(fFmpegFrameRecorder);
                }

                String mpdParentPath = FileManager.getParentPathFromUri(URI);
                File mpdParentFile = new File(mpdParentPath);
                if (!mpdParentFile.exists()) {
                    if (mpdParentFile.mkdirs()) {
                        logger.debug("[LocalStreamService] Parent mpd path is created. (mpdParentPath={})", mpdParentPath);
                    }
                }
                StreamConfigManager.setDashOptions(fFmpegFrameRecorder,
                        FileManager.getFileNameFromUri(URI),
                        configManager.isAudioOnly(),
                        configManager.getSegmentDuration(), configManager.getWindowSize()
                );
            }
            // RTMP 스트리밍일 경우 (비디오, 오디오 모두 포함)
            else {
                fFmpegFrameRecorder = new FFmpegFrameRecorder(
                        URI,
                        StreamConfigManager.CAPTURE_WIDTH, StreamConfigManager.CAPTURE_HEIGHT,
                        AudioService.CHANNEL_NUM
                );
                StreamConfigManager.setLocalStreamVideoOptions(fFmpegFrameRecorder);
            }
            StreamConfigManager.setLocalStreamAudioOptions(fFmpegFrameRecorder);

            avutil.av_log_set_level(AV_LOG_ERROR);
            fFmpegFrameRecorder.start();
            /////////////////////////////////

            /////////////////////////////////
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)
                    && configManager.isAudioOnly()) {
                //TimeUnit timeUnit = TimeUnit.MILLISECONDS;
                while(audioService.record(fFmpegFrameRecorder)) {
                    try {
                        if (startTime == 0) {
                            startTime = System.currentTimeMillis();
                        } else {
                            if (!isPreMediaReqSent) {
                                if ((System.currentTimeMillis() - startTime)
                                        >= configManager.getPreprocessInitIdleTime()) {
                                    sendPreLiveMediaProcessRequest();
                                    isPreMediaReqSent = true;
                                }
                            }
                        }

                        // Check for AV drift
                        long audioTs = 1000 * (System.currentTimeMillis() - startTime);
                        if (audioTs > fFmpegFrameRecorder.getTimestamp()) {
                            fFmpegFrameRecorder.setTimestamp(audioTs);
                        }

                        //timeUnit.sleep(1);
                    } catch (Exception e) {
                        //logger.warn("");
                    }
                }
            } else {
                audioService.startSampling(fFmpegFrameRecorder);

                if (openCVFrameGrabber != null) {
                    Frame capturedFrame;

                    while ((capturedFrame = openCVFrameGrabber.grab()) != null) {
                        if (exit) {
                            break;
                        }

                        if (startTime == 0) {
                            startTime = System.currentTimeMillis();
                        } else {
                            if (!isPreMediaReqSent) {
                                if ((System.currentTimeMillis() - startTime)
                                        >= configManager.getPreprocessInitIdleTime()) {
                                    sendPreLiveMediaProcessRequest();
                                    isPreMediaReqSent = true;
                                }
                            }
                        }

                        // Check for AV drift
                        long videoTS = 1000 * (System.currentTimeMillis() - startTime);
                        if (videoTS > fFmpegFrameRecorder.getTimestamp()) {
                            fFmpegFrameRecorder.setTimestamp(videoTS);
                        }

                        fFmpegFrameRecorder.record(capturedFrame);
                        if (localCameraCanvasController != null) {
                            frameQueue.offer(capturedFrame);
                        }
                    }
                }
            }
            /////////////////////////////////
        } catch (Exception e) {
            // ignore
            //logger.warn("LocalStreamService.run.Exception", e);
        } finally {
            try {
                audioService.releaseOutputResource();

                if (fFmpegFrameRecorder != null) {
                    fFmpegFrameRecorder.stop();
                    fFmpegFrameRecorder.release();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        logger.info("[LocalStreamService] STOPPING...");
    }
    ///////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private void sendPreLiveMediaProcessRequest () {
        DashServer dashServer = ServiceManager.getInstance().getDashServer();
        PreProcessMediaManager preProcessMediaManager = dashServer.getPreProcessMediaManager();
        GroupSocket listenSocket = preProcessMediaManager.getLocalGroupSocket();
        if (listenSocket != null) {
            DestinationRecord target = listenSocket.getDestination(preProcessMediaManager.getSessionId());
            if (target != null) {
                ConfigManager configManager = AppInstance.getInstance().getConfigManager();

                PreLiveMediaProcessRequest preLiveMediaProcessRequest = new PreLiveMediaProcessRequest(
                        new MessageHeader(
                                PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                MessageType.PREPROCESS_REQ,
                                dashServer.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                System.currentTimeMillis(),
                                PreLiveMediaProcessRequest.MIN_SIZE + configManager.getCameraPath().length()
                        ),
                        configManager.getPreprocessListenIp().length(),
                        configManager.getPreprocessListenIp(),
                        configManager.getCameraPath().length(),
                        configManager.getCameraPath(),
                        0
                );
                byte[] requestByteData = preLiveMediaProcessRequest.getByteData();
                target.getNettyChannel().sendData(requestByteData, requestByteData.length);
                logger.debug("[LocalStreamService] SEND PreLiveMediaProcessRequest={}", preLiveMediaProcessRequest);
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

}
