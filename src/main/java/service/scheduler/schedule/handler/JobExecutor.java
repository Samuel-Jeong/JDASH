package service.scheduler.schedule.handler;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class JobExecutor {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);

    private final String scheduleUnitKey;
    private final int index;

    private final PriorityBlockingQueue<Job> priorityQueue;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public JobExecutor(String scheduleUnitKey, int index, int queueSize) {
        this.scheduleUnitKey = scheduleUnitKey;
        this.index = index;

        priorityQueue = new PriorityBlockingQueue<>(
                queueSize,
                Comparator.comparing(Job::getPriority)
        );

        ThreadFactory threadFactory = new BasicThreadFactory
                .Builder()
                .namingPattern(scheduleUnitKey + "_JobExecutor" + "-" + index)
                .daemon(true)
                .build();

        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new Worker(),
                0,
                1,
                TimeUnit.MILLISECONDS
        );
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private class Worker implements Runnable {

        @Override
        public void run() {
            try {
                // poll(): dequeue 후 객체 null 여부에 상관없이 기다리지 않음
                // take(): dequeue 후 객체가 null 이 아닐 때까지 기다림
                Job job = priorityQueue.poll();
                if (job == null) { return; }

                Runnable runnable = job.getRunnable();
                if (runnable == null) { return; }

                runnable.run();
                if (!job.isLasted()) {
                    job.decCurRemainRunCount();
                    if (job.getCurRemainRunCount() < 0) {
                        job.setIsFinished(true);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

    }

    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
        priorityQueue.clear();
    }

    public boolean addJob(Job job) {
        return priorityQueue.offer(job);
    }

    public int getIndex() {
        return index;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
