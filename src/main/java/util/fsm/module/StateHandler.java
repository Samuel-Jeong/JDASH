package util.fsm.module;

import util.fsm.event.StateEventManager;
import util.fsm.event.base.CallBack;
import util.fsm.event.base.StateEvent;
import util.fsm.module.base.EventCondition;
import util.fsm.unit.StateUnit;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @class public class StateHandler
 * @brief StateHandler class
 * FSM 시나리오 전체를 운용하는 클래스
 */
public class StateHandler {

    // StateTaskManager
    private final StateTaskManager stateTaskManager;

    // StateEventManager
    private final StateEventManager stateEventManager;

    // StateHandler 이름
    private final String name;

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public StateHandler(String name)
     * @brief StateHandler 생성자 함수
     * @param name StateHandler 이름
     */
    public StateHandler(StateTaskManager stateTaskManager, String name) {
        this.stateTaskManager = stateTaskManager;
        this.name = name;

        stateEventManager = new StateEventManager(stateTaskManager);
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public boolean addState (String event, String fromState, String toState, CallBack successCallBack, CallBack failCallBack, String nextEvent, int delay, Object... params)
     * @brief 새로운 State 를 추가하는 함수
     * fromState 가 toState 로 천이되기 위한 trigger 이벤트와 천이 후에 실행될 CallBack 을 정의한다.
     *
     * 1) 천이 성공 시 지정한 CallBack 실행
     * 2) 천이 실패 시 지정한 CallBack 실행
     * 3) 천이 실패 시 timeout 후 event 실행 (TaskUnit 필요)
     *
     * @param event Trigger 될 이벤트 이름
     * @param fromState 천이 전 State 이름
     * @param toState 천이 후 State 이름
     * @param successCallBack 천이 성공 후 실행될 CallBack
     * @param failCallBack 천이 실패 후 실행될 CallBack
     * @param nextEvent 천이 후 다음에 실행될 이벤트 이름
     * @param delay 천이 실패 후 실행될 이벤트가 실행되기 위한 Timeout 시간
     * @param nextEventRetryCount 천이 실패 후 실행될 이벤트 재시도 횟수
     * @param nextEventCallBackParams 실패 후 실행될 이벤트의 CallBack 의 매개변수
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean addState (String event,
                             String fromState,
                             String toState,
                             CallBack successCallBack,
                             CallBack failCallBack,
                             String nextEvent,
                             int delay,
                             int nextEventRetryCount,
                             Object... nextEventCallBackParams) {
        HashSet<String> fromStateSet = new HashSet<>();
        fromStateSet.add(fromState);

        return stateEventManager.addEvent(
                event,
                fromStateSet, toState, successCallBack,
                failCallBack,
                nextEvent, delay, nextEventRetryCount, nextEventCallBackParams
        );
    }

    /**
     * @fn public boolean addState (String event, HashSet<String> fromStateSet, String toState, CallBack successCallBack, CallBack failCallBack, String nextEvent, int delay, Object... params)
     * @brief 새로운 State 를 추가하는 함수
     * fromState 가 toState 로 천이되기 위한 trigger 이벤트와 천이 후에 실행될 CallBack 을 정의한다.
     *
     * 1) 천이 성공 시 지정한 CallBack 실행
     * 2) 천이 실패 시 지정한 CallBack 실행
     * 3) 천이 실패 시 timeout 후 event 실행 (TaskUnit 필요)
     *
     * @param event Trigger 될 이벤트 이름
     * @param fromStateSet 천이 전 State Set
     * @param toState 천이 후 State 이름
     * @param successCallBack 천이 성공 후 실행될 CallBack
     * @param failCallBack 천이 실패 후 실행될 CallBack
     * @param nextEvent 천이 후 다음에 실행될 이벤트 이름
     * @param delay 천이 실패 후 실행될 이벤트가 실행되기 위한 Timeout 시간
     * @param nextEventRetryCount 천이 실패 후 실행될 이벤트 재시도 횟수
     * @param nextEventCallBackParams 실패 후 실행될 이벤트의 CallBack 의 매개변수
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean addState (String event,
                             HashSet<String> fromStateSet,
                             String toState,
                             CallBack successCallBack,
                             CallBack failCallBack,
                             String nextEvent,
                             int delay,
                             int nextEventRetryCount,
                             Object... nextEventCallBackParams) {
        return stateEventManager.addEvent(
                event,
                fromStateSet, toState, successCallBack,
                failCallBack,
                nextEvent, delay, nextEventRetryCount, nextEventCallBackParams
        );
    }

    public void clearStateEventManager() {
        stateEventManager.removeAllEvents();
    }

    /**
     * @fn public String getName()
     * @brief StateHandler 이름을 반환하는 함수
     * @return StateHandler 이름
     */
    public String getName() {
        return name;
    }

    public StateEvent getEvent (String event) {
        return stateEventManager.getStateEventByEvent(event);
    }

    /**
     * @fn public boolean removeState(String fromState)
     * @brief State 를 삭제하는 함수
     * @param event 이벤트 이름
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean removeState(String event) {
        return stateEventManager.removeEvent(event);
    }

    /**
     * @fn public List<String> getEventList ()
     * @brief StateEventManager 에 정의된 모든 이벤트들을 새로운 리스트에 저장하여 반환하는 함수
     * @return 성공 시 정의된 이벤트 리스트, 실패 시 null 반환
     */
    public List<String> getEventList () {
        return stateEventManager.getAllEvents();
    }

    public Map<String, StateEvent> cloneEventMap () {
        return stateEventManager.cloneEventMap();
    }

    public int getTotalEventSize () {
        return stateEventManager.getAllEvents().size();
    }

    /**
     * @fn public String fire (String event, StateUnit stateUnit, Object... params)
     * @brief 정의된 State 천이를 위해 지정한 이벤트를 발생시키는 함수
     * @param event 발생할 이벤트 이름
     * @param stateUnit State unit
     * @return 성공 시 천이 후 상태값 반환, 실패 시 null 또는 천이 전 상태값 반환
     */
    public String fire (String event, StateUnit stateUnit) {
        return stateEventManager.nextState(this, event, stateUnit, false);
    }

    /**
     * @fn public String retry(String event, StateUnit stateUnit, Object... params)
     * @brief 정의된 State 천이를 위해 지정한 이벤트를 다시 발생시키는 함수
     * @param event 발생할 이벤트 이름
     * @param stateUnit State unit
     * @return 성공 시 천이 후 상태값 반환, 실패 시 null 또는 천이 전 상태값 반환
     */
    public String handle(String event, StateUnit stateUnit) {
        return stateEventManager.nextState(this, event, stateUnit, true);
    }

    /**
     * @fn public StateEvent findStateEventByEvent(String event)
     * @brief 지정한 이벤트에 등록된 StateEvent 을 찾아서 반환하는 함수
     * @param event 이벤트 이름
     * @return 성공 시 StateEvent, 실패 시 null 반환
     */
    public StateEvent findStateEventByEvent(String event) {
        return stateEventManager.getStateEventByEvent(event);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void addEventCondition(EventCondition eventCondition, int delay) {
        stateTaskManager.addStateScheduler(this, eventCondition, delay);
    }

}
