package stream;

import config.ConfigManager;
import dash.DashManager;
import dash.dynamic.PreProcessMediaManager;
import dash.dynamic.message.PreLiveMediaProcessRequest;
import dash.dynamic.message.base.MessageHeader;
import dash.dynamic.message.base.MessageType;
import network.definition.DestinationRecord;
import network.socket.GroupSocket;
import org.bytedeco.ffmpeg.global.avcodec;
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

import java.util.concurrent.TimeUnit;

public class LocalStreamService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(LocalStreamService.class);

    private final ScheduleManager scheduleManager;
    private static final String LOCAL_STREAM_SCHEDULE_KEY = "LOCAL_STREAM_SCHEDULE_KEY";
    private final ConcurrentCyclicFIFO<Frame> frameQueue = new ConcurrentCyclicFIFO<>();
    private CameraCanvasController localCameraCanvasController = null;

    protected static final int CAMERA_INDEX = 0;
    protected static final int MIKE_INDEX = 4;

    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 640;
    public static final int CAPTURE_HEIGHT = 320;
    public static final int GOP_LENGTH_IN_FRAMES = 20;
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

        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        URI = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public void start() {
        try {
            audioService.initSampleService();

            openCVFrameGrabber = new OpenCVFrameGrabber(CAMERA_INDEX);
            openCVFrameGrabber.setImageWidth(CAPTURE_WIDTH);
            openCVFrameGrabber.setImageHeight(CAPTURE_HEIGHT);
            openCVFrameGrabber.start();

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
        } catch (Exception e) {
            logger.warn("LocalStreamService.start.Exception", e);
            System.exit(1);
        }

        logger.debug("[LocalStreamService] [START] URI=[{}]", URI);
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
        } catch (Exception e) {
            logger.warn("LocalStreamService.run.finally.Exception", e);
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        logger.info("[LocalStreamService] RUNNING...");

        FFmpegFrameRecorder videoFrameRecorder = null;
        try {
            /////////////////////////////////
            videoFrameRecorder = new FFmpegFrameRecorder(
                    URI,
                    CAPTURE_WIDTH, CAPTURE_HEIGHT,
                    AudioService.CHANNEL_NUM
            );
            setVideoOptions(videoFrameRecorder);
            setAudioOptions(videoFrameRecorder);
            videoFrameRecorder.start();
            audioService.startSampling(videoFrameRecorder, FRAME_RATE);
            /////////////////////////////////

            /////////////////////////////////
            if (openCVFrameGrabber != null) {
                Frame capturedFrame;

                while ((capturedFrame = openCVFrameGrabber.grab()) != null) {
                    if (exit) { break; }

                    if (startTime == 0) {
                        startTime = System.currentTimeMillis();
                    } else {
                        if (!isPreMediaReqSent) {
                            if ((System.currentTimeMillis() - startTime) >= configManager.getPreprocessInitIdleTime()) {
                                sendPreLiveMediaProcessRequest();
                                isPreMediaReqSent = true;
                            }
                        }
                    }

                    // Check for AV drift
                    long videoTS = 1000 * (System.currentTimeMillis() - startTime);
                    if (videoTS > videoFrameRecorder.getTimestamp()) {
                        videoFrameRecorder.setTimestamp(videoTS);
                    }

                    videoFrameRecorder.record(capturedFrame);
                    if (localCameraCanvasController != null) { frameQueue.offer(capturedFrame); }
                }
            }
            /////////////////////////////////
        } catch (Exception e) {
            // ignore
            //logger.warn("LocalStreamService.run.Exception", e);
        } finally {
            try {
                audioService.releaseOutputResource();

                if (videoFrameRecorder != null) {
                    videoFrameRecorder.stop();
                    videoFrameRecorder.release();
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
        DashManager dashManager = ServiceManager.getInstance().getDashManager();
        PreProcessMediaManager preProcessMediaManager = dashManager.getPreProcessMediaManager();
        GroupSocket listenSocket = preProcessMediaManager.getLocalGroupSocket();
        if (listenSocket != null) {
            DestinationRecord target = listenSocket.getDestination(preProcessMediaManager.getSessionId());
            if (target != null) {
                ConfigManager configManager = AppInstance.getInstance().getConfigManager();

                PreLiveMediaProcessRequest preLiveMediaProcessRequest = new PreLiveMediaProcessRequest(
                        new MessageHeader(
                                PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                MessageType.PREPROCESS_REQ,
                                dashManager.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                System.currentTimeMillis(),
                                PreLiveMediaProcessRequest.MIN_SIZE + configManager.getCameraPath().length()
                        ),
                        configManager.getPreprocessListenIp().length(),
                        configManager.getPreprocessListenIp(),
                        configManager.getCameraPath().length(),
                        configManager.getCameraPath(),
                        1800
                );
                byte[] requestByteData = preLiveMediaProcessRequest.getByteData();
                target.getNettyChannel().sendData(requestByteData, requestByteData.length);
                logger.debug("[LocalStreamService] SEND PreLiveMediaProcessRequest={}", preLiveMediaProcessRequest);
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private void setVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setVideoBitrate(2000000); // 2000K > default: 400000 (400K)
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");

        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);

        /**
         * 1. Flash Video (.flv)
         * - Owner : Adobe
         * [Video] : Sorenson H.263 (Flash v6, v7), VP6 (Flash v8), Screen video, H.264
         * [Audio] : MP3, ADPCM, Linear PCM, Nellymoser, Speex, AAC, G.711
         */
        fFmpegFrameRecorder.setFormat("flv");
        /**
         * 2. Matroska (wp, .mkv/.mka/.mks)
         * - Owner : CoreCodec
         * [Video] : H.264, Realvideo, DivX, XviD, HEVC
         * [Audio] : AAC, Vorbis, Dolby AC3, MP3
         */
        //fFmpegFrameRecorder.setFormat("matroska");

        /**
         * The range of the CRF scale is 0–51,
         *      where 0 is lossless (for 8 bit only, for 10 bit use -qp 0),
         *      23 is the default, and 51 is worst quality possible.
         * A lower value generally leads to higher quality,
         *      and a subjectively sane range is 17–28.
         * Consider 17 or 18 to be visually lossless or nearly so;
         *      it should look the same or nearly the same as the input but it isn't technically lossless.
         */
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE); // default: 30
        fFmpegFrameRecorder.setOption("keyint_min", String.valueOf(GOP_LENGTH_IN_FRAMES));
    }

    private void setAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        //fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AC3);

        fFmpegFrameRecorder.setAudioOption("tune", "zerolatency");
        fFmpegFrameRecorder.setAudioOption("preset", "ultrafast");
        fFmpegFrameRecorder.setAudioOption("crf", "18");
        fFmpegFrameRecorder.setAudioQuality(0);
        fFmpegFrameRecorder.setAudioBitrate(192000); // 192K > default: 64000 (64K)
        fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE); // default: 44100
        //fFmpegFrameRecorder.setSampleRate(48000); // FOR AC3
        fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
    }
    ////////////////////////////////////////////////////////////////////////////////

}
