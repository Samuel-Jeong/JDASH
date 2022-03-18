package util.fsm;

import util.fsm.module.StateHandler;
import util.fsm.module.StateTaskManager;
import util.fsm.unit.StateUnit;

import java.util.HashMap;
import java.util.Map;

/**
 * @class public class StateManager
 * @brief StateManager class
 * FSM 전체 구성 관리 클래스
 */
public class StateManager {

    // StateHandler Map
    private final HashMap<String, StateHandler> stateHandlerMap = new HashMap<>();

    // StateUnit Map
    private final HashMap<String, StateUnit> stateUnitMap = new HashMap<>();

    private final StateTaskManager stateTaskManager;

    private final int taskThreadMaxCount;

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public StateManager()
     * @brief StateManager 생성자 함수
     */
    public StateManager(int threadMaxCount) {
        // Nothing
        stateTaskManager = new StateTaskManager(this, threadMaxCount);
        this.taskThreadMaxCount = threadMaxCount;
    }

    public void stop () {
        stateTaskManager.stop();
    }

    public StateTaskManager getStateTaskManager() {
        return stateTaskManager;
    }

    ////////////////////////////////////////////////////////////////////////////////
    /**
     * @fn public void addStateUnit (String name, String handlerName, String initState, Object data)
     * @brief StateUnit 을 새로 추가하는 함수
     * @param name StateUnit 이름
     * @param initState 초기 상태
     */
    public void addStateUnit (String name, String handlerName, String initState, Object data) {
        synchronized (stateUnitMap) {
            if (stateUnitMap.get(name) != null) {
                return;
            }

            StateUnit stateUnit = new StateUnit(name, handlerName, initState, data);
            stateUnit.setIsAlive(true);
            stateUnitMap.putIfAbsent(name, stateUnit);
        }
    }

    /**
     * @fn public boolean removeStateUnit (String name)
     * @brief 지정한 이름의 StateUnit 을 삭제하는 함수
     * @param name StateUnit 이름
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean removeStateUnit (String name) {
        synchronized (stateUnitMap) {
            StateUnit stateUnit = stateUnitMap.get(name);
            if (stateUnit == null) {
                return false;
            }

            stateUnit.setIsAlive(false);
            return stateUnitMap.remove(name) != null;
        }
    }

    /**
     * @fn public StateHandler getStateUnit (String name)
     * @brief 지정한 이름의 StateUnit 을 반환하는 함수
     * @param name StateUnit 이름
     * @return 성공 시 StateUnit 객체, 실패 시 null 반환
     */
    public StateUnit getStateUnit (String name) {
        synchronized (stateUnitMap) {
            return stateUnitMap.get(name);
        }
    }

    public Map<String, StateUnit> cloneStateUnitMap() {
        HashMap<String, StateUnit> cloneMap;
        synchronized (stateUnitMap) {
            try {
                cloneMap = (HashMap<String, StateUnit>) stateUnitMap.clone();
            } catch (Exception e) {
                cloneMap = stateUnitMap;
            }
        }
        return cloneMap;
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public void addStateHandler (String name)
     * @brief StateHandler 를 새로 추가하는 함수
     * @param name StateHandler 이름
     */
    public void addStateHandler (String name) {
        synchronized (stateHandlerMap) {
            if (stateHandlerMap.get(name) != null) { return; }
            stateHandlerMap.putIfAbsent(
                    name,
                    new StateHandler(
                            stateTaskManager,
                            name
                    )
            );
        }
    }

    /**
     * @fn public boolean removeStateHandler (String name)
     * @brief 지정한 이름의 StateHandler 를 삭제하는 함수
     * @param name StateHandler 이름
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean removeStateHandler (String name) {
        synchronized (stateHandlerMap) {
            StateHandler stateHandler = stateHandlerMap.get(name);
            if (stateHandler == null) {
                return false;
            }

            stateHandler.clearStateEventManager();
            return stateHandlerMap.remove(name) != null;
        }
    }

    /**
     * @fn public StateHandler getStateHandler (String name)
     * @brief 지정한 이름의 StateHandler 를 반환하는 함수
     * @param name StateHandler 이름
     * @return 성공 시 StateHandler 객체, 실패 시 null 반환
     */
    public StateHandler getStateHandler (String name) {
        synchronized (stateHandlerMap) {
            return stateHandlerMap.get(name);
        }
    }

    public int getTotalEventSize () {
        synchronized (stateHandlerMap) {
            int totalSize = 0;

            for (StateHandler stateHandler : stateHandlerMap.values()) {
                if (stateHandler == null) { continue; }
                totalSize += stateHandler.getTotalEventSize();
            }

            return totalSize;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getTaskThreadMaxCount() {
        return taskThreadMaxCount;
    }

}
