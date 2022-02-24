package service.scheduler.schedule.unit;

import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.util.TimerTask;

public class FutureScheduler extends TimerTask {

    private final ScheduleManager scheduleManager;
    private final Job job;

    public FutureScheduler(ScheduleManager scheduleManager, Job job) {
        this.scheduleManager = scheduleManager;
        this.job = job;
    }

    @Override
    public void run() {
        if (job.getScheduleUnitKey() == null) {
            return;
        }

        ScheduleUnit scheduleUnit = scheduleManager.getScheduleUnit(job.getScheduleUnitKey());
        if (scheduleUnit.getJobScheduler().isJobFinished(job)) {
            return;
        }

        scheduleUnit.getJobScheduler().addJobToExecutor(job);

        if (!job.getIsInitialFinished() && job.getInitialDelay() > 0) {
            job.setIsInitialFinished(true);
            scheduleUnit.getJobScheduler().schedule(job);
        }
    }

}