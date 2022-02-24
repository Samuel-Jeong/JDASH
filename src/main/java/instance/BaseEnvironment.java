package instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.schedule.ScheduleManager;
import service.system.ResourceManager;

public class BaseEnvironment {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private static final Logger logger = LoggerFactory.getLogger(BaseEnvironment.class);
    private final ScheduleManager scheduleManager;
    private final ResourceManager portResourceManager;
    private DebugLevel debugLevel;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    // 프레임워크 전역 인스턴스
    // 1. JOB 스케줄링
    // 2. 포트 자원 관리 ()
    public BaseEnvironment(ScheduleManager scheduleManager, ResourceManager portResourceManager, DebugLevel debugLevel) {
        this.scheduleManager = scheduleManager;
        this.portResourceManager = portResourceManager;
        this.debugLevel = debugLevel;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public void start() {
        if (portResourceManager != null) {
            portResourceManager.initResource();
        }
    }

    public void stop() {
        if (scheduleManager != null) {
            scheduleManager.finish();
        }

        if (portResourceManager != null) {
            portResourceManager.releaseResource();
        }
    }

    public void printMsg(String msg, Object... parameters) {
        String log = String.format(msg, parameters);
        if (this.debugLevel == DebugLevel.INFO) {
            logger.info("{}", log);
        } else if (this.debugLevel == DebugLevel.DEBUG) {
            logger.debug("{}", log);
        } else if (this.debugLevel == DebugLevel.WARN) {
            logger.warn("{}", log);
        } else if (this.debugLevel == DebugLevel.ERROR) {
            logger.error("{}", log);
        }
    }

    public void printMsg(DebugLevel debugLevel, String msg, Object... parameters) {
        String log = String.format(msg, parameters);
        if (debugLevel == DebugLevel.INFO) {
            logger.info("{}", log);
        } else if (debugLevel == DebugLevel.DEBUG) {
            logger.debug("{}", log);
        } else if (debugLevel == DebugLevel.WARN) {
            logger.warn("{}", log);
        } else if (debugLevel == DebugLevel.ERROR) {
            logger.error("{}", log);
        }
    }

    public DebugLevel getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(DebugLevel debugLevel) {
        this.debugLevel = debugLevel;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public ResourceManager getPortResourceManager() {
        return portResourceManager;
    }

    @Override
    public String toString() {
        return "BaseEnvironment{" +
                "scheduleManager=" + scheduleManager +
                ", portResourceManager=" + portResourceManager +
                ", debugLevel=" + debugLevel +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
