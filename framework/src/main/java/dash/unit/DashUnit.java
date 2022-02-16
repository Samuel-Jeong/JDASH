package dash.unit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashUnit {

    private static final Logger logger = LoggerFactory.getLogger(DashUnit.class);

    ////////////////////////////////////////////////////////////
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
    public DashUnit(String id) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        liveTask = new ScheduledThreadPoolExecutor(1);
    }

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
                    runProcessWait(command, mpdPath, true)
            );
            isOngoingLiveTask.set(true);
        }
    }

    public void finishLiveMpdProcess() {
        liveTask.shutdown();
        isOngoingLiveTask.set(false);

        if (outputFilePath != null) { // MPD path
            String mpdParentPath = FileManager.getParentPathFromUri(outputFilePath);
            if (mpdParentPath != null) {
                File mpdParentPathFile = new File(mpdParentPath);
                if (mpdParentPathFile.exists() && mpdParentPathFile.isDirectory()) {
                    FileManager.deleteFile(mpdParentPath);
                }
            }
        }
    }

    public boolean getIsOngoingLiveTask() {
        return isOngoingLiveTask.get();
    }

    public byte[] getSegmentByteData(String uri) {
        return FileManager.readAllBytes(uri);
    }

    public void runProcessWait(String command, String mpdPath, boolean isClear) {
        BufferedReader stdOut = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);

            String str;
            stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((str = stdOut.readLine()) != null) {
                logger.debug(str);
            }

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("[DashUnit(id=" + this.id + ")] exit code is not 0 [" + exitValue + "]");
            }

            logger.debug("[DashUnit(id={})] Success to convert. (fileName={})", this.id, mpdPath);
        } catch (Exception e) {
            logger.warn("DashUnit.runProcess.Exception", e);
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (stdOut != null) {
                try {
                    stdOut.close();
                } catch (IOException e) {
                    logger.warn("[DashUnit(id={})] Fail to close the BufferReader.", this.id, e);
                }
            }

            if (isClear) {
                String mpdParentPath = FileManager.getParentPathFromUri(mpdPath);
                if (mpdParentPath != null) {
                    File mpdParentPathFile = new File(mpdParentPath);
                    if (mpdParentPathFile.exists() && mpdParentPathFile.isDirectory()) {
                        FileManager.deleteFile(mpdParentPath);
                    }
                }
            }
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
