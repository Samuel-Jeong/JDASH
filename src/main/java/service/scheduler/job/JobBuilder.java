package service.scheduler.job;

import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;

public class JobBuilder {

    private final Job job;

    public JobBuilder() {
        this.job = new Job();
    }

    public JobBuilder setScheduleManager(ScheduleManager scheduleManager) {
        job.setScheduleManager(scheduleManager);
        return this;
    }

    public JobBuilder setName(String name) {
        job.setName(name);
        return this;
    }

    public JobBuilder setInitialDelay(int initialDelay) {
        job.setInitialDelay(initialDelay);
        return this;
    }

    public JobBuilder setInterval(int interval) {
        job.setInterval(interval);
        return this;
    }

    public JobBuilder setTimeUnit(TimeUnit timeUnit) {
        job.setTimeUnit(timeUnit);
        return this;
    }

    public JobBuilder setPriority(int priority) {
        job.setPriority(priority);
        return this;
    }

    public JobBuilder setTotalRunCount(int totalRunCount) {
        job.setTotalRunCount(totalRunCount);
        return this;
    }

    public JobBuilder setIsLasted(boolean isLasted) {
        job.setLasted(isLasted);
        return this;
    }

    public Job build() {
        return job;
    }

}
