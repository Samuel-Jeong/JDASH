package dash.unit.tool;

import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class OldFileController extends Job {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(OldFileController.class);

    private final String dashUnitId; // DashUnit ID
    private final String dashPath; // 현재 DASH Streaming 경로
    private final long limitTime; // 제한 시간

    private final String[] exceptFileNameList = new String[] { "init" };
    private final String[] exceptFileExtensionList = new String[] { "mpd" };

    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public OldFileController(ScheduleManager scheduleManager, String name,
                             int initialDelay, int interval, TimeUnit timeUnit,
                             int priority, int totalRunCount, boolean isLasted,
                             String dashUnitId, String dashPath) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.dashUnitId = dashUnitId;
        this.dashPath = dashPath;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        this.limitTime = configManager.getChunkFileDeletionIntervalSeconds();

        logger.debug("[DashUnit(id={})] OldFileController is initiated. (dashPath={}, timeLimit={})",
                dashUnitId, dashPath, limitTime
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void run() {
        if (dashPath == null) { return; }

        try {
            fileManager.deleteOldFilesBySecond(
                    dashPath,
                    exceptFileNameList,
                    exceptFileExtensionList,
                    limitTime
            );
        } catch (Exception e) {
            //logger.warn("[DashUnit(id={})] OldFileController.run.Exception", dashUnitId, e);
        }
    }
    ////////////////////////////////////////////////////////////

}
