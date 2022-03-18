package util.fsm.module;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.StateManager;
import util.fsm.event.retry.RetryManager;
import util.fsm.info.ResultCode;
import util.fsm.module.base.EventCondition;
import util.fsm.module.base.StateTaskUnit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author jamesj
 * @class public class StateTaskManager
 * @brief StateTaskManager class
 * 이벤트 스케줄링 클래스
 */
public class StateTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(StateTaskManager.class);

    private final ScheduledThreadPoolExecutor executor;

    // StateScheduler Map
    private final Map<String, ScheduledThreadPoolExecutor> stateSchedulerMap = new HashMap<>();
    private final ReentrantLock stateSchedulerMapLock = new ReentrantLock();

    // ScheduledThreadPoolExecutor Map
    private final Map<String, ScheduledFuture<?>> stateTaskUnitMap = new HashMap<>();
    private final ReentrantLock stateTaskUnitMapLock = new ReentrantLock();

    // RetryManager
    private final RetryManager retryManager = new RetryManager();

    private final StateManager stateManager;

    ////////////////////////////////////////////////////////////////////////////////

    public StateTaskManager(StateManager stateManager, int threadMaxSize) {
        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("StateTaskManager-%d")
                .daemon(true)
                .priority(Thread.MAX_PRIORITY)
                .build();
        executor = new ScheduledThreadPoolExecutor(threadMaxSize, threadFactory);
        this.stateManager = stateManager;
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public void addStateScheduler(StateHandler stateHandler, EventCondition eventCondition, int delay)
     * @brief StateTaskManager 에 새로운 StateScheduler 를 등록하는 함수
     */
    public void addStateScheduler(StateHandler stateHandler, EventCondition eventCondition, int delay) {
        if (stateHandler == null || eventCondition == null || delay < 0) { return; }

        String handlerName = stateHandler.getName();
        String eventConditionName = eventCondition.getStateEvent().getName();
        String key = handlerName + "_" + eventConditionName;

        try {
            stateSchedulerMapLock.lock();

            if (stateSchedulerMap.get(key) != null) {
                logger.warn("[{}] ({}) StateScheduler Hashmap Key duplication error.",
                        ResultCode.DUPLICATED_KEY, key
                );
            } else {
                StateScheduler stateScheduler = new StateScheduler(stateManager, stateHandler, eventCondition, delay);
                ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                        .namingPattern("StateScheduler-" + key + "-%d")
                        .daemon(true)
                        .priority(Thread.MAX_PRIORITY)
                        .build();
                ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
                scheduledThreadPoolExecutor.scheduleAtFixedRate(
                        stateScheduler,
                        0,
                        stateScheduler.getInterval(),
                        TimeUnit.MILLISECONDS
                );

                if (stateSchedulerMap.put(key, scheduledThreadPoolExecutor) == null) {
                    logger.debug("[{}] ({}) StateScheduler is added.",
                            ResultCode.SUCCESS_ADD_STATE_TASK_UNIT, key
                    );
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] ({}) StateTaskManager.startScheduler.Exception",
                    ResultCode.FAIL_ADD_STATE_TASK_UNIT, key, e
            );
        } finally {
            stateSchedulerMapLock.unlock();
        }
    }

    /**
     * @fn public void removeStateScheduler (String handlerName)
     * @brief 지정한 이름의 StateScheduler 를 삭제하는 함수
     * @param handlerName StateHandler 이름
     */
    public void removeStateScheduler(String handlerName, String eventName) {
        if (handlerName == null || eventName == null) { return; }

        String key = handlerName + "_" + eventName;
        try {
            stateSchedulerMapLock.lock();

            if (!stateSchedulerMap.isEmpty()) {
                ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = stateSchedulerMap.get(key);
                if (scheduledThreadPoolExecutor == null) {
                    logger.warn("[{}] ({}) Fail to find the StateScheduler.",
                            ResultCode.FAIL_GET_STATE_TASK_UNIT, key
                    );
                } else {
                    scheduledThreadPoolExecutor.shutdown();

                    if (stateSchedulerMap.remove(key) != null) {
                        logger.debug("[{}] ({}) StateScheduler is removed.",
                                ResultCode.SUCCESS_REMOVE_STATE_TASK_UNIT, key
                        );
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] ({}) StateTaskManager.stopStateScheduler.Exception",
                    ResultCode.FAIL_REMOVE_STATE_TASK_UNIT, key, e
            );
        } finally {
            stateSchedulerMapLock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public void addStateTaskUnit (String handlerName, String name, StateTaskUnit stateTaskUnit)
     * @brief StateTaskManager 에 새로운 StateTaskUnit 를 등록하는 함수
     */
    public void addStateTaskUnit(String handlerName, String stateTaskUnitName, StateTaskUnit stateTaskUnit, int retryCount) {
        if (handlerName == null || stateTaskUnitName == null || stateTaskUnit == null) { return; }

        try {
            stateTaskUnitMapLock.lock();

            if (stateTaskUnitMap.get(stateTaskUnitName) != null) {
                logger.warn("[{}] ({}) StateTaskUnit Hashmap Key duplication error. (name={})",
                        ResultCode.DUPLICATED_KEY, handlerName, stateTaskUnitName
                );
            } else {
                ScheduledFuture<?> scheduledFuture = executor.scheduleAtFixedRate(
                        stateTaskUnit,
                        stateTaskUnit.getInterval(),
                        stateTaskUnit.getInterval(),
                        TimeUnit.MILLISECONDS
                );

                if (stateTaskUnitMap.put(stateTaskUnitName, scheduledFuture) == null) {
                    logger.debug("[{}] ({}) StateTaskUnit [{}] is added.",
                            ResultCode.SUCCESS_ADD_STATE_TASK_UNIT, handlerName, stateTaskUnitName
                    );
                }

                if (retryCount > 0) {
                    retryManager.addRetryUnit(stateTaskUnitName, retryCount, 0);
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] ({}) StateTaskManager.addTask.Exception",
                    ResultCode.FAIL_ADD_STATE_TASK_UNIT, handlerName, e
            );
        } finally {
            stateTaskUnitMapLock.unlock();
        }
    }

    /**
     * @fn public void removeStateTaskUnit (String handlerName, String name)
     * @brief 지정한 이름의 StateTaskUnit 를 삭제하는 함수
     * @param handlerName StateHandler 이름
     * @param stateTaskUnitName StateTaskUnit 이름
     */
    public void removeStateTaskUnit(String handlerName, String stateTaskUnitName) {
        if (handlerName == null || stateTaskUnitName == null) { return; }

        try {
            stateTaskUnitMapLock.lock();

            if (!stateTaskUnitMap.isEmpty()) {
                ScheduledFuture<?> scheduledFuture = stateTaskUnitMap.get(stateTaskUnitName);
                if (scheduledFuture == null) {
                    logger.warn("[{}] ({}) Fail to find the StateTaskUnit. (name={})",
                            ResultCode.FAIL_GET_STATE_TASK_UNIT, handlerName, stateTaskUnitName
                    );
                } else {
                    scheduledFuture.cancel(true);

                    if (stateTaskUnitMap.remove(stateTaskUnitName) != null) {
                        logger.debug("[{}] ({}) StateTaskUnit [{}] is removed.",
                                ResultCode.SUCCESS_REMOVE_STATE_TASK_UNIT, handlerName, stateTaskUnitName
                        );
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] ({}) ({}) StateTaskManager.removeTask.Exception",
                    ResultCode.FAIL_REMOVE_STATE_TASK_UNIT, handlerName, stateTaskUnitName, e
            );
        } finally {
            stateTaskUnitMapLock.unlock();
        }
    }

    public void stop ( ) {
        for (ScheduledFuture<?> scheduledFuture : stateTaskUnitMap.values()) {
            scheduledFuture.cancel(true);
        }

        executor.shutdown();
        logger.debug("() () () Interval Task Manager ends.");
    }

    ////////////////////////////////////////////////////////////////////////////////

    public RetryManager getRetryManager() {
        return retryManager;
    }

}
