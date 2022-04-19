package service.scheduler.job;

import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Job {

    private ScheduleManager scheduleManager = null;
    private String name = null;
    private int initialDelay = 0;
    private int interval = 0;
    private TimeUnit timeUnit = null; // ex) TimeUnit.MILLISECONDS

    private int priority = 0;
    private int totalRunCount = 0;
    private boolean isLasted = false;
    private final AtomicInteger curRemainRunCount = new AtomicInteger(0);
    private final AtomicBoolean isFinished = new AtomicBoolean(false);

    private String scheduleUnitKey = null;
    private Runnable runnable = null;

    public Job() {
        // Nothing
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public void setScheduleManager(ScheduleManager scheduleManager) {
        this.scheduleManager = scheduleManager;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getTotalRunCount() {
        return totalRunCount;
    }

    public void setTotalRunCount(int totalRunCount) {
        this.totalRunCount = totalRunCount;
    }

    public int getCurRemainRunCount() {
        return curRemainRunCount.get();
    }

    public void setCurRemainRunCount(int curRemainRunCount) {
        this.curRemainRunCount.set(curRemainRunCount);
    }

    public int incCurRemainRunCount() {
        return curRemainRunCount.incrementAndGet();
    }

    public int decCurRemainRunCount() {
        return curRemainRunCount.decrementAndGet();
    }

    public boolean isLasted() {
        return isLasted;
    }

    public void setLasted(boolean lasted) {
        isLasted = lasted;
    }

    public boolean getIsFinished() {
        return isFinished.get();
    }

    public void setIsFinished(boolean isFinished) {
        this.isFinished.set(isFinished);
    }

    public String getScheduleUnitKey() {
        return scheduleUnitKey;
    }

    public void setScheduleUnitKey(String scheduleUnitKey) {
        this.scheduleUnitKey = scheduleUnitKey;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
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

}
