package dash.preprocess.message.exception;

public class MessageException extends Exception {

    private final String message;

    public MessageException(String message) {
        super(message);

        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
