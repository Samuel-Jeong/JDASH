package dash;

import org.junit.Test;
import service.scheduler.schedule.ScheduleManager;
import stream.RemoteStreamService;

import java.util.concurrent.TimeUnit;

public class RemoteCameraTest {

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
        scheduleManager.startJob(
                "REMOTE_CAMERA_TEST",
                remoteCameraService
        );
    }
}
