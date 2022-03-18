package util.fsm.info;

/**
 * @class public class ResultCode
 * @brief ResultCode class
 */
public class ResultCode {

    // 상태 추가 성공
    public static final int SUCCESS_ADD_STATE = 1000;
    // 상태 삭제 성공
    public static final int SUCCESS_REMOVE_STATE = 1001;
    // 상태 천이 성공
    public static final int SUCCESS_TRANSIT_STATE = 1002;
    // 실패 이벤트 예약 성공
    public static final int SUCCESS_RESERVE_FAIL_STATE = 1010;
    // 실패 이벤트 삭제 성공
    public static final int SUCCESS_REMOVE_FAIL_STATE = 1011;
    // StateTaskUnit 추가 성공
    public static final int SUCCESS_ADD_STATE_TASK_UNIT = 1020;
    // StateTaskUnit 삭제 성공
    public static final int SUCCESS_REMOVE_STATE_TASK_UNIT = 1021;
    // RetryUnit 추가 성공
    public static final int SUCCESS_ADD_RETRY_UNIT = 1030;
    // RetryUnit 삭제 성공
    public static final int SUCCESS_REMOVE_RETRY_UNIT = 1031;

    ////////////////////////////////////////////////////////////////////////////////

    // 상태 추가 실패
    public static final int FAIL_ADD_STATE = 2000;
    // 상태 삭제 실패
    public static final int FAIL_REMOVE_STATE = 2001;
    // 상태 천이 실패
    public static final int FAIL_TRANSIT_STATE = 2002;
    // 상태 조회 실패
    public static final int FAIL_GET_STATE = 2003;
    // 콜백 조회 실패
    public static final int FAIL_GET_CALLBACK = 2004;
    // 상태 처리자 조회 실패
    public static final int FAIL_GET_STATE_HANDLER = 2005;
    // 이벤트 조회 실패
    public static final int FAIL_GET_EVENT = 2006;
    // 이벤트 추가 실패
    public static final int FAIL_ADD_EVENT = 2007;
    // 실패 이벤트 예약 실패
    public static final int FAIL_RESERVE_FAIL_STATE = 2020;
    // StateTaskUnit 조회 실패
    public static final int FAIL_GET_STATE_TASK_UNIT = 2030;
    // StateTaskUnit 추가 실패
    public static final int FAIL_ADD_STATE_TASK_UNIT = 2031;
    // StateTaskUnit 삭제 실패
    public static final int FAIL_REMOVE_STATE_TASK_UNIT = 2032;
    // RetryUnit 조회 실패
    public static final int FAIL_GET_RETRY_UNIT = 2040;

    ////////////////////////////////////////////////////////////////////////////////

    // 상태 중복
    public static final int DUPLICATED_STATE = 3000;
    // 이벤트 중복
    public static final int DUPLICATED_EVENT = 3001;
    // Map key 중복
    public static final int DUPLICATED_KEY = 3002;
    // 알 수 없는 상태
    public static final int UNKNOWN_STATE = 3010;
    // 같은 상태
    public static final int SAME_STATE = 3011;
    // NULL 객체
    public static final int NULL_OBJECT = 3100;
    // Thread Exception
    public static final int THREAD_EXCEPTION = 3200;
    // Not positive integer
    public static final int NOT_POSITIVE_INTEGER = 3300;
}
