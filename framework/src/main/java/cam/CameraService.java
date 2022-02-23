package cam;

import config.ConfigManager;
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
import util.module.FileManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CameraService {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(CameraService.class);

    protected static final int CAMERA_INDEX = 0;
    protected static final int MIKE_INDEX = 4;

    protected FrameGrabber grabber;
    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 1280;
    public static final int CAPTURE_HEIGHT = 720;
    public static final int GOP_LENGTH_IN_FRAMES = 30;
    private final String URI;

    private final OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();
    private FFmpegFrameRecorder fFmpegFrameRecorder = null;
    private final AudioService audioService = new AudioService();

    private static long startTime = 0;
    private boolean alive = true;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public CameraService() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        //URI = FileManager.concatFilePath(configManager.getCameraMp4Path(), "cam_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".mp4");
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        URI = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void initOutput() throws Exception {
        fFmpegFrameRecorder = new FFmpegFrameRecorder(
                URI,
                CAPTURE_WIDTH,
                CAPTURE_HEIGHT,
                AudioService.CHANNEL_NUM
        );

        fFmpegFrameRecorder.setInterleaved(true);
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setVideoBitrate(2000000);
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        //fFmpegFrameRecorder.setFormat("matroska"); // > H265
        fFmpegFrameRecorder.setFormat("flv"); // > H264
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE);

        audioService.setRecorderParams(fFmpegFrameRecorder);
        audioService.initSampleService();
        fFmpegFrameRecorder.start();
        audioService.startSampling(FRAME_RATE);
        ///////////////////////////////////////////////

        ///////////////////
        // TODO : TEST
        /*ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        RtmpClient rtmpClient = new RtmpClient(
                configManager.getCameraPath(),
                FileManager.concatFilePath("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources", configManager.getCameraPath())
        );
        rtmpClient.start();*/
        ///////////////////
    }

    public void output(Frame frame) throws Exception {
        fFmpegFrameRecorder.record(frame);
    }

    public void releaseOutputResource() throws Exception {
        audioService.releaseOutputResource();
        fFmpegFrameRecorder.close();
    }

    protected int getInterval() {
        return (int) (1000 / FRAME_RATE);
    }

    protected void instanceGrabber() {
        grabber = new OpenCVFrameGrabber(CAMERA_INDEX);
    }

    protected void initGrabber() throws Exception {
        instanceGrabber();
        grabber.setImageWidth(CAPTURE_WIDTH);
        grabber.setImageHeight(CAPTURE_HEIGHT);
        grabber.start();
    }

    private void process() {
        try {
            final CanvasFrame cameraFrame = new CanvasFrame("Live stream", CanvasFrame.getDefaultGamma() / grabber.getGamma());

            Frame capturedFrame;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Mat mat;
            Point point = new Point(15, 35);

            while ((capturedFrame = grabber.grab()) != null) {
                mat = openCVConverter.convertToMat(capturedFrame);
                opencv_imgproc.putText(mat, simpleDateFormat.format(new Date()), point, opencv_imgproc.CV_FONT_VECTOR0, 0.8, new Scalar(0, 200, 255, 0), 1, 0, false);
                capturedFrame = openCVConverter.convert(mat);

                if (alive && cameraFrame.isVisible()) {
                    cameraFrame.showImage(capturedFrame);
                }

                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }

                long videoTS = 1000 * (System.currentTimeMillis() - startTime);

                // Check for AV drift
                if (videoTS > fFmpegFrameRecorder.getTimestamp()) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Lip-flap correction: [{}] : [{}] -> [{}]",
                                videoTS, fFmpegFrameRecorder.getTimestamp(),
                                (videoTS - fFmpegFrameRecorder.getTimestamp())
                        );
                    }
                    fFmpegFrameRecorder.setTimestamp(videoTS);
                }

                if (alive) {
                    fFmpegFrameRecorder.record(capturedFrame);
                }
            }
        } catch (Exception e) {
            logger.warn("CameraService.process.Exception", e);
        }
    }

    private void safeRelease() {
        try {
            releaseOutputResource();
        } catch (Exception e) {
            logger.error("CameraService.safeRelease.Exception", e);
        }

        if (grabber != null) {
            try {
                grabber.close();
            } catch (Exception e) {
                logger.error("CameraService.safeRelease.Exception", e);
            }
        }
    }

    private void init() throws Exception {
        avutil.av_log_set_level(avutil.AV_LOG_INFO);
        FFmpegLogCallback.set();
        initGrabber();
        initOutput();
    }

    public void action() {
        try {
            //////////////////////////////////////
            /*DashManager dashManager = ServiceManager.getInstance().getDashManager();
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
            }*/
            //////////////////////////////////////

            init();
            process();
        } catch (Exception e) {
            logger.error("CameraService.action.Exception", e);
        } finally {
            safeRelease();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    ////////////////////////////////////////////////////////////////////////////////

}