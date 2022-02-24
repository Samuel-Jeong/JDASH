package service.scheduler.schedule.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;

public class JobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    ////////////////////////////////////////////////////////////////////////////////

    private final String ownerName;
    private final int poolSize;
    private final int queueSize;

    private final JobExecutor[] jobExecutors; // Round-Robin executor selection
    private int curExecutorIndex = 0;

    ////////////////////////////////////////////////////////////////////////////////

    public JobScheduler(String ownerName, int poolSize, int queueSize) {
        this.ownerName = ownerName;
        this.poolSize = poolSize;
        this.queueSize = queueSize;

        jobExecutors = new JobExecutor[poolSize];
        for (int i = 0; i < poolSize; i++) {
            jobExecutors[i] = new JobExecutor(i, queueSize);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public boolean schedule(Job job) {
        if (!job.getIsInitialFinished()) {
            int initialDelay = job.getInitialDelay();
            if (initialDelay > 0) {
                job.initialSchedule();
                return true;
            }
        }

        if (isJobFinished(job)) {
            return false;
        }

        addJobToExecutor(job);
        int interval = job.getInterval();
        if (interval > 0) {
            job.schedule();
        }

        return true;
    }

    public void cancel(Job job) {
        job.setIsFinished(true);
    }

    public void stop() {
        for (int i = 0; i < poolSize; i++) {
            jobExecutors[i].stop();
        }

        logger.debug("[{}] is finished.", ownerName);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void addJobToExecutor(Job job) {
        jobExecutors[curExecutorIndex].addJob(job);

        curExecutorIndex++;
        if (curExecutorIndex >= poolSize) {
            curExecutorIndex = 0;
        }
    }

    public boolean isJobFinished(Job job) {
        if (job == null) {
            return true;
        }

        return job.getIsFinished() ||
                (!job.isLasted() && (job.decCurRemainRunCount() < 0));
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getCurExecutorIndex() {
        return curExecutorIndex;
    }

    @Override
    public String toString() {
        return "JobScheduler{" +
                "ownerName='" + ownerName + '\'' +
                ", poolSize=" + poolSize +
                ", queueSize=" + queueSize +
                '}';
    }
}
