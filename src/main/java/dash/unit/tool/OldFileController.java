package dash.unit.tool;

import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.job.JobContainer;
import util.module.FileManager;

public class OldFileController extends JobContainer {

    private static final Logger logger = LoggerFactory.getLogger(OldFileController.class);

    private final String dashUnitId; // DashUnit ID
    private final String dashPath; // 현재 DASH Streaming 경로
    private final long limitTime; // 제한 시간

    private final String[] exceptFileNameList = new String[] { "init" };
    private final String[] exceptFileExtensionList = new String[] { "mpd" };

    private final FileManager fileManager = new FileManager();

    public OldFileController(Job oldFileControlJob, String dashUnitId, String dashPath) {
        setJob(oldFileControlJob);
        this.dashUnitId = dashUnitId;
        this.dashPath = dashPath;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        this.limitTime = configManager.getChunkFileDeletionIntervalSeconds();

        logger.debug("[DashUnit(id={})] OldFileController is initiated. (dashPath={}, timeLimit={})",
                dashUnitId, dashPath, limitTime
        );
    }

    public void start() {
        if (dashPath == null) { return; }

        getJob().setRunnable(() -> {
            try {
                fileManager.deleteOldFilesBySecond(
                        dashPath,
                        exceptFileNameList,
                        exceptFileExtensionList,
                        limitTime
                );
            } catch (Exception e) {
                logger.trace("[DashUnit(id={})] OldFileController.run.Exception", dashUnitId, e);
            }
        });
    }

}
