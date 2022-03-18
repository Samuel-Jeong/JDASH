package util.fsm.module.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.event.retry.RetryManager;
import util.fsm.event.retry.base.RetryStatus;
import util.fsm.module.StateHandler;
import util.fsm.module.StateTaskManager;
import util.fsm.unit.StateUnit;

/**
 * @class public class StateTaskUnit extends AbstractStateTaskUnit
 * @brief StateTaskUnit class
 * 스케줄된 이벤트 클래스
 */
public class StateTaskUnit extends AbstractStateTaskUnit {

    private static final Logger logger = LoggerFactory.getLogger(StateTaskUnit.class);

    // Name
    private final String name;
    // StateTaskManager
    private final StateTaskManager stateTaskManager;
    // StateHandler
    private final StateHandler stateHandler;
    // Event
    private final String event;
    // StateUnit
    private final StateUnit stateUnit;

    ////////////////////////////////////////////////////////////////////////////////

    public StateTaskUnit(String name,
                         StateTaskManager stateTaskManager,
                         StateHandler stateHandler,
                         String event,
                         StateUnit stateUnit,
                         int interval) {
        super(interval);

        this.name = name;
        this.stateTaskManager = stateTaskManager;
        this.stateHandler = stateHandler;
        this.event = event;
        this.stateUnit = stateUnit;
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void run() {
        logger.debug("({}) StateTaskUnit is started. (event={}, stateUnit={}, delay={})",
                stateHandler.getName(), event, stateUnit, getInterval()
        );

        // 1) 지정한 이벤트 실행
        stateHandler.handle(
                event,
                stateUnit
        );

        stateTaskManager.removeStateTaskUnit(stateHandler.getName(), name);

        // 2) 재시도 진행 중이면, 동일한 StateTaskUnit 정보로 StateTaskUnit 을 스케줄링한다.
        RetryManager retryManager = stateTaskManager.getRetryManager();
        RetryStatus retryStatus = retryManager.getRetryStatus(name);

        if (retryStatus == RetryStatus.ONGOING) {
            stateTaskManager.addStateTaskUnit(
                    stateHandler.getName(),
                    name,
                    new StateTaskUnit(
                            name,
                            stateTaskManager,
                            stateHandler,
                            event,
                            stateUnit,
                            getInterval()
                    ),
                    0
            );
        }
    }

}
