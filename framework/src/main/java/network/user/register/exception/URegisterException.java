package network.user.register.exception;

public class URegisterException extends Exception {

    private final String message;

    public URegisterException(String message) {
        super(message);

        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
