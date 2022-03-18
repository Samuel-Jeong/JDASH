package util.fsm.event.base;

import util.fsm.StateManager;
import util.fsm.unit.StateUnit;

/**
 * @class public abstract class CallBack
 * @brief CallBack class
 * 이벤트 실행 시 사용자가 직접 정의 가능한 동작 클래스
 */
public abstract class CallBack {

    private final StateManager stateManager;
    private final String name;

    ////////////////////////////////////////////////////////////////////////////////

    protected CallBack(StateManager stateManager, String name) {
        this.stateManager = stateManager;
        this.name = name;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public Object callBackFunc(StateUnit stateUnit) {
        // Must be implemented.
        return null;
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "CallBack{" +
                "name='" + name + '\'' +
                '}';
    }
}
