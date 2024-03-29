package stream;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.dynamic.DynamicMediaManager;
import dash.server.dynamic.message.StreamingStartRequest;
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
import service.scheduler.job.JobBuilder;
import service.scheduler.job.JobContainer;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;
import util.module.FileManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;

public class LocalStreamService extends JobContainer {

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
    private final FileManager fileManager = new FileManager();

    private OpenCVFrameGrabber openCVFrameGrabber = null;
    private final AudioService audioService = new AudioService();

    private static long startTime = 0;
    private boolean isPreMediaReqSent = false;
    private boolean exit = false;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public LocalStreamService(Job localStreamServiceJob) {
        setJob(localStreamServiceJob);
        this.scheduleManager = localStreamServiceJob.getScheduleManager();
        this.configManager = AppInstance.getInstance().getConfigManager();

        if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
            String networkPath = StreamConfigManager.RTMP_PREFIX + configManager.getRtmpServerIp() + ":" + configManager.getRtmpServerPort();
            URI = fileManager.concatFilePath(networkPath, configManager.getCameraPath());
        } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
            String uriFileName = fileManager.getFileNameFromUri(configManager.getCameraPath());
            String uri = fileManager.concatFilePath(configManager.getCameraPath(), uriFileName + StreamConfigManager.DASH_POSTFIX);
            URI = fileManager.concatFilePath(configManager.getMediaBasePath(), uri);
        } else {
            URI = null;
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public boolean init() {
        try {
            audioService.initSampleService(scheduleManager);

            /////////////////////////////////
            // [INPUT] OpenCVFrameGrabber
            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                if (!configManager.isAudioOnly()) {
                    openCVFrameGrabber = new OpenCVFrameGrabber(CAMERA_INDEX);
                    openCVFrameGrabber.setImageWidth(configManager.getLocalVideoWidth());
                    openCVFrameGrabber.setImageHeight(configManager.getLocalVideoHeight());
                    openCVFrameGrabber.start();
                }
            } else {
                openCVFrameGrabber = new OpenCVFrameGrabber(CAMERA_INDEX);
                openCVFrameGrabber.setImageWidth(configManager.getLocalVideoWidth());
                openCVFrameGrabber.setImageHeight(configManager.getRemoteVideoHeight());
                openCVFrameGrabber.start();
            }
            /////////////////////////////////

            /////////////////////////////////
            // [OUTPUT] LOCAL CAMERA CANVAS CONTROLLER
            if (configManager.isEnableGui()
                    && openCVFrameGrabber != null
                    && !configManager.isAudioOnly()) {
                if (scheduleManager.initJob(LOCAL_STREAM_SCHEDULE_KEY, 1, 5)) {
                    logger.debug("[LocalStreamService] Success to init [{}]", LOCAL_STREAM_SCHEDULE_KEY);

                    Job localCameraCanvasControlJob = new JobBuilder()
                            .setScheduleManager(scheduleManager)
                            .setName(CameraCanvasController.class.getSimpleName())
                            .setInitialDelay(0)
                            .setInterval(1)
                            .setTimeUnit(TimeUnit.MILLISECONDS)
                            .setPriority(1)
                            .setTotalRunCount(1)
                            .setIsLasted(true)
                            .build();
                    localCameraCanvasController = new CameraCanvasController(
                            localCameraCanvasControlJob,
                            true, frameQueue, openCVFrameGrabber.getGamma()
                    );
                    localCameraCanvasController.start();
                    if (scheduleManager.startJob(LOCAL_STREAM_SCHEDULE_KEY, localCameraCanvasController.getJob())) {
                        logger.debug("[LocalStreamService] [+RUN] Success to start the local camera.");
                    } else {
                        logger.warn("[LocalStreamService] [-RUN FAIL] Fail to start the local camera.");
                        System.exit(1);
                    }
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
                scheduleManager.stopJob(LOCAL_STREAM_SCHEDULE_KEY, localCameraCanvasController.getJob());
                localCameraCanvasController = null;
            }

            if (openCVFrameGrabber != null) {
                openCVFrameGrabber.close();
                openCVFrameGrabber = null;
            }

            if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                String mpdParentPath = fileManager.getParentPathFromUri(URI);
                if (mpdParentPath != null) {
                    fileManager.deleteFile(mpdParentPath);
                    logger.debug("[LocalStreamService] DELETE ALL MPD Files. (path={})", mpdParentPath);
                }
            }
        } catch (Exception e) {
            logger.warn("LocalStreamService.stop.Exception", e);
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public void start() {
        getJob().setRunnable(() -> {
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
                                configManager.getLocalVideoWidth(), configManager.getLocalVideoHeight(),
                                AudioService.CHANNEL_NUM
                        );
                        StreamConfigManager.setLocalStreamVideoOptions(fFmpegFrameRecorder);
                    }

                    String mpdParentPath = fileManager.getParentPathFromUri(URI);
                    File mpdParentFile = new File(mpdParentPath);
                    if (!mpdParentFile.exists()) {
                        if (mpdParentFile.mkdirs()) {
                            logger.debug("[LocalStreamService] Parent mpd path is created. (mpdParentPath={})", mpdParentPath);
                        }
                    }
                    StreamConfigManager.setDashOptions(fFmpegFrameRecorder,
                            fileManager.getFileNameFromUri(URI),
                            configManager.getSegmentDuration(), configManager.getWindowSize()
                    );
                }
                // RTMP 스트리밍일 경우 (비디오, 오디오 모두 포함)
                else {
                    fFmpegFrameRecorder = new FFmpegFrameRecorder(
                            URI,
                            configManager.getLocalVideoWidth(), configManager.getLocalVideoHeight(),
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
                    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
                    while(audioService.record(fFmpegFrameRecorder)) {
                        try {
                            if (startTime == 0) {
                                startTime = System.currentTimeMillis();
                            } else {
                                if (!isPreMediaReqSent) {
                                    if ((System.currentTimeMillis() - startTime)
                                            >= configManager.getPreprocessInitIdleTime()) {
                                        sendStreamingStartRequest();
                                        isPreMediaReqSent = true;
                                    }
                                }
                            }

                            // Check for AV drift
                            long audioTs = 1000 * (System.currentTimeMillis() - startTime);
                            if (audioTs > fFmpegFrameRecorder.getTimestamp()) {
                                fFmpegFrameRecorder.setTimestamp(audioTs);
                            }

                            timeUnit.sleep(1);
                        } catch (Exception e) {
                            //logger.warn("");
                        }
                    }
                } else {
                    if (!audioService.startSampling(fFmpegFrameRecorder)) { return; }

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
                                        sendStreamingStartRequest();
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
                        openCVFrameGrabber.flush();
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
        });
    }
    ///////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private void sendStreamingStartRequest() {
        DashServer dashServer = ServiceManager.getInstance().getDashServer();
        DynamicMediaManager dynamicMediaManager = dashServer.getDynamicMediaManager();
        GroupSocket listenSocket = dynamicMediaManager.getLocalGroupSocket();
        if (listenSocket != null) {
            DestinationRecord target = listenSocket.getDestination(dynamicMediaManager.getSessionId());
            if (target != null) {
                ConfigManager configManager = AppInstance.getInstance().getConfigManager();

                StreamingStartRequest preLiveMediaProcessRequest = new StreamingStartRequest(
                        new MessageHeader(
                                DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                MessageType.STREAMING_START_REQ,
                                dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                System.currentTimeMillis(),
                                StreamingStartRequest.MIN_SIZE + configManager.getCameraPath().length()
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
