package service.monitor;

import dash.unit.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.job.JobContainer;
import service.system.SystemManager;

/**
 * @author jamesj
 * @class public class ServiceHaHandler extends TaskUnit
 * @brief ServiceHaHandler
 */
public class HaHandler extends JobContainer {

    private static final Logger logger = LoggerFactory.getLogger(HaHandler.class);

    public HaHandler(Job haHandleJob) {
        setJob(haHandleJob);
    }

    public void start () {
        getJob().setRunnable(() -> {
            SystemManager systemManager = SystemManager.getInstance();

            String cpuUsageStr = systemManager.getCpuUsage();
            String memoryUsageStr = systemManager.getHeapMemoryUsage();

            logger.debug("| cpu=[{}], mem=[{}], thread=[{}] | DashUnitCount=[S:{}/D:{}]",
                    cpuUsageStr, memoryUsageStr, Thread.activeCount(),
                    ServiceManager.getInstance().getDashServer().getDashUnitMapSizeWithStreamType(StreamType.STATIC),
                    ServiceManager.getInstance().getDashServer().getDashUnitMapSizeWithStreamType(StreamType.DYNAMIC)
            );
        });
    }

}
