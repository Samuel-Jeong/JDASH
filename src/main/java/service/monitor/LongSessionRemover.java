package service.monitor;

import dash.server.DashServer;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.FileManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LongSessionRemover extends Job {

    private static final Logger logger = LoggerFactory.getLogger(LongSessionRemover.class);

    private final long limitTime;

    private final FileManager fileManager = new FileManager();

    public LongSessionRemover(ScheduleManager scheduleManager, String name, int initialDelay, int interval, TimeUnit timeUnit, int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        limitTime = AppInstance.getInstance().getConfigManager().getLocalSessionLimitTime();
    }

    @Override
    public void run() {
        try {
            DashServer dashServer = ServiceManager.getInstance().getDashServer();

            ///////////////////////////////////
            // 1) CHECK USELESS SESSION
            HashMap<String, DashUnit> dashUnitMap = dashServer.getCloneDashMap();
            if (!dashUnitMap.isEmpty()) {
                for (Map.Entry<String, DashUnit> entry : dashUnitMap.entrySet()) {
                    if (entry == null) {
                        continue;
                    }

                    DashUnit dashUnit = entry.getValue();
                    if (dashUnit == null) {
                        continue;
                    }

                    if (!dashUnit.getType().equals(StreamType.DYNAMIC)) { continue; }

                    long expires = dashUnit.getExpires();
                    if (expires <= 0) { continue; }

                    long curTime = System.currentTimeMillis();
                    if ((curTime - dashUnit.getInitiationTime()) >= expires) {
                        dashUnit.clearMpdPath();
                        dashServer.deleteDashUnit(dashUnit.getId());
                        logger.warn("({}) REMOVED USELESS DASH UNIT(DashUnit=\n{})", getName(), dashUnit);
                    }
                }
            }
            ///////////////////////////////////

            ///////////////////////////////////
            // 2) CHECK USELESS FILE
            /*List<String> dynamicStreamKeys = dashServer.getDynamicStreamPathList();
            File mediaBaseDir = new File(AppInstance.getInstance().getConfigManager().getMediaBasePath());
            if (mediaBaseDir.exists() && mediaBaseDir.isDirectory()) {
                File[] mediaDirs = mediaBaseDir.listFiles();
                if (mediaDirs == null || mediaDirs.length == 0) { return; }

                for (File mediaDir : mediaDirs) {
                    if (!mediaDir.getAbsolutePath().endsWith("live")) { continue; }
                    fileManager.deleteOldDirectoriesBySecond(
                            mediaDir,
                            dynamicStreamKeys,
                            limitTime
                    );
                }
            }*/
            ///////////////////////////////////
        } catch (Exception e) {
            logger.warn("({}) run.Exception", getName());
        }
    }

}
