package service.monitor;

import dash.unit.DashUnit;
import network.user.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
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
        HashMap<String, DashUnit> dashUnitMap = ServiceManager.getInstance().getDashManager().getCloneDashMap();
        if (!dashUnitMap.isEmpty()) {
            for (Map.Entry<String, DashUnit> entry : dashUnitMap.entrySet()) {
                if (entry == null) {
                    continue;
                }

                DashUnit dashUnit = entry.getValue();
                if (dashUnit == null) {
                    continue;
                }

                long curTime = System.currentTimeMillis();
                if ((curTime - dashUnit.getInitiationTime()) >= limitTime) {
                    ServiceManager.getInstance().getDashManager().deleteDashUnit(dashUnit.getId());
                    logger.warn("({}) REMOVED LONG DASH UNIT(DashUnit=\n{})", getName(), dashUnit);
                }
            }
        }

        HashMap<String, UserInfo> userInfoMap = ServiceManager.getInstance().getDashManager().getUserManager().getCloneUserInfoMap();
        if (!userInfoMap.isEmpty()) {
            for (Map.Entry<String, UserInfo> entry : userInfoMap.entrySet()) {
                if (entry == null) {
                    continue;
                }

                UserInfo userInfo = entry.getValue();
                if (userInfo == null) {
                    continue;
                }

                long curTime = System.currentTimeMillis();
                if ((curTime - userInfo.getCreatedTime()) >= limitTime) {
                    ServiceManager.getInstance().getDashManager().getUserManager().deleteUserInfo(userInfo.getUserId());
                    logger.warn("({}) REMOVED LONG USER INFO(UserInfo=\n{})", getName(), userInfo);
                }
            }
        }
    }
    
}
