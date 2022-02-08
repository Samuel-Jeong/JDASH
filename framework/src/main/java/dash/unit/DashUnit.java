package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tool.parser.mpd.data.MPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DashUnit {

    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    ////////////////////////////////////////////////////////////
    private final long initiationTime;
    private final String id;

    transient private final MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;

    private String url = null;
    private String curSegmentName = null;

    private double duration = 0.0;
    private double minBufferTime= 0.0;
    private double curNetworkBitRate= 0.0;
    private double curSelectedBitRate= 0.0;
    private double curBufferTime= 0.0;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getMinBufferTime() {
        return minBufferTime;
    }

    public void setMinBufferTime(double minBufferTime) {
        this.minBufferTime = minBufferTime;
    }

    public String getCurSegmentName() {
        return curSegmentName;
    }

    public void setCurSegmentName(String curSegmentName) {
        this.curSegmentName = curSegmentName;
    }

    public double getCurNetworkBitRate() {
        return curNetworkBitRate;
    }

    public void setCurNetworkBitRate(double curNetworkBitRate) {
        this.curNetworkBitRate = curNetworkBitRate;
    }

    public double getCurSelectedBitRate() {
        return curSelectedBitRate;
    }

    public void setCurSelectedBitRate(double curSelectedBitRate) {
        this.curSelectedBitRate = curSelectedBitRate;
    }

    public double getCurBufferTime() {
        return curBufferTime;
    }

    public void setCurBufferTime(double curBufferTime) {
        this.curBufferTime = curBufferTime;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
