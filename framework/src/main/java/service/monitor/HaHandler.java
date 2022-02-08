package service.monitor;

import dash.DashManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import service.system.SystemManager;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesj
 * @class public class ServiceHaHandler extends TaskUnit
 * @brief ServiceHaHandler
 */
public class HaHandler extends Job {

    private static final Logger logger = LoggerFactory.getLogger(HaHandler.class);

    ////////////////////////////////////////////////////////////////////////////////

    public HaHandler(ScheduleManager scheduleManager,
                     String name,
                     int initialDelay, int interval, TimeUnit timeUnit,
                     int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void run () {
        SystemManager systemManager = SystemManager.getInstance();

        String cpuUsageStr = systemManager.getCpuUsage();
        String memoryUsageStr = systemManager.getHeapMemoryUsage();

        logger.debug("| cpu=[{}], mem=[{}], thread=[{}] | DashUnitCount=[{}]",
                cpuUsageStr, memoryUsageStr, Thread.activeCount(),
                DashManager.getInstance().getDashUnitMapSize()
        );
    }

}
