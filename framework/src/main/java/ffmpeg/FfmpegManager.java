package ffmpeg;

import config.ConfigManager;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.io.IOException;

public class FfmpegManager {

    private static final Logger logger = LoggerFactory.getLogger(FfmpegManager.class);

    /////////////////////////////////////////////////////
    // VIDEO OPTIONS
    private static final String VIDEO_CODEC = "libx265";
    private static final String VIDEO_BIT_RATE = "2M";
    private static final String VIDEO_MAX_RATE = "3M";
    private static final String VIDEO_BUF_SIZE = "4M";
    private static final String FPS = "30";
    private static final String GOP = "30";
    private static final String V_SIZE_1 = "960x540";
    private static final String V_SIZE_2 = "416x234";
    private static final String V_SIZE_3 = "640x360";
    private static final String V_SIZE_4 = "768x432";
    private static final String V_SIZE_5 = "1280x720";
    private static final String V_SIZE_6 = "1920x1080";
    private static final String USE_TEMPLATE = "1";
    private static final String USE_TIMELINE = "1";
    private static final String PRESET_P = "veryfast";

    // AUDIO OPTIONS
    private static final String AUDIO_CODEC = "aac";
    private static final String AUDIO_SAMPLING_RATE = "44100";
    private static final String AUDIO_CHANNEL = "1";
    private static final String AUDIO_BIT_RATE = "128k";

    // DASH OPTIONS
    private static final String DASH_FORMAT_NAME = "dash";
    private static final String SEG_DURATION = "2";
    private static final String INIT_SEG_NAME = "_init\\$RepresentationID\\$.\\$ext\\$";
    private static final String MEDIA_SEG_NAME = "_chunk\\$RepresentationID\\$-\\$Number%05d\\$.\\$ext\\$";
    private static final String ADAPTATION_SETS = "id=0,streams=v id=1,streams=a";
    /////////////////////////////////////////////////////

    /////////////////////////////////////////////////////
    private final FFmpegExecutor fFmpegExecutor;
    /////////////////////////////////////////////////////

    /////////////////////////////////////////////////////
    public FfmpegManager() throws IOException {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        /////////////////////////////////////////////////////
        FFmpeg fFmpeg = new FFmpeg(configManager.getFfmpegPath());
        FFprobe fFprobe = new FFprobe(configManager.getFfprobePath());
        this.fFmpegExecutor = new FFmpegExecutor(fFmpeg, fFprobe);
    }
    /////////////////////////////////////////////////////

    /////////////////////////////////////////////////////
    public void startRtmpStreaming(String segmentName, String input, String output) {
        logger.debug("[FfmpegManager] [START] startRtmpStreaming");
        FFmpegBuilder builder = new FFmpegBuilder()
                .overrideOutputFiles(true)
                .setInput(input)
                .addOutput(output)
                .setFormat("dash")
                .addExtraArgs("-preset", PRESET_P)
                .addExtraArgs("-keyint_min", GOP)
                .addExtraArgs("-g", GOP)
                .addExtraArgs("-r", FPS)
                .addExtraArgs("-c:v", VIDEO_CODEC)
                .addExtraArgs("-c:a", AUDIO_CODEC)
                .addExtraArgs("-ac", AUDIO_CHANNEL)
                .addExtraArgs("-ar", AUDIO_SAMPLING_RATE)
                .addExtraArgs("-map", "v:0")
                .addExtraArgs("-s:2", V_SIZE_3)
                .addExtraArgs("-b:v:2", VIDEO_BIT_RATE)
                .addExtraArgs("-maxrate:2", VIDEO_MAX_RATE)
                .addExtraArgs("-bufsize:2", VIDEO_BUF_SIZE)
                .addExtraArgs("-map", "0:a")
                .addExtraArgs("-init_seg_name", segmentName + INIT_SEG_NAME)
                .addExtraArgs("-media_seg_name", segmentName + MEDIA_SEG_NAME)
                .addExtraArgs("-use_template", USE_TEMPLATE)
                .addExtraArgs("-use_timeline", USE_TIMELINE)
                .addExtraArgs("-seg_duration", SEG_DURATION)
                .addExtraArgs("-adaptation_sets", ADAPTATION_SETS)
                .done();

        fFmpegExecutor.createJob(builder).run();
        logger.debug("[FfmpegManager] [END] startRtmpStreaming");
    }
    /////////////////////////////////////////////////////

}
