package register.exception;

public class URtmpException extends Exception {

    private final String message;

    public URtmpException(String message) {
        super(message);

        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
