package dash.unit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import process.ProcessManager;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.io.File;
import java.time.Duration;

public class DashUnit {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    private final long initiationTime;
    private final String id;

    transient private MPD mpd;

    private String inputFilePath = null;
    private String outputFilePath = null;
    private String curSegmentName = null;

    private Duration duration = null;
    private Duration minBufferTime= null;

    private Thread liveStreamingThread = null;
    private boolean isLiveStreaming = false;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(String id, MPD mpd) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runLiveMpdProcess(String command, String mpdPath) {
        if (liveStreamingThread != null && (liveStreamingThread.isAlive() || !liveStreamingThread.isInterrupted())) {
            liveStreamingThread.interrupt();
            clearMpdPath();
        }

        liveStreamingThread = new Thread(() -> ProcessManager.runProcessWait(command, mpdPath));
        liveStreamingThread.start();
        logger.debug("[DashUnit(id={})] RUN Live MPD Process", id);
    }

    public void finishLiveMpdProcess() {
        if (liveStreamingThread != null && (liveStreamingThread.isAlive() || !liveStreamingThread.isInterrupted())) {
            liveStreamingThread.interrupt();
            liveStreamingThread = null;
            logger.debug("[DashUnit(id={})] FINISH Live MPD Process", id);
        }

        clearMpdPath();
    }

    public void clearMpdPath() {
        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = FileManager.getParentPathFromUri(outputFilePath);
            if (mpdParentPath != null) {
                File mpdParentPathFile = new File(mpdParentPath);
                if (mpdParentPathFile.exists() && mpdParentPathFile.isDirectory()) {
                    FileManager.deleteFile(mpdParentPath);
                    logger.debug("[DashUnit(id={})] DELETE ALL MPD Files. (path={})", id, mpdParentPath);
                }
            }
        }
    }

    public byte[] getSegmentByteData(String uri) {
        return FileManager.readAllBytes(uri);
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

    public void setMpd(MPD mpd) {
        this.mpd = mpd;
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

    public boolean isLiveStreaming() {
        return isLiveStreaming;
    }

    public void setLiveStreaming(boolean liveStreaming) {
        isLiveStreaming = liveStreaming;
    }

    @Override
    public String toString() {
        return "DashUnit{" +
                "initiationTime=" + initiationTime +
                ", id='" + id + '\'' +
                ", inputFilePath='" + inputFilePath + '\'' +
                ", outputFilePath='" + outputFilePath + '\'' +
                ", curSegmentName='" + curSegmentName + '\'' +
                ", duration=" + duration +
                ", minBufferTime=" + minBufferTime +
                ", isLiveStreaming=" + isLiveStreaming +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
