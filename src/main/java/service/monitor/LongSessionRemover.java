package service.monitor;

import config.ConfigManager;
import dash.server.DashServer;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.job.JobContainer;
import util.module.FileManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LongSessionRemover extends JobContainer {

    private static final Logger logger = LoggerFactory.getLogger(LongSessionRemover.class);

    private final long sessionDeletionLimitTime;
    private final long dirDeletionLimitTime;

    private final ConfigManager configManager;
    private final FileManager fileManager = new FileManager();

    public LongSessionRemover(Job longSessionRemoveJob) {
        setJob(longSessionRemoveJob);
        configManager = AppInstance.getInstance().getConfigManager();
        sessionDeletionLimitTime = configManager.getAutoDeleteSessionLimitTime();
        dirDeletionLimitTime = configManager.getAutoDeleteDirLimitTime();
    }

    public void start() {
        getJob().setRunnable(() -> {
            try {
                DashServer dashServer = ServiceManager.getInstance().getDashServer();

                ///////////////////////////////////
                // 1) CHECK USELESS SESSION
                if (configManager.isEnableAutoDeleteUselessSession()) {
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

                            long curLimitTime = sessionDeletionLimitTime;
                            long expires = dashUnit.getExpires();
                            if (expires > 0) {
                                curLimitTime = expires;
                            }

                            long curTime = System.currentTimeMillis();
                            if ((curTime - dashUnit.getInitiationTime()) >= curLimitTime) {
                                dashUnit.clearMpdPath();
                                dashServer.deleteDashUnit(dashUnit.getId());
                                logger.warn("({}) REMOVED USELESS DASH UNIT(DashUnit=\n{})", getJob().getName(), dashUnit);
                            }
                        }
                    }
                }
                ///////////////////////////////////

                ///////////////////////////////////
                // 2) CHECK USELESS FILE
                if (configManager.isEnableAutoDeleteUselessDir()) {
                    List<String> dynamicStreamKeys = dashServer.getDynamicStreamPathList();
                    File mediaBaseDir = new File(configManager.getMediaBasePath());
                    if (mediaBaseDir.exists() && mediaBaseDir.isDirectory()) {
                        File[] mediaDirs = mediaBaseDir.listFiles();
                        if (mediaDirs == null || mediaDirs.length == 0) {
                            return;
                        }

                        for (File mediaDir : mediaDirs) {
                            if (!mediaDir.getAbsolutePath().endsWith("live")) {
                                continue;
                            }
                            fileManager.deleteOldDirectoriesBySecond(
                                    mediaDir,
                                    dynamicStreamKeys,
                                    dirDeletionLimitTime
                            );
                        }
                    }
                }
                ///////////////////////////////////
            } catch (Exception e) {
                logger.warn("({}) run.Exception", getJob().getName());
            }     
        });
    }

}
