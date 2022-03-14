package stream;

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

import java.util.concurrent.TimeUnit;

public class RemoteStreamService extends Job {

    ///////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(RemoteStreamService.class);

    private static final String INIT_SEGMENT_POSTFIX = "_init$RepresentationID$.m4s";
    private static final String MEDIA_SEGMENT_POSTFIX = "_chunk$RepresentationID$_$Number%05d$.m4s";

    public static final String V_SIZE_1 = "960x540";
    public static final String V_SIZE_2 = "416x234";
    public static final String V_SIZE_3 = "640x360";
    public static final String V_SIZE_4 = "768x432";
    public static final String V_SIZE_5 = "1280x720";
    public static final String V_SIZE_6 = "1920x1080";

    private final ConfigManager configManager;

    private final String dashUnitId;

    private final String URI_FILE_NAME;
    private final String RTMP_PATH;
    private final String DASH_PATH;
    public final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 960;
    public static final int CAPTURE_HEIGHT = 540;
    public static final int GOP_LENGTH_IN_FRAMES = 20;

    private final String SUBTITLE;

    private static long startTime = 0;
    private boolean exit = false;

    private CanvasFrame cameraFrame = null;
    private FFmpegFrameGrabber fFmpegFrameGrabber = null;

    private final OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();
    private final Point point = new Point(15, 65);
    private final Scalar scalar = new Scalar(0, 200, 255, 0);
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public RemoteStreamService(ScheduleManager scheduleManager,
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
    public RemoteStreamService(ScheduleManager scheduleManager,
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
    public RemoteStreamService(ScheduleManager scheduleManager,
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

    public boolean init() {
        try {
            cameraFrame = null;
            if (configManager.isEnableClient()) {
                cameraFrame = new CanvasFrame("[REMOTE] Live stream", CanvasFrame.getDefaultGamma() / fFmpegFrameGrabber.getGamma());
            }

            /////////////////////////////////
            // [INPUT] FFmpegFrameGrabber
            fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(RTMP_PATH);
            if (!configManager.isAudioOnly()) {
                fFmpegFrameGrabber.setImageWidth(CAPTURE_WIDTH);
                fFmpegFrameGrabber.setImageHeight(CAPTURE_HEIGHT);
            }
            fFmpegFrameGrabber.start();
            /////////////////////////////////
        } catch (Exception e) {
            logger.warn("RemoteStreamService.init.Exception", e);
            return false;
        }

        logger.debug("[RemoteStreamService] [INIT] RTMP_PATH=[{}], DASH_PATH=[{}]", RTMP_PATH, DASH_PATH);
        return true;
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
                setAudioOptions(audioFrameRecorder);
                setDashOptions(audioFrameRecorder);
                audioFrameRecorder.start();
            } else {
                videoFrameRecorder = new FFmpegFrameRecorder(
                        DASH_PATH,
                        CAPTURE_WIDTH, CAPTURE_HEIGHT,
                        AudioService.CHANNEL_NUM
                );
                setVideoOptions(videoFrameRecorder);
                setAudioOptions(videoFrameRecorder);
                setDashOptions(videoFrameRecorder);
                videoFrameRecorder.start();
            }
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
                if(capturedFrame == null){ continue; }
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
                        if (cameraFrame != null && cameraFrame.isVisible()) {
                            cameraFrame.showImage(capturedFrame);
                        }
                    }
                    //////////////////////////////////////
                    // VIDEO DATA
                    else if (capturedFrame.image != null && capturedFrame.image.length > 0) {
                        Mat mat = openCVConverter.convertToMat(capturedFrame);
                        if (mat != null) {
                            opencv_imgproc.putText(mat, SUBTITLE, point, opencv_imgproc.CV_FONT_VECTOR0, 0.8, scalar, 1, 0, false);
                            capturedFrame = openCVConverter.convert(mat);
                        }

                        videoFrameRecorder.record(capturedFrame);
                        if (cameraFrame != null && cameraFrame.isVisible()) {
                            cameraFrame.showImage(capturedFrame);
                        }
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
    public void stop() {
        exit = true;

        try {
            if (fFmpegFrameGrabber != null) {
                fFmpegFrameGrabber.stop();
                fFmpegFrameGrabber.release();
                fFmpegFrameGrabber = null;
            }
        } catch (Exception e) {
            logger.warn("RemoteStreamService.run.finally.Exception", e);
        }

        if (configManager.isClearDashDataIfSessionClosed()) {
            FileManager.deleteFile
                    (FileManager.concatFilePath(
                            configManager.getMediaBasePath(),
                            configManager.getCameraPath()
                    )
            );
        }
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public String getDashUnitId() {
        return dashUnitId;
    }

    private void setDashOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        // -map v:0 -s:0 $V_SIZE_1 -b:v:0 2M -maxrate:0 2.14M -bufsize:0 3.5M
        /*fFmpegFrameRecorder.setOption("map", "0:v:0");
        fFmpegFrameRecorder.setOption("b:v:0", "2M");
        fFmpegFrameRecorder.setOption("s:v:0", V_SIZE_1);*/
    /*fFmpegFrameRecorder.setOption("maxrate:0", "2.14M");
        fFmpegFrameRecorder.setOption("bufsize:0", "3.5M");*/

        // -map v:0 -s:1 $V_SIZE_2 -b:v:1 145k -maxrate:1 155k -bufsize:1 220k
        /*fFmpegFrameRecorder.setOption("map", "0:v:1");
        fFmpegFrameRecorder.setOption("b:v:1", "145K");
        fFmpegFrameRecorder.setOption("s:v:1", V_SIZE_2);*/
   /*fFmpegFrameRecorder.setOption("maxrate:1", "155k");
        fFmpegFrameRecorder.setOption("bufsize:1", "220k");*/

        // -map v:0 -s:2 $V_SIZE_3 -b:v:2 50K -maxrate:2 1M -bufsize:2 2M
        /*fFmpegFrameRecorder.setOption("map", "0:v:2");
        fFmpegFrameRecorder.setOption("b:v:2", "50K");
        fFmpegFrameRecorder.setOption("s:v:2", V_SIZE_3);
        fFmpegFrameRecorder.setOption("maxrate:2", "1M");
        fFmpegFrameRecorder.setOption("bufsize:2", "2M");*/

        // -map v:0 -s:3 $V_SIZE_4 -b:v:3 730k -maxrate:3 781k -bufsize:3 1278k
        /*fFmpegFrameRecorder.setOption("map", "0:v:3");
        fFmpegFrameRecorder.setOption("b:v:3", "730k");
        fFmpegFrameRecorder.setOption("s:v:3", V_SIZE_4);
        fFmpegFrameRecorder.setOption("maxrate:3", "781k");
        fFmpegFrameRecorder.setOption("bufsize:3", "1278k");*/

        //fFmpegFrameRecorder.setOption("map", "1:a:0");

        fFmpegFrameRecorder.setFormat("dash");
        fFmpegFrameRecorder.setOption("init_seg_name", URI_FILE_NAME + INIT_SEGMENT_POSTFIX);
        fFmpegFrameRecorder.setOption("media_seg_name", URI_FILE_NAME + MEDIA_SEGMENT_POSTFIX);
        fFmpegFrameRecorder.setOption("use_template", "1");
        fFmpegFrameRecorder.setOption("use_timeline", "0");
        fFmpegFrameRecorder.setOption("ldash", "1");
        fFmpegFrameRecorder.setOption("streaming", "1");

        /**
         * Set an intended target latency in seconds (fractional value can be set) for serving.
         * Applicable only when streaming and write_prft options are enabled.
         * This is an informative fields clients can use to measure the latency of the service.
         */
        fFmpegFrameRecorder.setOption("target_latency", "3");

        /**
         * Set the segment length in seconds (fractional value can be set).
         * The value is treated as average segment duration when use_template is enabled and
         *      use_timeline is disabled and as minimum segment duration for all the other use cases.
         */
        fFmpegFrameRecorder.setOption("seg_duration", String.valueOf(configManager.getSegmentDuration()));

        /**
         * Set the length in seconds of fragments within segments (fractional value can be set).
         * Create fragments that are duration microseconds long.
         */
        fFmpegFrameRecorder.setOption("frag_duration", "0.2"); //
        fFmpegFrameRecorder.setOption("frag_type`", "duration"); // Set the type of interval for fragmentation.

        // URL of the page that will return the UTC timestamp in ISO format. Example: "https://time.akamai.com/?iso"
        fFmpegFrameRecorder.setOption("utc_timing_url", "https://time.akamai.com/?iso");

        // Adjusts the sensitivity of x264's scenecut detection. Rarely needs to be adjusted. Recommended default: 40
        fFmpegFrameRecorder.setOption("sc_threshold", "0");

        /**
         * x264, by default,
         *  adaptively decides through a low-resolution lookahead the best number of B-frames to use.
         *  It is possible to disable this adaptivity; this is not recommended. Recommended default: 1
         *
         * 0: Very fast, but not recommended.
         *      Does not work with pre-scenecut (scenecut must be off to force off b-adapt).
         * 1: Fast, default mode in x264.
         *      A good balance between speed and quality.
         * 2: A much slower but more accurate B-frame decision mode that correctly detects fades and
         *      generally gives considerably better quality.
         *      Its speed gets considerably slower at high bframes values, so
         *      its recommended to keep bframes relatively low (perhaps around 3) when using this option.
         *      It also may slow down the first pass of x264 when in threaded mode.
         */
        fFmpegFrameRecorder.setOption("b_strategy", "0");

        /**
         * Set container format (mp4/webm) options using a : separated list of key=value parameters.
         * Values containing : special characters must be escaped.
         */
        fFmpegFrameRecorder.setOption("format_options", "movflags=+cmaf");

        /**
         * moov atom is the special part of the file,
         *      which defines the timescale, duration, display characteristics of the video,
         *      as well as subatoms containing information for each track in the video.
         *      This atom may be located at the end of the file,
         *      which is why you may get the error when the file was not completely uploaded.
         *
         * moov atom is essential for video decoding and without it,
         *      the uploaded video is unplayable, so it cannot be converted.
         *      We try our best to make the conversion even in the most hopeless cases,
         *      but if the file is not readable at all.
         *      (like missing header or moov atom in video file) - there is nothing we can do at all.
         */
        /**
         * -movflags empty_moov
             * Write an initial moov atom directly at the start of the file, without describing any samples in it.
             * Generally, an mdat/moov pair is written at the start of the file, as a normal MOV/MP4 file,
             * containing only a short portion of the file.
             * With this option set, there is no initial mdat atom,
             * and the moov atom only describes the tracks but has a zero duration.
             * This option is implicitly set when writing ismv (Smooth Streaming) files.
         */
        /**
         * -movflags separate_moof
             * Write a separate moof (movie fragment) atom for each track.
             * Normally, packets for all tracks are written in a moof atom (which is slightly more efficient),
             * but with this option set, the muxer writes one moof/mdat pair for each track, making it easier to separate tracks.
             * This option is implicitly set when writing ismv (Smooth Streaming) files.
         */
        /**
         * -movflags default_base_moof
             * Similarly to the omit_tfhd_offset,
             * this flag avoids writing the absolute base_data_offset field in tfhd atoms,
             * but does so by using the new default-base-is-moof flag instead.
             * This flag is new from 14496-12:2012.
             * This may make the fragments easier to parse in certain circumstances
             * (avoiding basing track fragment location calculations on the implicit end of the previous track fragment).
         */
        //fFmpegFrameRecorder.setOption("movflags", "+empty_moov+separate_moof+default_base_moof");

        // Set the maximum number of segments kept in the manifest.
        fFmpegFrameRecorder.setOption("window_size", String.valueOf(configManager.getWindowSize()));

        // Bit set of AV_CODEC_EXPORT_DATA_* flags, which affects the kind of metadata exported in frame, packet, or coded stream side data by decoders and encoders.
        fFmpegFrameRecorder.setOption("export_side_data", "prft");

        /**
         * Write Producer Reference Time elements on supported streams.
         * This also enables writing prft boxes in the underlying muxer.
         * Applicable only when the utc_url option is enabled.
         * It’s set to auto by default,
         *      in which case the muxer will attempt to enable it only in modes that require it.
         */
        fFmpegFrameRecorder.setOption("write_prft", "1");
    }

    private void setVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setVideoBitrate(5000000); // 2000K > default: 400000 (400K)
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE); // default: 30
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        fFmpegFrameRecorder.setOption("keyint_min", String.valueOf(GOP_LENGTH_IN_FRAMES));
        //fFmpegFrameRecorder.setFormat("matroska"); // > H265
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);
    }

    private void setAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setAudioBitrate(192000); // 192K > default: 64000 (64K)
        fFmpegFrameRecorder.setAudioOption("tune", "zerolatency");
        fFmpegFrameRecorder.setAudioOption("preset", "ultrafast");
        fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        fFmpegFrameRecorder.setAudioOption("crf", "18");
        fFmpegFrameRecorder.setAudioQuality(0);
        fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE); // default: 44100
        fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
    }
    ///////////////////////////////////////////////////////////////////////////

}
