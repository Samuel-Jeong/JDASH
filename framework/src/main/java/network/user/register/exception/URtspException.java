package network.user.register.exception;

public class URtspException extends Exception {

    private final String message;

    public URtspException(String message) {
        super(message);

        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
