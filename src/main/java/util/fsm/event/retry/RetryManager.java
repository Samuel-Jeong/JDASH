package util.fsm.event.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.event.retry.base.RetryStatus;
import util.fsm.event.retry.base.RetryUnit;
import util.fsm.info.ResultCode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @class public class RetryManager
 * @brief RetryManager class
 * 이벤트 재시도 진행 클래스
 */
public class RetryManager {

    private static final Logger logger = LoggerFactory.getLogger(RetryManager.class);

    // Retry Count Map
    private final Map<String, RetryUnit> retryUnitMap = new HashMap<>();
    // Retry Count Map Lock
    private final ReentrantLock retryUnitMapLock = new ReentrantLock();

    ////////////////////////////////////////////////////////////////////////////////

    public RetryManager() {
        // Nothing
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public RetryStatus checkRetry(String key)
     * @brief 지정한 키의 RetryUnit 데이터를 확인하여 상태를 반환하는 함수
     * 1) 현재 재시도 횟수가 재시도 제한 횟수 미만이면, RetryStatus.ONGOING 상태 반환
     * 2) 현재 재시도 횟수가 재시도 제한 횟수 이상이면, RetryStatus.IDLE 상태 반환
     * 3) 재시도 제한 횟수가 0 이면 재시도 하지 않으므로, null 반환
     * 4) 지정한 Key 에 대한 RetryUnit 이 없는 경우, null 반환
     * @param key RetryUnit Map Key
     * @return 성공 시 RetryUnit, 실패 시 null 반환
     */
    public RetryStatus checkRetry(String key) {
        RetryUnit retryUnit = getRetryUnit(key);
        if (retryUnit == null) {
            logger.trace("[{}] RetryManager.checkRetry: Not found the RetryUnit. (key={})",
                    ResultCode.FAIL_GET_RETRY_UNIT, key
            );
            return RetryStatus.NONE;
        } else if (retryUnit.getRetryCountLimit() <= 0) {
            logger.warn("[{}] RetryManager.checkRetry: Retry limit count is not positive. (key={})",
                    ResultCode.NOT_POSITIVE_INTEGER, key
            );
            return RetryStatus.NONE;
        }

        try {
            int curRetryCount = retryUnit.getCurRetryCount();
            if (curRetryCount < retryUnit.getRetryCountLimit()) {
                retryUnit.setRetryStatus(RetryStatus.ONGOING);
                retryUnit.setCurRetryCount(curRetryCount + 1);
            } else {
                removeRetryUnit(key);
            }

            return retryUnit.getRetryStatus();
        } catch (Exception e) {
            return RetryStatus.NONE;
        }
    }

    public RetryStatus getRetryStatus(String key) {
        RetryUnit retryUnit = getRetryUnit(key);
        if (retryUnit == null) {
            logger.trace("[{}] RetryManager.checkRetry: Not found the RetryUnit. (key={})",
                    ResultCode.FAIL_GET_RETRY_UNIT, key
            );
            return null;
        }

        try {
            return retryUnit.getRetryStatus();
        } catch (Exception e) {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public RetryUnit addRetryUnit(String key, int retryCountLimit, int initialRetryCount)
     * @brief RetryUnit 을 새로 추가하는 함수
     * @param key RetryUnit Map Key
     * @param retryCountLimit 재시도 제한 횟수
     * @param initialRetryCount 초기 재시도 진행 횟수
     * @return 성공 시 RetryUnit, 실패 시 null 반환
     */
    public RetryUnit addRetryUnit(String key, int retryCountLimit, int initialRetryCount) {
        try {
            retryUnitMapLock.lock();

            if (retryUnitMap.putIfAbsent(key, new RetryUnit(key, retryCountLimit, initialRetryCount)) == null) {
                logger.debug("[{}] RetryManager.checkRetry: Success to add a RetryUnit. ({})",
                        ResultCode.SUCCESS_ADD_RETRY_UNIT, retryUnitMap.get(key)
                );
            }

            return retryUnitMap.get(key);
        } catch (Exception e) {
            return null;
        } finally {
            retryUnitMapLock.unlock();
        }
    }

    /**
     * @fn public RetryUnit getRetryUnit(String key)
     * @brief RetryUnit 을 반환하는 함수
     * @param key RetryUnit Map Key
     * @return 성공 시 RetryUnit, 실패 시 null 반환
     */
    public RetryUnit getRetryUnit(String key) {
        try {
            retryUnitMapLock.lock();

            return retryUnitMap.get(key);
        } catch (Exception e) {
            return null;
        } finally {
            retryUnitMapLock.unlock();
        }
    }

    /**
     * @fn public void removeRetryUnit(String key)
     * @brief RetryUnit 을 삭제하는 함수
     * @param key RetryUnit Map Key
     */
    public void removeRetryUnit(String key) {
        try {
            retryUnitMapLock.lock();

            RetryUnit retryUnit = retryUnitMap.remove(key);
            if (retryUnit != null) {
                logger.debug("[{}] RetryManager.checkRetry: Success to remove the RetryUnit. ({})",
                        ResultCode.SUCCESS_REMOVE_RETRY_UNIT, retryUnit
                );
                retryUnit.setRetryStatus(RetryStatus.IDLE);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            retryUnitMapLock.unlock();
        }
    }

}
