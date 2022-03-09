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
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.FileManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class RemoteCameraService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(RemoteCameraService.class);

    private static final String INIT_SEGMENT_POSTFIX = "_init$RepresentationID$.m4s";
    private static final String MEDIA_SEGMENT_POSTFIX = "_chunk$RepresentationID$_$Number%05d$.m4s";

    private final ConfigManager configManager;

    private final String dashUnitId;

    private final String URI_FILE_NAME;
    private final String RTMP_PATH;
    private final String DASH_PATH;
    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 1280;
    public static final int CAPTURE_HEIGHT = 720;
    public static final int GOP_LENGTH_IN_FRAMES = 30;

    private final String SUBTITLE;
    public static final String V_SIZE_1 = "960x540";
    public static final String V_SIZE_2 = "416x234";
    public static final String V_SIZE_3 = "640x360";
    public static final String V_SIZE_4 = "768x432";
    public static final String V_SIZE_5 = "1280x720";
    public static final String V_SIZE_6 = "1920x1080";

    private static long startTime = 0;

    private boolean exit = false;
    private boolean isFinished = false;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public RemoteCameraService(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted,
                               String dashUnitId, ConfigManager configManager, String uriFileName, String rtmpPath, String dashPath) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = dashUnitId;
        this.configManager = configManager;
        URI_FILE_NAME = uriFileName;
        RTMP_PATH = rtmpPath;
        DASH_PATH = dashPath;
        SUBTITLE = "> REMOTE (" + URI_FILE_NAME + ")";
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // FOR TEST
    public RemoteCameraService(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted,
                               ConfigManager configManager) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = null;
        this.configManager = configManager;
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
        SUBTITLE = "TEST";
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // FOR TEST
    public RemoteCameraService(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = null;
        this.configManager = new ConfigManager("/Users/jamesj/GIT_PROJECTS/JDASH/src/main/resources/config/user_conf.ini");
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
        SUBTITLE = "TEST";
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        logger.info("[RemoteCameraService] RUNNING...");
        logger.debug("[RemoteCameraService] RTMP_PATH=[{}], DASH_PATH=[{}]", RTMP_PATH, DASH_PATH);

        try {
            /////////////////////////////////
            // [INPUT] FFmpegFrameGrabber
            FrameGrabber frameGrabber = FFmpegFrameGrabber.createDefault(RTMP_PATH);
            //FrameGrabber frameGrabber = new OpenCVFrameGrabber(RTMP_PATH);
            frameGrabber.setImageWidth(CAPTURE_WIDTH);
            frameGrabber.setImageHeight(CAPTURE_HEIGHT);
            frameGrabber.start();
            /////////////////////////////////

            /////////////////////////////////
            // [OUTPUT] FFmpegFrameRecorder
            FFmpegFrameRecorder fFmpegFrameRecorder = new FFmpegFrameRecorder(
                    DASH_PATH,
                    CAPTURE_WIDTH, CAPTURE_HEIGHT,
                    AudioService.CHANNEL_NUM // (audioChannels > 0: not record / 1: record)
            );
            fFmpegFrameRecorder.setInterleaved(true);
            setVideoOptions(fFmpegFrameRecorder);
            setAudioOptions(fFmpegFrameRecorder);
            fFmpegFrameRecorder.start();
            /////////////////////////////////

            /////////////////////////////////
            // [GRAB FRAME]
            CanvasFrame cameraFrame = null;
            if (configManager.isEnableClient()) {
                cameraFrame = new CanvasFrame("[REMOTE] Live stream", CanvasFrame.getDefaultGamma() / frameGrabber.getGamma());
            }

            OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();
            Mat mat;
            Point point = new Point(15, 65);
            Scalar scalar = new Scalar(0, 200, 255, 0);
            long curTimeStamp;

            while (!exit) {
                Frame capturedFrame = frameGrabber.grab();
                if(capturedFrame == null){
                    continue;
                }

                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                }

                // Check for AV drift
                curTimeStamp = 1000 * (System.currentTimeMillis() - startTime);
                if (curTimeStamp > fFmpegFrameRecorder.getTimestamp()) { // Lip-flap correction
                    fFmpegFrameRecorder.setTimestamp(curTimeStamp);
                }

                // TODO : 영상은 정상인데, 음성은 계속 앞에 재생되던 사운드가 Loopback 되는 현상 발생 중
                if (capturedFrame.image != null && capturedFrame.samples != null) {
                    fFmpegFrameRecorder.record(capturedFrame);
                    if (cameraFrame != null && cameraFrame.isVisible()) {
                        cameraFrame.showImage(capturedFrame);
                    }
                    logger.debug("[@ INTERLEAVED @] FRAME: {} {}", capturedFrame.timestamp, capturedFrame.getTypes());
                } else if (capturedFrame.image != null) {
                    mat = openCVConverter.convertToMat(capturedFrame);
                    opencv_imgproc.putText(mat, SUBTITLE, point, opencv_imgproc.CV_FONT_VECTOR0, 0.8, scalar, 1, 0, false);
                    capturedFrame = openCVConverter.convert(mat);
                    fFmpegFrameRecorder.record(capturedFrame);
                    if (cameraFrame != null && cameraFrame.isVisible()) {
                        cameraFrame.showImage(capturedFrame);
                    }
                } else if (capturedFrame.samples != null) {
                    fFmpegFrameRecorder.record(capturedFrame);
                }
            }
            /////////////////////////////////

            frameGrabber.stop();
            frameGrabber.release();
            fFmpegFrameRecorder.stop();
            fFmpegFrameRecorder.release();

            FileManager.deleteFile(FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath()));
        } catch (Exception e) {
            FFmpegLogCallback.set();
            logger.warn("RemoteCameraService.run.Exception", e);
        }

        isFinished = true;
        logger.info("[RemoteCameraService] STOPPING...");
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public void finish() {
        exit = true;
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public String getDashUnitId() {
        return dashUnitId;
    }

    public boolean isFinished() {
        return isFinished;
    }

    private void setVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setFormat("dash");
        fFmpegFrameRecorder.setVideoBitrate(2000000);
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE);
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        //fFmpegFrameRecorder.setFormat("matroska"); // > H265
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);

        // -map v:0 -s:0 $V_SIZE_1 -b:v:0 2M -maxrate:0 2.14M -bufsize:0 3.5M
        /*fFmpegFrameRecorder.setOption("map", "v:0");
        fFmpegFrameRecorder.setOption("s:0", V_SIZE_1);
        fFmpegFrameRecorder.setOption("b:v:0", "2M");
        fFmpegFrameRecorder.setOption("maxrate:0", "2.14M");
        fFmpegFrameRecorder.setOption("bufsize:0", "3.5M");*/

        // -map v:0 -s:1 $V_SIZE_2 -b:v:1 145k -maxrate:1 155k -bufsize:1 220k
        /*fFmpegFrameRecorder.setOption("map", "v:0");
        fFmpegFrameRecorder.setOption("s:1", V_SIZE_2);
        fFmpegFrameRecorder.setOption("b:v:1", "145K");
        fFmpegFrameRecorder.setOption("maxrate:1", "155k");
        fFmpegFrameRecorder.setOption("bufsize:1", "220k");*/

        // -map v:0 -s:2 $V_SIZE_3 -b:v:2 50K -maxrate:2 1M -bufsize:2 2M
        /*fFmpegFrameRecorder.setOption("map", "v:0");
        fFmpegFrameRecorder.setOption("s:2", V_SIZE_3);
        fFmpegFrameRecorder.setOption("b:v:2", "50K");
        fFmpegFrameRecorder.setOption("maxrate:2", "1M");
        fFmpegFrameRecorder.setOption("bufsize:2", "2M");*/

        // -map v:0 -s:3 $V_SIZE_4 -b:v:3 730k -maxrate:3 781k -bufsize:3 1278k
        /*fFmpegFrameRecorder.setOption("map", "v:0");
        fFmpegFrameRecorder.setOption("s:3", V_SIZE_4);
        fFmpegFrameRecorder.setOption("b:v:3", "730k");
        fFmpegFrameRecorder.setOption("maxrate:3", "781k");
        fFmpegFrameRecorder.setOption("bufsize:3", "1278k");*/

        fFmpegFrameRecorder.setOption("init_seg_name", URI_FILE_NAME + INIT_SEGMENT_POSTFIX);
        fFmpegFrameRecorder.setOption("media_seg_name", URI_FILE_NAME + MEDIA_SEGMENT_POSTFIX);
    }

    private void setAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        fFmpegFrameRecorder.setAudioOption("crf", "0");
        fFmpegFrameRecorder.setAudioQuality(0);
        fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE);
        fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
        fFmpegFrameRecorder.setAudioBitrate(192000);
    }
    ///////////////////////////////////////////////////////////////////////////

}
