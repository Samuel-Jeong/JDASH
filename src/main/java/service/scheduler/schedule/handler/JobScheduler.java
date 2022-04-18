package service.scheduler.schedule.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.unit.JobAdder;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class JobScheduler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private final String scheduleUnitKey;
    private final int poolSize;
    private final int queueSize;

    private final HashMap<String, JobAdder> scheduleMap = new HashMap<>();
    private final ReentrantLock scheduleLock = new ReentrantLock();

    private final JobExecutor[] jobExecutors; // Round-Robin executor selection
    private final ReentrantLock executorLock = new ReentrantLock();
    private int curExecutorIndex = 0;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public JobScheduler(String scheduleUnitKey, int poolSize, int queueSize) {
        this.scheduleUnitKey = scheduleUnitKey;
        this.poolSize = poolSize;
        this.queueSize = queueSize;

        jobExecutors = new JobExecutor[poolSize];
        for (int i = 0; i < poolSize; i++) {
            jobExecutors[i] = new JobExecutor(scheduleUnitKey, i, queueSize);
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public boolean schedule(Job job) {
        if (job == null) { return false; }

        scheduleLock.lock();
        try {
            if (job.isLasted() && job.getInterval() <= 0) {
                logger.warn("[JobScheduler({})] Fail to start [{}]. Job is lasted, but interval is not positive. (interval={})",
                        scheduleUnitKey,
                        job.getName(), job.getInterval()
                );
                return false;
            }

            if (scheduleMap.get(scheduleUnitKey + ":" + job.getName()) != null) {
                logger.warn("[JobScheduler({})] Job is already scheduled. ({})", scheduleUnitKey, job.getName());
                return false;
            }

            JobAdder jobAdder = new JobAdder(this, job, curExecutorIndex);
            jobAdder.run();
            curExecutorIndex++;
            if (curExecutorIndex >= poolSize) {
                curExecutorIndex = 0;
            }
            scheduleMap.put(
                    scheduleUnitKey + ":" + job.getName(),
                    jobAdder
            );
            logger.debug("[JobScheduler({})] [{}] is started.", scheduleUnitKey, job.getName());
        } catch (Exception e) {
            logger.warn("[JobScheduler({})] Fail to schedule the job. ({})", scheduleUnitKey, job.getName(), e);
            return false;
        } finally {
            scheduleLock.unlock();
        }

        return true;
    }

    public void cancel(Job job) {
        if (job == null) { return; }

        scheduleLock.lock();
        try {
            String jobKey = scheduleUnitKey + ":" + job.getName();
            JobAdder jobAdder = scheduleMap.get(jobKey);
            if (jobAdder != null) {
                jobAdder.stop();
                job.setIsFinished(true);
                scheduleMap.remove(jobKey);
                logger.debug("[JobScheduler({})] [{}] is canceled.", scheduleUnitKey, job.getName());
            } else {
                logger.warn("[JobScheduler({})] [{}] is not canceled. Not found the job.", scheduleUnitKey, job.getName());
            }
        } catch (Exception e) {
            logger.warn("[JobScheduler({})] Fail to cancel the job. ({})", scheduleUnitKey, job.getName(), e);
        } finally {
            scheduleLock.unlock();
        }
    }

    public void stop() {
        scheduleLock.lock();
        try {
            scheduleMap.values().forEach(JobAdder::stop);
            scheduleMap.clear();
            logger.debug("[JobScheduler({})] Success to stop all the jobs.", scheduleUnitKey);
        } catch (Exception e) {
            logger.warn("[JobScheduler({})] Fail to stop the jobs.", scheduleUnitKey, e);
        } finally {
            scheduleLock.unlock();
        }

        executorLock.lock();
        try {
            for (int i = 0; i < poolSize; i++) {
                jobExecutors[i].stop();
            }
        } catch (Exception e) {
            logger.warn("[JobScheduler({})] Fail to stop the job executors. Exception", scheduleUnitKey, e);
        } finally {
            executorLock.unlock();
        }

        logger.debug("[JobScheduler({})] is finished.", scheduleUnitKey);
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void addJobToExecutor(int executorIndex, Job job) {
        executorLock.lock();
        try {
            jobExecutors[executorIndex].addJob(job);
            //logger.debug("jobExecutor[{}] add job ({})", curExecutorIndex, job.getName());
        } catch (Exception e) {
            logger.warn("[JobScheduler({})] Fail to add the job to executors. Exception", scheduleUnitKey, e);
        } finally {
            executorLock.unlock();
        }
    }

    public int getScheduledJobCount() {
        return scheduleMap.size();
    }

    @Override
    public String toString() {
        return "JobScheduler{" +
                "scheduleUnitKey='" + scheduleUnitKey + '\'' +
                ", poolSize=" + poolSize +
                ", queueSize=" + queueSize +
                '}';
    }
    ////////////////////////////////////////////////////////////////////////////////

}
