package service;

import dash.DashManager;
import dash.unit.DashUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LongSessionRemover extends Job {

    private static final Logger logger = LoggerFactory.getLogger(LongSessionRemover.class);

    private final long limitTime;

    public LongSessionRemover(ScheduleManager scheduleManager, String name, int initialDelay, int interval, TimeUnit timeUnit, int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        limitTime = AppInstance.getInstance().getConfigManager().getLocalSessionLimitTime();
    }

    @Override
    public void run() {
        HashMap<String, DashUnit> rtspUnitMap = DashManager.getInstance().getCloneDashMap();
        if (!rtspUnitMap.isEmpty()) {
            for (Map.Entry<String, DashUnit> entry : rtspUnitMap.entrySet()) {
                if (entry == null) {
                    continue;
                }

                DashUnit dashUnit = entry.getValue();
                if (dashUnit == null) {
                    continue;
                }

                long curTime = System.currentTimeMillis();
                if ((curTime - dashUnit.getInitiationTime()) >= limitTime) {
                    DashManager.getInstance().deleteDashUnit(dashUnit.getId());
                    logger.warn("({}) REMOVED LONG SESSION(DashUnit=\n{})", getName(), dashUnit);
                }
            }
        }
    }
    
}
