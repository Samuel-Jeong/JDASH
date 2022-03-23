package util.fsm.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.event.base.CallBack;
import util.fsm.event.base.StateEvent;
import util.fsm.event.retry.RetryManager;
import util.fsm.event.retry.base.RetryStatus;
import util.fsm.info.ResultCode;
import util.fsm.module.StateHandler;
import util.fsm.module.StateTaskManager;
import util.fsm.module.base.StateTaskUnit;
import util.fsm.unit.StateUnit;

import java.util.*;

/**
 * @class public class StateEventManager
 * @brief StateEventManager class
 */
public class StateEventManager {

    private static final Logger logger = LoggerFactory.getLogger(StateEventManager.class);

    private final StateTaskManager stateTaskManager;

    // Event Map
    private final HashMap<String, StateEvent> eventMap = new HashMap<>();

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public StateEventManager()
     * @brief StateEventManager 생성자 함수
     */
    public StateEventManager(StateTaskManager stateTaskManager) {
        this.stateTaskManager = stateTaskManager;
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public boolean addEvent(String event, String fromState, String toState, CallBack successCallBack, CallBack failCallBack, String nextEvent, int delay, Object... params)
     * @brief 새로운 이벤트를 생성하는 함수
     * @param event 이벤트 이름
     * @param fromStateSet 천이 전 State Set
     * @param toState 천이 후 State 이름
     * @param successCallBack 천이 성공 후 실행될 CallBack
     * @param failCallBack 천이 실패 후 실행될 CallBack
     * @param nextEvent 천이 실패 후 실행될 이벤트 이름
     * @param delay 천이 실패 후 실행될 이벤트가 실행되기 위한 Timeout 시간
     * @param nextEventRetryCount 천이 실패 후 실행될 이벤트 재시도 횟수
     * @param nextEventCallBackParams 실패 후 실행될 이벤트의 CallBack 의 매개변수
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean addEvent(String event,
                            HashSet<String> fromStateSet,
                            String toState,
                            CallBack successCallBack,
                            CallBack failCallBack,
                            String nextEvent,
                            int delay,
                            int nextEventRetryCount,
                            Object... nextEventCallBackParams) {
        if (event == null || fromStateSet == null || toState == null) {
            logger.warn("[{}] Fail to add event. (event={}, fromState={}, toState={})",
                    ResultCode.FAIL_ADD_EVENT, event, fromStateSet, toState
            );
            return false;
        }

        if (fromStateSet.contains(toState)) {
            logger.warn("[{}] Fail to add event. (From == To) (event={}, fromState={}, toState={})",
                    ResultCode.FAIL_ADD_EVENT, event, fromStateSet, toState
            );
            return false;
        }

        StateEvent stateEvent = getStateEventByEvent(event);
        if (stateEvent != null) {
            logger.warn("[{}] Duplicated event. (event={}, fromState={}, toState={})",
                    ResultCode.DUPLICATED_EVENT, event, fromStateSet, toState
            );
            return false;
        }

        synchronized (eventMap) {
            stateEvent = new StateEvent(
                    event,
                    fromStateSet,
                    toState,
                    successCallBack,
                    failCallBack,
                    nextEvent,
                    delay,
                    nextEventRetryCount,
                    nextEventCallBackParams
            );

            boolean result = eventMap.putIfAbsent(event, stateEvent) == null;
            if (result) {
                logger.trace("[{}] Success to add state. (event={}, fromState={}, toState={})",
                        ResultCode.SUCCESS_ADD_STATE, stateEvent, fromStateSet, toState
                );
            } else {
                logger.warn("[{}] Fail to add state. (event={}, fromState={}, toState={})",
                        ResultCode.FAIL_ADD_STATE, stateEvent, fromStateSet, toState
                );
            }

            return result;
        }
    }

    /**
     * @fn public void removeAllEvents()
     * @brief 등록된 모든 이벤트들을 삭제하는 함수
     */
    public void removeAllEvents() {
        synchronized (eventMap) {
            eventMap.clear();
        }
    }

    /**
     * @fn public boolean removeEvent(String fromState)
     * @brief From state 를 Map 에서 삭제하는 함수
     * 다른 From state 와 To state 로 포함되어 있으면 다 삭제
     * @param event Event
     * @return 성공 시 true, 실패 시 false 반환
     */
    public boolean removeEvent(String event) {
        synchronized (eventMap) {
            boolean result = eventMap.remove(event) != null;
            if (result) {
                logger.debug("[{}] Success to remove the from state. (event={})",
                        ResultCode.SUCCESS_REMOVE_STATE, event
                );
            } else {
                logger.debug("[{}] Fail to remove the from state. (event={})",
                        ResultCode.FAIL_REMOVE_STATE, event
                );
            }

            return result;
        }
    }

    /**
     * @fn public List<String> getAllEvents ()
     * @brief 정의된 모든 이벤트들을 새로운 리스트에 저장하여 반환하는 함수
     * @return 성공 시 정의된 이벤트 리스트, 실패 시 null 반환
     */
    public List<String> getAllEvents () {
        synchronized (eventMap) {
            return new ArrayList<>(eventMap.keySet());
        }
    }

    public Map<String, StateEvent> cloneEventMap() {
        HashMap<String, StateEvent> cloneMap;
        synchronized (eventMap) {
            try {
                cloneMap = (HashMap<String, StateEvent>) eventMap.clone();
            } catch (Exception e) {
                cloneMap = eventMap;
            }
        }
        return cloneMap;
    }

    /**
     * @fn public StateEvent getStateEventByEvent (String event)
     * @brief 이벤트 이름으로 StateEvent 를 반환하는 함수
     * @param event 이벤트 이름
     * @return 성공 시 StateEvent, 실패 시 null 반환
     */
    public StateEvent getStateEventByEvent(String event) {
        synchronized (eventMap) {
            return eventMap.get(event);
        }
    }

    /**
     * @fn public String nextState(StateHandler stateHandler, String event, StateUnit stateUnit, Object... params)
     * @brief 지정한 이벤트에 일치하는 상태 천이를 진행하는 함수
     * @param stateHandler StateHandler
     * @param event 이벤트 이름
     * @param stateUnit State unit
     * @param isScheduled 스케줄링되어 발생한 이벤트 여부
     * @return 성공 시 천이 후 상태값 반환, 실패 시 null 또는 천이 전 상태값 반환
     */
    public String nextState(StateHandler stateHandler, String event, StateUnit stateUnit, boolean isScheduled) {
        StateEvent stateEvent = getStateEventByEvent(event);
        if (stateEvent == null) {
            logger.warn("[{}] ({}) Fail to find the event. Must define the event. (event={}, stateUnit={})",
                    ResultCode.FAIL_GET_EVENT, stateHandler.getName(), event, stateUnit
            );
            return null;
        }

        if (stateUnit == null) {
            logger.warn("[{}] ({}) StateUnit is null. (event={})",
                    ResultCode.NULL_OBJECT, stateHandler.getName(), event
            );
            return null;
        } else if (!stateUnit.getIsAlive()) {
            logger.warn("[{}] ({}) Fail to transit. StateUnit is not alive. (event={})",
                    ResultCode.FAIL_TRANSIT_STATE, stateHandler.getName(), event
            );
            return null;
        }

        String curFromState = null;
        HashSet<String> fromStateSet = stateEvent.getFromStateSet();
        String toState = stateEvent.getToState();
        String nextEvent = stateEvent.getNextEvent();
        boolean isRetryOngoing = false;
        boolean isRetryTransitionCompleted = false;

        try {
            stateUnit.logicLock.lock();
            logger.debug("[LOCK<] StateUnit=[{}], lockAddr=[{}]", stateUnit, stateUnit.logicLock);

            // From state 가 현재 상태와 같은지 확인
            if (fromStateSet.contains(stateUnit.getCurState())) {
                curFromState = stateUnit.getCurState();
            }

            // From state 가 현재 상태와 같으면 성공
            if (curFromState != null) {
                // 1) 상태 천이
                // 1-1) 현재 이벤트가 스케줄링되어 발생한 이벤트인지 확인
                if (isScheduled) {
                    // 1-1-1) 재시도 제한 횟수가 0 초과이고, 재시도 진행 횟수가 재시도 제한 횟수와 같거나 크면 상태 천이 수행
                    RetryManager retryManager = stateTaskManager.getRetryManager();
                    RetryStatus retryStatus = retryManager.checkRetry(stateUnit.getNextEventKey());
                    if (retryStatus != RetryStatus.ONGOING) {
                        // 마지막 재시도 이벤트인 경우 CallBack 실행하지 않고 상태 천이만 수행한다.
                        stateUnit.setState(curFromState, toState);
                        if (retryStatus == RetryStatus.IDLE) {
                            isRetryTransitionCompleted = true;
                        }
                    }
                    // 1-1-2) 앞의 경우에 해당되지 않으면, 상태 천이 수행하지 않는다.
                    else {
                        isRetryOngoing = true;
                    }
                }
                // 1-2) 직접 fire 함수를 호출하여 발생한 이벤트이면, 상태 천이 수행
                else {
                    stateUnit.setState(curFromState, toState);
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] ({}) Fail to transit. StateEventManager.nextState.Exception (event={}, curState={})",
                    ResultCode.FAIL_TRANSIT_STATE, stateHandler.getName(), event, stateUnit.getCurState(), e
            );
        } finally {
            stateUnit.logicLock.unlock();
            logger.debug("[>UNLOCK] StateUnit=[{}], lockAddr=[{}]", stateUnit, stateUnit.logicLock);

            if (curFromState != null) {
                // 재시도 로직 수행 중에는 스케줄링 관련 로직은 수행되지 않는다.
                if (!isRetryOngoing) {
                    // 2) Prev Event 취소
                    // 만약 현재 상태가 기대되는 상태 천이의 현재 상태이면
                    // 이전에 등록된 nextEvent 를 취소시킨다.
                    if (stateUnit.getNextEventKey() != null) {
                        stateTaskManager.removeStateTaskUnit(
                                stateHandler.getName(),
                                stateUnit.getNextEventKey()
                        );

                        stateUnit.setNextEventKey(null);
                    }

                    // 3) Next Event 추가
                    // 다음 상태에 대해 기대되는 상태 천이(또는 이벤트)가
                    // 일정 시간 이후에 발생하지 않을 경우 지정한 다음 이벤트를 발생시킨다.
                    if (nextEvent != null) {
                        int nextEventInterval = stateEvent.getNextEventInterval();
                        int nextEventRetryCount = stateEvent.getNextEventRetryCount();
                        String stateTaskUnitName = stateUnit.setNextEventKey(toState);

                        stateTaskManager.addStateTaskUnit(
                                stateHandler.getName(),
                                stateTaskUnitName,
                                new StateTaskUnit(
                                        stateTaskUnitName,
                                        stateTaskManager,
                                        stateHandler,
                                        nextEvent,
                                        stateUnit,
                                        nextEventInterval
                                ),
                                nextEventRetryCount
                        );
                    }
                }
            }

            // 4) Success CallBack 실행
            // From state 가 현재 상태와 다르면 실패
            if (curFromState == null) {
                logger.warn("[{}] ({}) Fail to transit. From state is not matched. (event={}, fromState: cur={}, expected={}, stateUnit={})",
                        ResultCode.FAIL_TRANSIT_STATE, stateHandler.getName(), event, stateUnit.getCurState(), fromStateSet, stateUnit
                );

                // Fail CallBack 실행
                CallBack failCallBack = stateEvent.getFailCallBack();
                if (failCallBack != null) {
                    stateUnit.setFailCallBackResult(
                            failCallBack.callBackFunc(stateUnit)
                    );
                }
            } else {
                if (!isRetryTransitionCompleted) {
                    CallBack successCallBack = stateEvent.getSuccessCallBack();
                    if (successCallBack != null) {
                        stateUnit.setSuccessCallBackResult(
                                successCallBack.callBackFunc(stateUnit)
                        );
                    }
                }
            }
        }

        return stateUnit.getCurState();
    }

}
