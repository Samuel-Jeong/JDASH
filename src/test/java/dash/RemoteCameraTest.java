package dash;

import cam.RemoteCameraService;
import org.junit.Test;
import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;

public class RemoteCameraTest {

    @Test
    public void test() {
        ScheduleManager scheduleManager = new ScheduleManager();
        RemoteCameraService remoteCameraService = new RemoteCameraService(
                scheduleManager,
                RemoteCameraService.class.getSimpleName(),
                0, 1, TimeUnit.MILLISECONDS,
                1, 1, false
        );
        scheduleManager.startJob(
                "REMOTE_CAMERA_TEST",
                remoteCameraService
        );
    }
}
