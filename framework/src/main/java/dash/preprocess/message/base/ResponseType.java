package dash.preprocess.message.base;

public class ResponseType {

    public static final String REASON_SUCCESS = "SUCCESS";
    public static final String REASON_NOT_FOUND = "NOT FOUND";
    public static final String REASON_ALREADY_EXIST = "ALREADY EXIST";

    public static final int UNKNOWN = 0;
    public static final int SUCCESS = 200;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int NOT_AUTHORIZED = 401;

}
