package cam;

import config.ConfigManager;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.red5.server.net.rtmp.codec.RTMP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.module.FileManager;

import java.net.URI;

public class RemoteCameraService extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(RemoteCameraService.class);

    private static final String INIT_SEGMENT_POSTFIX = "_init$RepresentationID$.m4s";
    private static final String MEDIA_SEGMENT_POSTFIX = "_chunk$RepresentationID$_$Number%05d$.m4s";

    private final ConfigManager configManager;
    private FFmpegFrameRecorder fFmpegFrameRecorder = null;

    private final String URI_FILE_NAME;
    private final String RTMP_PATH;
    private final String DASH_PATH;
    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 1280;
    public static final int CAPTURE_HEIGHT = 720;
    public static final int GOP_LENGTH_IN_FRAMES = 30;

    private boolean exit = false;
    private boolean isFinished = false;

    public RemoteCameraService(ConfigManager configManager, String uriFileName, String rtmpPath, String dashPath) {
        this.configManager = configManager;
        URI_FILE_NAME = uriFileName;
        RTMP_PATH = rtmpPath;
        DASH_PATH = dashPath;
    }

    // FOR TEST
    public RemoteCameraService(ConfigManager configManager) {
        this.configManager = configManager;
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
    }

    // FOR TEST
    public RemoteCameraService() {
        configManager = new ConfigManager("/Users/jamesj/GIT_PROJECTS/JDASH/src/main/resources/config/user_conf.ini");
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
    }

    @Override
    public void run() {
        try {
            FFmpegFrameGrabber fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(RTMP_PATH);
            fFmpegFrameGrabber.setImageWidth(CAPTURE_WIDTH);
            fFmpegFrameGrabber.setImageHeight(CAPTURE_HEIGHT);
            fFmpegFrameGrabber.start();

            FFmpegFrameRecorder fFmpegFrameRecorder = new FFmpegFrameRecorder(DASH_PATH, CAPTURE_WIDTH, CAPTURE_HEIGHT, 1); // TODO : 화질 커스텀
            // (audioChannels > 0: not record / 1: record)
            fFmpegFrameRecorder.setInterleaved(true);
            fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
            fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
            fFmpegFrameRecorder.setVideoOption("crf", "28");
            fFmpegFrameRecorder.setVideoBitrate(2000000);
            //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);
            fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            //fFmpegFrameRecorder.setFormat("matroska"); // > H265
            fFmpegFrameRecorder.setFormat("dash");
            fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
            fFmpegFrameRecorder.setFrameRate(FRAME_RATE);
            fFmpegFrameRecorder.setOption("init_seg_name", FileManager.concatFilePath(URI_FILE_NAME, INIT_SEGMENT_POSTFIX));
            fFmpegFrameRecorder.setOption("media_seg_name", FileManager.concatFilePath(URI_FILE_NAME, MEDIA_SEGMENT_POSTFIX));

            FFmpegLogCallback.set();
            fFmpegFrameRecorder.start();

            CanvasFrame cameraFrame = null;
            if (configManager.isEnableClient()) {
                cameraFrame = new CanvasFrame("[REMOTE] Live stream", CanvasFrame.getDefaultGamma() / fFmpegFrameGrabber.getGamma());
            }

            while (!exit) {
                Frame frame = fFmpegFrameGrabber.grab();
                if(frame == null){
                    continue;
                }

                fFmpegFrameRecorder.record(frame);

                if (cameraFrame != null && cameraFrame.isVisible()) {
                    cameraFrame.showImage(frame);
                }
            }

            fFmpegFrameGrabber.stop();
            fFmpegFrameGrabber.release();
            fFmpegFrameRecorder.stop();
            fFmpegFrameRecorder.release();
        } catch (Exception e) {
            logger.warn("RemoteCameraService.run.Exception", e);
        }

        isFinished = true;
    }

    public void finish() {
        exit = true;
    }

    public boolean isFinished() {
        return isFinished;
    }
}
