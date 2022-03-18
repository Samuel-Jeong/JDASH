package util.fsm.unit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.info.ResultCode;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @class public class StateUnit
 * @brief StateUnit class
 */
public class StateUnit {

    private static final Logger logger = LoggerFactory.getLogger(StateUnit.class);

    // StateUnit 식별 이름
    private final String name;
    // StateHandler 이름
    private final String handlerName;
    // StateUnit Logic lock
    public final ReentrantLock logicLock = new ReentrantLock();

    private final AtomicBoolean isAlive = new AtomicBoolean(false);

    // 바로 이전 상태
    private String prevState = null;
    // 현재 상태
    private String curState;
    // 천이 실패 시 실행될 이벤트 키
    private String nextEventKey = null;
    // Success CallBack 결과값
    private Object successCallBackResult = null;
    // Fail CallBack 결과값
    private Object failCallBackResult = null;
    // Spare Data
    private Object data;

    ////////////////////////////////////////////////////////////////////////////////

    public StateUnit(String name, String handlerName, String curState, Object data) {
        this.name = name;
        this.handlerName = handlerName;
        this.curState = curState;
        this.data = data;

        logger.debug("[NEW] StateUnit: name=[{}], lockAddr=[{}]", name, logicLock);
    }

    /**
     * @fn public String getName()
     * @brief StateUnit 이름을 반환하는 함수
     * @return StateUnit 이름
     */
    public String getName() {
        return name;
    }

    /**
     * @fn public String getHandlerName()
     * @brief StateHandler 이름을 반환하는 함수
     * @return StateHandler 이름
     */
    public String getHandlerName() {
        return handlerName;
    }

    /**
     * @fn public String getNextEventKey()
     * @brief nextEventKey 를 반환하는 함수
     * @return 기존에 설정된 nextEventKey
     */
    public String getNextEventKey() {
        return nextEventKey;
    }

    /**
     * @fn public String setFailEventKey(String curState)
     * @brief nextEventKey 를 설정하고 반환하는 함수
     * @param curState 현재 상태
     * @return 새로 설정된 nextEventKey
     */
    public String setNextEventKey(String curState) {
        this.nextEventKey = makeNextEventKey(curState);
        return this.nextEventKey;
    }

    /**
     * @fn private String makeNextEventKey(String curState)
     * @brief nextEventKey 로 사용될 문자열을 생성하고 반환하는 함수
     * @param curState 현재 상태
     * @return 새로 생성된 nextEventKey
     */
    private String makeNextEventKey(String curState) {
        if (curState == null) { return null; }
        return name + ":" + curState + ":" + UUID.randomUUID();
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public String getData()
     * @brief StateUnit Spare data 를 반환하는 함수
     * @return data
     */
    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
        logger.debug("({}) StateUnit: data=[{}]", name, data);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void setState(String fromState, String toState) {
        setPrevState(fromState);
        setCurState(toState);
    }

    /**
     * @fn public String getCurState ()
     * @brief 현재 State 이름을 반환하는 함수
     * @return 현재 State 이름
     */
    public String getCurState() {
        return curState;
    }

    /**
     * @fn private void setCurState (String state)
     * @brief 현재 State 를 설정하는 함수
     * @param curState 현재 State 이름
     */
    private void setCurState(String curState) {
        logger.debug("[{}] ({}) Cur State is changed. ([{}] > [{}])",
                ResultCode.SUCCESS_TRANSIT_STATE, name, getCurState(), curState
        );
        this.curState = curState;
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public String getPrevState ()
     * @brief 이전 State 이름을 반환하는 함수
     * @return 이전 State 이름
     */
    public String getPrevState() {
        return prevState;
    }

    /**
     * @fn private void setPrevState (String state)
     * @brief 이전 State 를 설정하는 함수
     * @param prevState 이전 State 이름
     */
    private void setPrevState(String prevState) {
        logger.debug("[{}] ({}) Prev State is changed. ([{}] > [{}])",
                ResultCode.SUCCESS_TRANSIT_STATE, name, getPrevState(), prevState
        );
        this.prevState = prevState;
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public Object setSuccessCallBackResult()
     * @brief Success CallBack 실행 결과를 반환하는 함수
     * @return Success CallBack 결과값
     */
    public Object getSuccessCallBackResult() {
        return successCallBackResult;
    }

    /**
     * @fn public void setSuccessCallBackResult(Object result)
     * @brief Success CallBack 실행 결과를 저장하는 함수
     * @param result 저장할 Success CallBack 결과값
     */
    public void setSuccessCallBackResult(Object result) {
        this.successCallBackResult = result;
    }

    public Object getFailCallBackResult() {
        return failCallBackResult;
    }

    public void setFailCallBackResult(Object failCallBackResult) {
        this.failCallBackResult = failCallBackResult;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void setIsAlive (boolean isAlive) {
        this.isAlive.set(isAlive);
    }

    public boolean getIsAlive () {
        return isAlive.get();
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "StateUnit{" +
                "name='" + name + '\'' +
                ", handlerName='" + handlerName + '\'' +
                ", prevState='" + prevState + '\'' +
                ", curState='" + curState + '\'' +
                ", nextEventKey='" + nextEventKey + '\'' +
                '}';
    }
}
