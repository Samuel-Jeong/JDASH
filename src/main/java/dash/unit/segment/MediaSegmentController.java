package dash.unit.segment;

import dash.mpd.MpdManager;
import dash.unit.MediaType;
import lombok.extern.slf4j.Slf4j;
import service.scheduler.job.Job;
import service.scheduler.job.JobBuilder;
import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;

@Slf4j
public class MediaSegmentController {

    private final String id;
    private final MediaType mediaType;
    private final ScheduleManager scheduleManager;
    private final String OLD_FILE_CONTROL_SCHEDULE_KEY;
    private transient OldFileController oldFileController = null;
    private final MediaSegmentInfo mediaSegmentInfo;

    public MediaSegmentController(String id, MediaType mediaType, ScheduleManager scheduleManager) {
        this.id = id;
        this.mediaType = mediaType;
        this.mediaSegmentInfo = new MediaSegmentInfo(mediaType);

        this.scheduleManager = scheduleManager;
        this.OLD_FILE_CONTROL_SCHEDULE_KEY = "OLD_FILE_CONTROL_SCHEDULE_KEY:" + id + ":" + mediaType.name();
        if (scheduleManager.initJob(OLD_FILE_CONTROL_SCHEDULE_KEY, 1, 1)) {
            log.debug("[{}/MediaSegmentController(id={})] Success to init job scheduler ({})", mediaType.name(), id, OLD_FILE_CONTROL_SCHEDULE_KEY);
        }
    }

    public void start (MpdManager mpdManager, String dashPath) {
        Job oldFileControlJob = new JobBuilder()
                .setScheduleManager(scheduleManager)
                .setName(OldFileController.class.getSimpleName() + "_" + id + ":" + mediaType.name())
                .setInitialDelay(0)
                .setInterval(1000)
                .setTimeUnit(TimeUnit.MILLISECONDS)
                .setPriority(1)
                .setTotalRunCount(1)
                .setIsLasted(true)
                .build();
        oldFileController = new OldFileController(
                oldFileControlJob,
                mpdManager, id, dashPath, mediaSegmentInfo
        );
        oldFileController.init();
        if (scheduleManager.startJob(OLD_FILE_CONTROL_SCHEDULE_KEY, oldFileController.getJob())) {
            log.debug("[{}/MediaSegmentController(id={})] [+RUN] OldFileController", mediaType.name(), id);
        } else {
            log.warn("[{}/MediaSegmentController(id={})] [-RUN FAIL] OldFileController", mediaType.name(), id);
        }
    }

    public void stop() {
        if (oldFileController != null) {
            scheduleManager.stopJob(OLD_FILE_CONTROL_SCHEDULE_KEY, oldFileController.getJob());
            oldFileController = null;
            log.debug("[{}/MediaSegmentController(id={})] [-FINISH] OldFileController", mediaType.name(), id);
        }

        scheduleManager.stopAll(OLD_FILE_CONTROL_SCHEDULE_KEY);
    }

    public MediaSegmentInfo getMediaSegmentInfo() {
        return mediaSegmentInfo;
    }

}
