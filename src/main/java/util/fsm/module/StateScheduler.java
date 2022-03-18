package util.fsm.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.StateManager;
import util.fsm.event.base.StateEvent;
import util.fsm.module.base.AbstractStateTaskUnit;
import util.fsm.module.base.EventCondition;
import util.fsm.unit.StateUnit;

import java.util.HashSet;
import java.util.Map;

/**
 * @class public class StateScheduler
 * @brief StateScheduler class
 * 주기적으로 이벤트 실행시키는 클래스
 */
public class StateScheduler extends AbstractStateTaskUnit {

    private static final Logger logger = LoggerFactory.getLogger(StateScheduler.class);

    private final StateManager stateManager;
    private final StateHandler stateHandler;
    private final String handlerName;

    private final EventCondition eventCondition;

    ////////////////////////////////////////////////////////////////////////////////

    protected StateScheduler(StateManager stateManager, StateHandler stateHandler, EventCondition eventCondition, int delay) {
        super(delay);

        this.stateManager = stateManager;
        this.stateHandler = stateHandler;
        this.handlerName = stateHandler.getName();
        this.eventCondition = eventCondition;
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void run() {
        try {
            Thread.currentThread().setName(stateHandler.getName());

            if (eventCondition == null) {
                return;
            }

            StateEvent stateEvent = eventCondition.getStateEvent();
            if (stateEvent == null) { return; }

            HashSet<String> fromStateSet = stateEvent.getFromStateSet();

            // 1) 현재 StateManager 에 등록된 StateUnit Map 을 가져온다.
            Map<String, StateUnit> stateUnitMap = stateManager.cloneStateUnitMap();
            if (stateUnitMap.isEmpty()) { return; }

            for (StateUnit stateUnit : stateUnitMap.values()) {
                if (stateUnit == null || !stateUnit.getIsAlive()) { continue; }

                // 2) StateUnit 의 StateHandler 이름과 다르면 다른 StateUnit 검색
                if (!stateUnit.getHandlerName().equals(handlerName)) { continue; }

                eventCondition.setCurStateUnit(stateUnit);
                if (fromStateSet.contains(stateUnit.getCurState()) && eventCondition.checkCondition()) {
                    new Thread(() -> {
                        logger.debug("(StateScheduler-{}) Event is triggered by scheduler. (event={}, stateUnit={})",
                                handlerName, stateEvent, stateUnit
                        );

                        stateHandler.fire(
                                stateEvent.getName(),
                                stateUnit
                        );
                    }).start();
                }
            }
        } catch (Exception e) {
            logger.warn("({}) StateScheduler.run.Exception", handlerName, e);
        }
    }

}
