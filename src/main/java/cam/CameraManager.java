package cam;

import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;

public class CameraManager extends Job {

    ////////////////////////////////////////////////////////////////////////////////
    public CameraManager(ScheduleManager scheduleManager, String name,
                         int initialDelay, int interval, TimeUnit timeUnit,
                         int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        CameraService cameraService = new CameraService();
        cameraService.action();
    }
    ////////////////////////////////////////////////////////////////////////////////

}
