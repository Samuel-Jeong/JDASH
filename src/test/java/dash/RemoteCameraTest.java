package dash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.job.JobBuilder;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;

import java.util.concurrent.TimeUnit;

public class RemoteCameraTest {

    private static final Logger logger = LoggerFactory.getLogger(RemoteCameraTest.class);

    public void test() {
        ScheduleManager scheduleManager = new ScheduleManager();

        Job remoteStreamServiceJob = new JobBuilder()
                .setScheduleManager(scheduleManager)
                .setName(RemoteStreamService.class.getSimpleName())
                .setInitialDelay(0)
                .setInterval(10)
                .setTimeUnit(TimeUnit.MILLISECONDS)
                .setPriority(1)
                .setTotalRunCount(1)
                .setIsLasted(true)
                .build();

        RemoteStreamService remoteCameraService = new RemoteStreamService(remoteStreamServiceJob);
        if (remoteCameraService.init()) {
            remoteCameraService.start();
            if (scheduleManager.startJob(
                    "REMOTE_CAMERA_TEST",
                    remoteCameraService.getJob())) {
                logger.debug("[RemoteCameraTest] [+RUN] Success to start the remote camera.");
            } else {
                logger.warn("[RemoteCameraTest)] [-RUN FAIL] Fail to start the remote camera.");
            }
        }
    }
}
