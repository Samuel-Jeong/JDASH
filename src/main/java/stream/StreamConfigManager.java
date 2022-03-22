package stream;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

public class StreamConfigManager {

    ///////////////////////////////////////////////////////////////////////////
    public static final String STREAMING_WITH_DASH = "dash";
    public static final String STREAMING_WITH_RTMP = "rtmp";

    public static final String RTMP_PREFIX = "rtmp://";
    public static final String HTTP_PREFIX = "http://";
    public static final String DASH_POSTFIX = ".mpd";
    public static final String MP4_POSTFIX = ".mp4";

    private static final String INIT_SEGMENT_POSTFIX = "_init$RepresentationID$.m4s";
    private static final String MEDIA_SEGMENT_POSTFIX = "_chunk$RepresentationID$_$Number%05d$.m4s";

    public static final String V_SIZE_1 = "960x540";
    public static final String V_SIZE_2 = "416x234";
    public static final String V_SIZE_3 = "640x360";
    public static final String V_SIZE_4 = "768x432";
    public static final String V_SIZE_5 = "1280x720";
    public static final String V_SIZE_6 = "1920x1080";

    public static final double FRAME_RATE = 30;
    public static final int CAPTURE_WIDTH = 640;
    public static final int CAPTURE_HEIGHT = 320;
    public static final int GOP_LENGTH_IN_FRAMES = 2;

    public static final int AUDIO_RETRY_LIMIT = 3;
    public static final int VIDEO_RETRY_LIMIT = 3;
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public static void setDashOptions(FFmpegFrameRecorder fFmpegFrameRecorder,
                                      String uriFileName,
                                      boolean isAudioOnly, double segmentDuration, int windowSize) {
        if (fFmpegFrameRecorder == null) { return; }

        /*if (!isAudioOnly) {
            fFmpegFrameRecorder.setOption("-map", "0");
            fFmpegFrameRecorder.setOption("-map", "0");
            fFmpegFrameRecorder.setOption("-map", "0");

            fFmpegFrameRecorder.setOption("b:v:0", "800k");
            fFmpegFrameRecorder.setOption("s:v:0", V_SIZE_5);
            fFmpegFrameRecorder.setOption("profile:v:0", "main");

            fFmpegFrameRecorder.setOption("b:v:1", "500k");
            fFmpegFrameRecorder.setOption("s:v:1", V_SIZE_3);
            fFmpegFrameRecorder.setOption("profile:v:1", "main");

            fFmpegFrameRecorder.setOption("b:v:2", "300k");
            fFmpegFrameRecorder.setOption("s:v:2", V_SIZE_2);
            fFmpegFrameRecorder.setOption("profile:v:2", "baseline");

            fFmpegFrameRecorder.setOption("bf", "1");
        }*/

        fFmpegFrameRecorder.setFormat("dash");
        fFmpegFrameRecorder.setOption("init_seg_name", uriFileName + INIT_SEGMENT_POSTFIX);
        fFmpegFrameRecorder.setOption("media_seg_name", uriFileName + MEDIA_SEGMENT_POSTFIX);
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
        if (segmentDuration > 0) {
            fFmpegFrameRecorder.setOption("seg_duration", String.valueOf(segmentDuration));
        }

        //fFmpegFrameRecorder.setOption("frag_type", "duration"); // Set the type of interval for fragmentation.
        /**
         * Set the length in seconds of fragments within segments (fractional value can be set).
         * Create fragments that are duration microseconds long.
         */
        //fFmpegFrameRecorder.setOption("frag_duration", "0.2");

        // URL of the page that will return the UTC timestamp in ISO format. Example: "https://time.akamai.com/?iso"
        fFmpegFrameRecorder.setOption("utc_timing_url", "https://time.akamai.com/?iso");

        // Adjusts the sensitivity of x264's scenecut detection. Rarely needs to be adjusted. Recommended default: 40
        //fFmpegFrameRecorder.setOption("sc_threshold", "0");

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
        //fFmpegFrameRecorder.setOption("b_strategy", "0");

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
        if (windowSize > 0) {
            fFmpegFrameRecorder.setOption("window_size", String.valueOf(windowSize));
        }

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
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public static void setRemoteStreamVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        if (fFmpegFrameRecorder == null) { return; }

        fFmpegFrameRecorder.setVideoBitrate(2000000); // 2000K > default: 400000 (400K)
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");

        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);

        //fFmpegFrameRecorder.setFormat("flv");
        fFmpegFrameRecorder.setFormat("matroska");

        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE); // default: 30
        //fFmpegFrameRecorder.setOption("keyint_min", String.valueOf(GOP_LENGTH_IN_FRAMES));
    }

    public static void setLocalStreamVideoOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        fFmpegFrameRecorder.setVideoBitrate(2000000); // 2000K > default: 400000 (400K)
        fFmpegFrameRecorder.setVideoOption("tune", "zerolatency");
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");

        fFmpegFrameRecorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        //fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H265);

        /**
         * 1. Flash Video (.flv)
         * - Owner : Adobe
         * [Video] : Sorenson H.263 (Flash v6, v7), VP6 (Flash v8), Screen video, H.264
         * [Audio] : MP3, ADPCM, Linear PCM, Nellymoser, Speex, AAC, G.711
         */
        //fFmpegFrameRecorder.setFormat("flv");
        /**
         * 2. Matroska (wp, .mkv/.mka/.mks)
         * - Owner : CoreCodec
         * [Video] : H.264, Realvideo, DivX, XviD, HEVC
         * [Audio] : AAC, Vorbis, Dolby AC3, MP3
         */
        fFmpegFrameRecorder.setFormat("matroska");

        /**
         * The range of the CRF scale is 0–51,
         *      where 0 is lossless (for 8 bit only, for 10 bit use -qp 0),
         *      23 is the default, and 5matroska1 is worst quality possible.
         * A lower value generally leads to higher quality,
         *      and a subjectively sane range is 17–28.
         * Consider 17 or 18 to be visually lossless or nearly so;
         *      it should look the same or nearly the same as the input but it isn't technically lossless.
         */
        fFmpegFrameRecorder.setVideoOption("crf", "28");
        fFmpegFrameRecorder.setGopSize(GOP_LENGTH_IN_FRAMES);
        fFmpegFrameRecorder.setFrameRate(FRAME_RATE); // default: 30
        //fFmpegFrameRecorder.setOption("keyint_min", String.valueOf(GOP_LENGTH_IN_FRAMES));
    }
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    public static void setRemoteStreamAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
        if (fFmpegFrameRecorder == null) { return; }

        //fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
        fFmpegFrameRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AC3);

        fFmpegFrameRecorder.setAudioOption("tune", "zerolatency");
        fFmpegFrameRecorder.setAudioOption("preset", "ultrafast");
        fFmpegFrameRecorder.setAudioOption("crf", "18");
        fFmpegFrameRecorder.setAudioQuality(0);
        fFmpegFrameRecorder.setAudioBitrate(192000); // 192K > default: 64000 (64K)
        fFmpegFrameRecorder.setSampleRate(AudioService.SAMPLE_RATE); // default: 44100
        //fFmpegFrameRecorder.setSampleRate(48000); // FOR AC3
        fFmpegFrameRecorder.setAudioChannels(AudioService.CHANNEL_NUM);
    }

    public static void setLocalStreamAudioOptions(FFmpegFrameRecorder fFmpegFrameRecorder) {
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
    ///////////////////////////////////////////////////////////////////////////

}
