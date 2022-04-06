package dash;

import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.global.avdevice;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LocalCameraTest {

    private static final Logger logger = LoggerFactory.getLogger(LocalCameraTest.class);

    @Test
    public void test () {
        avdevice.avdevice_register_all();
        AVInputFormat inp = avdevice.av_input_video_device_next(null);
        while ( inp != null ) {
            String format = inp.name().getString();
            if (format == null || format.isEmpty()) {
                break;
            }

            FFmpegFrameGrabber lister = new FFmpegFrameGrabber("");

            lister.setFormat(format);
            logger.debug("format: {}", format);

            lister.setOption("list_devices", "true");
            try {
                lister.start();
            } catch (Exception e) {
                // cannot open "dummy": ignore exception
            }

            inp.close();
            inp = avdevice.av_input_video_device_next(inp);
        }
    }

}
