package dash.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;

import java.util.concurrent.TimeUnit;

public class HttpMessageConsumer extends Job {

    private static final Logger logger = LoggerFactory.getLogger(HttpMessageConsumer.class);

    private final ConcurrentCyclicFIFO<Object[]> httpMessageQueue;

    public HttpMessageConsumer(ScheduleManager scheduleManager,
                               String name,
                               int initialDelay, int interval, TimeUnit timeUnit,
                               int priority, int totalRunCount, boolean isLasted,
                               ConcurrentCyclicFIFO<Object[]> httpMessageQueue) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);
        this.httpMessageQueue = httpMessageQueue;
    }

    @Override
    public void run() {
        try {
            // TODO
            Object[] data = httpMessageQueue.take();

        } catch (InterruptedException e) {
            logger.error("HttpMessageConsumer.queueProcessing.Exception", e);
        }
    }

}
