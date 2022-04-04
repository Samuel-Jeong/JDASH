package dash;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;

import java.util.concurrent.TimeUnit;

public class RemoteCameraTest {

    private static final Logger logger = LoggerFactory.getLogger(RemoteCameraTest.class);

    @Test
    public void test() {
        ScheduleManager scheduleManager = new ScheduleManager();
        RemoteStreamService remoteCameraService = new RemoteStreamService(
                scheduleManager,
                RemoteStreamService.class.getSimpleName(),
                0, 1, TimeUnit.MILLISECONDS,
                1, 1, false
        );
        remoteCameraService.init();
        if (scheduleManager.startJob(
                "REMOTE_CAMERA_TEST",
                remoteCameraService)) {
            logger.debug("[RemoteCameraTest] [+RUN] Success to start the remote camera.");
        } else {
            logger.warn("[RemoteCameraTest)] [-RUN FAIL] Fail to start the remote camera.");
        }
    }
}
