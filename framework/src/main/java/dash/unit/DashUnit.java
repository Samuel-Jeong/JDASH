package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.data.MPD;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

public class DashUnit {

    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    ////////////////////////////////////////////////////////////
    private final long initiationTime;
    private final String id;

    transient private final MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String curSegmentName = null;

    private Duration duration = null;
    private Duration minBufferTime= null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(String id, MPD mpd) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public byte[] getSegmentByteData(String uri) {
        File m3u8File = new File(uri);
        if (!m3u8File.exists() || !m3u8File.isFile()) {
            logger.warn("[DashUnit({})] Fail to get the segment data. URI is not exists. (uri={})", id, uri);
            return null;
        }

        try {
            return Files.readAllBytes(Paths.get(uri));
        } catch (Exception e) {
            return null;
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public long getInitiationTime() {
        return initiationTime;
    }

    public String getId() {
        return id;
    }

    public MPD getMpd() {
        return mpd;
    }

    public String getInputFilePath() {
        return inputFilePath;
    }

    public void setInputFilePath(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public void setOutputFilePath(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Duration getMinBufferTime() {
        return minBufferTime;
    }

    public void setMinBufferTime(Duration minBufferTime) {
        this.minBufferTime = minBufferTime;
    }

    public String getCurSegmentName() {
        return curSegmentName;
    }

    public void setCurSegmentName(String curSegmentName) {
        this.curSegmentName = curSegmentName;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
