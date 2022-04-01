package service.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Job implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Job.class);

    ////////////////////////////////////////////////////////////////////////////////
    private final ScheduleManager scheduleManager;
    private final String name;
    private final int initialDelay;
    private final int interval;
    private final TimeUnit timeUnit; // ex) TimeUnit.MILLISECONDS

    private final int priority;
    private final int totalRunCount;
    private final AtomicInteger curRemainRunCount = new AtomicInteger(0);
    private final boolean isLasted;
    private final AtomicBoolean isFinished = new AtomicBoolean(false);

    private String scheduleUnitKey;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public Job(ScheduleManager scheduleManager, String name, int initialDelay, int interval, TimeUnit timeUnit, int priority, int totalRunCount, boolean isLasted) {
        this.scheduleManager = scheduleManager;
        this.name = name;
        this.initialDelay = initialDelay;
        this.interval = interval;
        this.timeUnit = timeUnit;
        this.priority = priority;
        this.totalRunCount = totalRunCount;
        this.curRemainRunCount.set(totalRunCount);
        this.isLasted = isLasted;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public String getScheduleUnitKey() {
        return scheduleUnitKey;
    }

    public void setScheduleUnitKey(String scheduleUnitKey) {
        this.scheduleUnitKey = scheduleUnitKey;
    }

    public int getPriority() {
        return priority;
    }

    public int getTotalRunCount() {
        return totalRunCount;
    }

    public void setCurRemainRunCount(int count) {
        curRemainRunCount.set(count);
    }

    public int incCurRemainRunCount() {
        return curRemainRunCount.incrementAndGet();
    }

    public int decCurRemainRunCount() {
        return curRemainRunCount.decrementAndGet();
    }

    public int getCurRemainRunCount() {
        return curRemainRunCount.get();
    }

    public String getName() {
        return name;
    }

    public int getInitialDelay() {
        return initialDelay;
    }

    public int getInterval() {
        return interval;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public boolean isLasted() {
        return isLasted;
    }

    public boolean getIsFinished() {
        return isFinished.get();
    }

    public void setIsFinished(boolean isFinished) {
        this.isFinished.set(isFinished);
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    @Override
    public String toString() {
        return "Job{" +
                "name='" + name + '\'' +
                ", initialDelay=" + initialDelay +
                ", interval=" + interval +
                ", timeUnit=" + timeUnit +
                ", priority=" + priority +
                ", totalRunCount=" + totalRunCount +
                ", curRemainRunCount=" + curRemainRunCount.get() +
                ", isLasted=" + isLasted +
                ", isFinished=" + isFinished.get() +
                ", scheduleUnitKey=" + scheduleUnitKey +
                '}';
    }
    ////////////////////////////////////////////////////////////////////////////////
}
