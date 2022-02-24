package service.scheduler.schedule.handler;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import service.scheduler.job.Job;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class JobExecutor {

    private final int index;

    private final PriorityBlockingQueue<Job> priorityQueue;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    ////////////////////////////////////////////////////////////////////////////////

    public JobExecutor(int index, int queueSize) {
        this.index = index;

        priorityQueue = new PriorityBlockingQueue<>(
                queueSize,
                Comparator.comparing(Job::getPriority)
        );

        ThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern("JobExecutor" + "-" + index).daemon(true).build();
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new Worker(),
                0,
                1,
                TimeUnit.MILLISECONDS
        );
    }

    ////////////////////////////////////////////////////////////////////////////////

    private class Worker implements Runnable {

        @Override
        public void run() {
            try {
                // poll(): dequeue 후 객체 null 여부에 상관없이 기다리지 않음
                // take(): dequeue 후 객체가 null 이 아닐 때까지 기다림
                Job job = priorityQueue.poll();
                if (job == null) {
                    return;
                }

                job.run();
            } catch (Exception e) {
                // ignore
            }
        }

    }

    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
        priorityQueue.clear();
    }

    public void addJob(Job job) {
        priorityQueue.offer(job);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getIndex() {
        return index;
    }
}
