package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lindstrom.mpd.data.MPD;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private MPD mpd;

    private String inputFilePath;
    private String outputFilePath;

    private String url;
    private double duration;
    private double minBufferTime;
    private String curSegmentName;
    private double curNetworkBitRate;
    private double curSelectedBitRate;
    private double curBufferTime;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
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
