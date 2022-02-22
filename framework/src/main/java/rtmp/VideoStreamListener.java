package rtmp;

import com.google.gson.Gson;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.scheduling.JDKSchedulingService;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VideoStreamListener implements IStreamListener {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamListener.class);

    private volatile boolean firstPacketReceived = false;
    // Maximum time between video packets
    private int videoTimeout = 10000;
    private long firstPacketTime = 0L;
    private long packetCount = 0L;
    // Last time video was received, not video timestamp
    private long lastVideoTime;
    private String userId;
    // Stream being observed
    private IBroadcastStream stream;
    // if this stream is recorded or not
    private boolean record;
    // Scheduler
    private ISchedulingService scheduler;
    // Event queue worker job name
    private String timeoutJobName;
    private volatile boolean publishing = false;
    private volatile boolean streamPaused = false;
    private IScope scope;

    public VideoStreamListener(IScope scope, IBroadcastStream stream, Boolean record, String userId, int packetTimeout) {
        this.scope = scope;
        this.stream = stream;
        this.record = record;
        this.videoTimeout = packetTimeout;
        this.userId = userId;

        // get the scheduler
        //scheduler = (ISchedulingService) scope.getParent().getContext().getBean(JDKSchedulingService.BEAN_NAME);
        scheduler = (ISchedulingService) ScopeUtils.getScopeService(scope, ISchedulingService.class, JDKSchedulingService.class, false);
    }

    private Long genTimestamp() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    @Override
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        IoBuffer buf = packet.getData();
        if (buf != null)
            buf.rewind();

        if (buf == null || buf.remaining() == 0){
            return;
        }

        if (packet instanceof VideoData) {
            // keep track of last time video was received
            lastVideoTime = System.currentTimeMillis();
            packetCount++;

            if (! firstPacketReceived) {
                firstPacketReceived = true;
                publishing = true;
                firstPacketTime = lastVideoTime;

                // start the worker to monitor if we are still receiving video packets
                timeoutJobName = scheduler.addScheduledJob(videoTimeout, new TimeoutJob());

                if (record) {
                    Map<String, String> event = new HashMap<>();
                    event.put("module", "WEBCAM");
                    event.put("timestamp", genTimestamp().toString());
                    event.put("meetingId", scope.getName());
                    event.put("stream", stream.getPublishedName());
                    event.put("eventName", "StartWebcamShareEvent");
                    logger.debug("VIDEO DATA: {}", event);
                    //recordingService.record(scope.getName(), event);
                }
            }


            if (streamPaused) {
                streamPaused = false;
                long now = System.currentTimeMillis();
                long numSeconds = (now - lastVideoTime)/1000;

                Map<String, Object> logData = new HashMap<>();
                logData.put("meetingId", scope.getName());
                logData.put("userId", userId);
                logData.put("stream", stream.getPublishedName());
                logData.put("packetCount", packetCount);
                logData.put("publishing", publishing);
                logData.put("pausedFor (sec)", numSeconds);

                Gson gson = new Gson();
                String logStr =  gson.toJson(logData);

                logger.warn("Video stream restarted. data={}", logStr );
            }

        }
    }

    public void streamStopped() {
        this.publishing = false;
    }

    private class TimeoutJob implements IScheduledJob {
        private boolean streamStopped = false;

        public void execute(ISchedulingService service) {
            Map<String, Object> logData = new HashMap<>();
            logData.put("meetingId", scope.getName());
            logData.put("userId", userId);
            logData.put("stream", stream.getPublishedName());
            logData.put("packetCount", packetCount);
            logData.put("publishing", publishing);

            Gson gson = new Gson();

            long now = System.currentTimeMillis();
            if ((now - lastVideoTime) > videoTimeout && !streamPaused) {
                streamPaused = true;
                long numSeconds = (now - lastVideoTime)/1000;
                logData.put("lastPacketTime (sec)", numSeconds);
                String logStr =  gson.toJson(logData);
                logger.warn("Video packet timeout. data={}", logStr );
            }

            String logStr =  gson.toJson(logData);
            if (!publishing) {
                logger.warn("Removing scheduled job. data={}", logStr );
                // remove the scheduled job
                scheduler.removeScheduledJob(timeoutJobName);
            }
        }

    }

}