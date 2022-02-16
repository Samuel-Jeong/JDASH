package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import process.ProcessManager;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final ThreadPoolExecutor liveTask;
    private final AtomicBoolean isOngoingLiveTask = new AtomicBoolean(false);
    private boolean isLiveStreaming = false;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(String id, MPD mpd) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
        liveTask = new ScheduledThreadPoolExecutor(1);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runLiveMpdProcess(String command, String mpdPath) {
        if (!getIsOngoingLiveTask()) {
            liveTask.execute(() ->
                    ProcessManager.runProcessWait(command, mpdPath)
            );
            isOngoingLiveTask.set(true);
            logger.debug("[DashUnit(id={})] RUN Live MPD Process", id);
        }
    }

    public void finishLiveMpdProcess() {
        liveTask.shutdown();
        isOngoingLiveTask.set(false);

        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = FileManager.getParentPathFromUri(outputFilePath);
            if (mpdParentPath != null) {
                File mpdParentPathFile = new File(mpdParentPath);
                if (mpdParentPathFile.exists() && mpdParentPathFile.isDirectory()) {
                    FileManager.deleteFile(mpdParentPath);
                }
            }
        }

        logger.debug("[DashUnit(id={})] FINISH Live MPD Process", id);
    }

    public boolean getIsOngoingLiveTask() {
        return isOngoingLiveTask.get();
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
                ", isOngoingLiveTask=" + isOngoingLiveTask.get() +
                ", isLiveStreaming=" + isLiveStreaming +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
