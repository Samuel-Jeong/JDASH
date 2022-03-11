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
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.FileManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class LocalStreamService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(LocalStreamService.class);

    protected static final int CAMERA_INDEX = 0;
    protected static final int MIKE_INDEX = 4;

    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 960;
    public static final int CAPTURE_HEIGHT = 540;
    public static final int GOP_LENGTH_IN_FRAMES = 20;
    private final String URI;

    private final ConfigManager configManager;

    private OpenCVFrameGrabber openCVFrameGrabber = null;
    private final AudioService audioService = new AudioService();

    private static long startTime = 0;
    private boolean isPreMediaReqSent = false;
    private boolean exit = false;

    private final OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();
    private final Point point = new Point(15, 65);
    private final Scalar scalar = new Scalar(0, 200, 255, 0);
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public LocalStreamService(ScheduleManager scheduleManager,
                              String name,
                              int initialDelay, int interval, TimeUnit timeUnit,
                              int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        configManager = AppInstance.getInstance().getConfigManager();

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
        } catch (Exception e) {
            logger.warn("LocalStreamService.start.Exception", e);
            System.exit(1);
        }

        logger.debug("[LocalStreamService] [START] URI=[{}]", URI);
    }

    public void stop() {
        exit = true;

        try {
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
            if (openCVFrameGrabber != null) { // && videoFrameRecorder != null) {
                CanvasFrame cameraFrame = new CanvasFrame("[LOCAL] Live stream", CanvasFrame.getDefaultGamma() / openCVFrameGrabber.getGamma());

                Frame capturedFrame;
                Date curData = new Date();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                while ((capturedFrame = openCVFrameGrabber.grab()) != null) {
                    if (exit) { continue; }

                    if (startTime == 0) {
                        startTime = System.currentTimeMillis();
                    } else {
                        if (!isPreMediaReqSent) {
                            if ((System.currentTimeMillis() - startTime) >= configManager.getPreprocessInitIdleTime()) { // 5초 후에 PLAY 전송
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

                    Mat mat = openCVConverter.convertToMat(capturedFrame);
                    if (mat != null) {
                        curData.setTime(System.currentTimeMillis());
                        opencv_imgproc.putText(mat, simpleDateFormat.format(curData), point, opencv_imgproc.CV_FONT_VECTOR0, 0.8, scalar, 1, 0, false);
                        capturedFrame = openCVConverter.convert(mat);
                    }

                    if (cameraFrame.isVisible()) {
                        cameraFrame.showImage(capturedFrame);
                    }

                    videoFrameRecorder.record(capturedFrame);
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
                logger.debug("[CameraService] SEND PreLiveMediaProcessRequest={}", preLiveMediaProcessRequest);
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private void setVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setVideoBitrate(5000000); // default: 400000
        fFmpegFrameRecorder.setFormat("flv"); // > H264
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE); // default: 30
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        fFmpegFrameRecorder.setOption("keyint_min", String.valueOf(GOP_LENGTH_IN_FRAMES));
        //fFmpegFrameRecorder.setFormat("matroska"); // > H265
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);
    }

    private void setAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setAudioOption("tune", "zerolatency");
        fFmpegFrameRecorder.setAudioOption("preset", "ultrafast");
        fFmpegFrameRecorder.setAudioOption("crf", "18");
        fFmpegFrameRecorder.setAudioQuality(0);
        fFmpegFrameRecorder.setAudioBitrate(96000); // default: 64000
        fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE); // default: 44100
        fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
        fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
    }
    ////////////////////////////////////////////////////////////////////////////////

}
