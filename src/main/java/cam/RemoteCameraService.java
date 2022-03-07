package cam;

import config.ConfigManager;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.module.FileManager;

public class RemoteCameraService extends Thread {

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

    private boolean exit = false;
    private boolean isFinished = false;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public RemoteCameraService(String dashUnitId, ConfigManager configManager, String uriFileName, String rtmpPath, String dashPath) {
        this.dashUnitId = dashUnitId;
        this.configManager = configManager;
        URI_FILE_NAME = uriFileName;
        RTMP_PATH = rtmpPath;
        DASH_PATH = dashPath;
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // FOR TEST
    public RemoteCameraService(ConfigManager configManager) {
        this.dashUnitId = null;
        this.configManager = configManager;
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // FOR TEST
    public RemoteCameraService() {
        this.dashUnitId = null;
        this.configManager = new ConfigManager("/Users/jamesj/GIT_PROJECTS/JDASH/src/main/resources/config/user_conf.ini");
        String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
        RTMP_PATH = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
        DASH_PATH = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath() + ".mpd");
        URI_FILE_NAME = FileManager.getFileNameFromUri(DASH_PATH);
    }
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void run() {
        logger.info("[RemoteCameraService] RUNNING...");
        logger.debug("[RemoteCameraService] RTMP_PATH=[{}], DASH_PATH=[{}]", RTMP_PATH, DASH_PATH);

        try {
            /////////////////////////////////
            // [INPUT] FFmpegFrameGrabber
            FFmpegFrameGrabber fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(RTMP_PATH);
            fFmpegFrameGrabber.setImageWidth(CAPTURE_WIDTH);
            fFmpegFrameGrabber.setImageHeight(CAPTURE_HEIGHT);
            fFmpegFrameGrabber.start();
            /////////////////////////////////

            /////////////////////////////////
            // [OUTPUT] FFmpegFrameRecorder
            FFmpegFrameRecorder fFmpegFrameRecorder = new FFmpegFrameRecorder(
                    DASH_PATH,
                    CAPTURE_WIDTH, CAPTURE_HEIGHT, // TODO : 화질 커스텀
                    AudioService.CHANNEL_NUM // (audioChannels > 0: not record / 1: record)
            );
            //fFmpegFrameRecorder.setInterleaved(true);
            fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
            fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
            fFmpegFrameRecorder.setVideoOption("crf", "28");
            fFmpegFrameRecorder.setVideoBitrate(2000000);
            fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            //fFmpegFrameRecorder.setFormat("matroska"); // > H265
            //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);
            fFmpegFrameRecorder.setFormat("dash");
            fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
            fFmpegFrameRecorder.setFrameRate(FRAME_RATE);
            fFmpegFrameRecorder.setOption("init_seg_name", URI_FILE_NAME + INIT_SEGMENT_POSTFIX);
            fFmpegFrameRecorder.setOption("media_seg_name", URI_FILE_NAME + MEDIA_SEGMENT_POSTFIX);

            fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            fFmpegFrameRecorder.setAudioOption("crf", "0");
            fFmpegFrameRecorder.setAudioQuality(0);
            fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE);
            fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
            fFmpegFrameRecorder.setAudioBitrate(192000);

            FFmpegLogCallback.set();
            fFmpegFrameRecorder.start();
            /////////////////////////////////

            /////////////////////////////////
            // [GRAB FRAME]
            CanvasFrame cameraFrame = null;
            if (configManager.isEnableClient()) {
                cameraFrame = new CanvasFrame("[REMOTE] Live stream", CanvasFrame.getDefaultGamma() / fFmpegFrameGrabber.getGamma());
            }

            while (!exit) {
                Frame frame = fFmpegFrameGrabber.grab();
                if(frame == null){
                    continue;
                }

                //long count = 0;
                if (frame.image != null) {
                    //logger.debug("[{}]+VIDEO", count);
                    fFmpegFrameRecorder.record(frame);

                    if (cameraFrame != null && cameraFrame.isVisible()) {
                        cameraFrame.showImage(frame);
                    }
                }

                if (frame.samples != null) {
                    //logger.debug("[{}]+AUDIO", count);
                    fFmpegFrameRecorder.record(frame);
                }

                //count++;
            }
            /////////////////////////////////

            fFmpegFrameGrabber.stop();
            fFmpegFrameGrabber.release();
            fFmpegFrameRecorder.stop();
            fFmpegFrameRecorder.release();

            FileManager.deleteFile(FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath()));
        } catch (Exception e) {
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
    ///////////////////////////////////////////////////////////////////////////

}
