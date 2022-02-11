package cam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;


public class Mp4Maker {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(Mp4Maker.class);

    private String outputPath;
    private SeekableByteChannel out = null;
    private AWTSequenceEncoder encoder = null;
    private final AtomicInteger fps = new AtomicInteger(30);
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public Mp4Maker(String outputPath) {
        this.outputPath = outputPath;
    }

    public boolean init() {
        if (outputPath == null) { return false; }

        try {
            out = NIOUtils.writableFileChannel(outputPath);
            encoder = new AWTSequenceEncoder(out, Rational.R(getFps(), 1));
            return true;
        } catch (Exception e) {
            if (encoder != null) {
                try {
                    encoder.finish();
                } catch (IOException ex) {
                    // ignore
                }
                encoder = null;
            }

            NIOUtils.closeQuietly(out);
            logger.warn("Mp4Maker.process.Exception", e);
            return false;
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void write(BufferedImage bufferedImage) {
        if (bufferedImage == null) { return; }
        if (out == null || encoder == null) { return; }

        try {
            encoder.encodeImage(bufferedImage);
        } catch (Exception e) {
            //logger.warn("Mp4Maker.process.Exception", e);
        }
    }

    public void finish() {
        if (out == null || encoder == null) { return; }

        try {
            encoder.finish();
            encoder = null;
            //logger.debug("[Mp4Maker] Success to make the mp4. ({})", outputPath);
        } catch (Exception e) {
            logger.debug("[Mp4Maker] Fail to make the mp4. ({})", outputPath);
        } finally {
            NIOUtils.closeQuietly(out);
            out = null;
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public int getFps() {
        return this.fps.get();
    }

    public void setFps(int fps) {
        this.fps.set(fps);
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////////////////////////

}
