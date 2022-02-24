package dash.unit;

import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rtmp.RtmpClient;
import service.AppInstance;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.time.Duration;
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

    private boolean isLiveStreaming = false;

    //private LiveStreamingHandler liveStreamingHandler = null;
    private final AtomicBoolean isRtmpStreaming = new AtomicBoolean(false);

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    private RtmpClient rtmpClient = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit(String id, MPD mpd) {
        this.id = id;
        this.initiationTime = System.currentTimeMillis();
        this.mpd = mpd;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void runRtmpStreaming(String uriFileName, String curRtmpUri, String mpdPath) {
        if (isRtmpStreaming.get()) {
            logger.warn("[DashUnit(id={})] runRtmpStreaming is already running...", id);
            return;
        }

        try {
            rtmpClient = new RtmpClient(uriFileName, mpdPath);
            rtmpClient.start();

            // sh rtmp_streaming.sh jamesj rtmp://192.168.5.222:1940/live/jamesj /home/uangel/udash/media/live/jamesj/jamesj.mpd
            /*String scriptPath = configManager.getScriptPath();
            String command = "sh " + scriptPath;
            command = command + " " + uriFileName + " " + curRtmpUri + " " + mpdPath;
            liveStreamingHandler = new LiveStreamingHandler(isRtmpStreaming, command);
            liveStreamingHandler.start();
            isRtmpStreaming.set(true);
            logger.debug("[DashUnit(id={})] [+RUN] RtmpStreaming (command={})", id, command);*/
        } catch (Exception e) {
            logger.debug("[DashUnit(id={})] runRtmpStreaming.Exception", id, e);
        }
    }

    public void finishRtmpStreaming() {
        if (rtmpClient != null) {
            rtmpClient.stop();
        }

        /*if (liveStreamingHandler != null && isRtmpStreaming.get()) {
            liveStreamingHandler.interrupt();
            if (liveStreamingHandler.isInterrupted()) {
                logger.debug("[DashUnit(id={})] [-FINISH] RtmpStreaming", id);
                isRtmpStreaming.set(false);
                liveStreamingHandler = null;
            }
        }*/
    }

    /*private static class LiveStreamingHandler extends Thread {

        private final AtomicBoolean isRtmpStreaming;
        private final String command;

        public LiveStreamingHandler(AtomicBoolean isRtmpStreaming, String command) {
            this.isRtmpStreaming = isRtmpStreaming;
            this.command = command;
        }

        @Override
        public void run() {
            while (isRtmpStreaming.get()) {
                ProcessManager.runProcessWait(command);
            }
        }

    }*/

    public void clearMpdPath() {
        if (outputFilePath != null) { // Delete MPD path
            String mpdParentPath = FileManager.getParentPathFromUri(outputFilePath);
            //logger.debug("[DashUnit(id={})] outputFilePath: {}, mpdParentPath: {}", id, outputFilePath, mpdParentPath);
            if (mpdParentPath != null) {
                FileManager.deleteFile(mpdParentPath);
                logger.debug("[DashUnit(id={})] DELETE ALL MPD Files. (path={})", id, mpdParentPath);
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
