package dash.preprocess.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.preprocess.message.base.MessageFactory;
import dash.preprocess.message.base.MessageHeader;
import dash.preprocess.message.base.ResponseType;
import dash.preprocess.message.exception.MessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class EndLiveMediaProcessResponse extends MessageFactory {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(EndLiveMediaProcessResponse.class);

    public static final int MIN_SIZE = MessageHeader.SIZE + ByteUtil.NUM_BYTES_IN_INT * 2;

    private final MessageHeader messageHeader; // 22 bytes

    private final int statusCode; // 4 bytes
    private final int reasonLength; // 4 bytes
    private String reason = ""; // [reasonLength] bytes
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public EndLiveMediaProcessResponse(byte[] data) throws MessageException {
        if (data.length >= MIN_SIZE) {
            int index = 0;

            byte[] headerByteData = new byte[MessageHeader.SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.messageHeader = new MessageHeader(headerByteData);
            index += headerByteData.length;

            byte[] statusCodeByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, statusCodeByteData, 0, statusCodeByteData.length);
            statusCode = ByteUtil.bytesToInt(statusCodeByteData, true);
            index += statusCodeByteData.length;

            byte[] reasonLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, reasonLengthByteData, 0, reasonLengthByteData.length);
            reasonLength = ByteUtil.bytesToInt(reasonLengthByteData, true);
            index += reasonLengthByteData.length;
            if (reasonLength > 0) {
                byte[] reasonByteData = new byte[reasonLength];
                System.arraycopy(data, index, reasonByteData, 0, reasonLength);
                reason = new String(reasonByteData, StandardCharsets.UTF_8);
                //index += reasonLength;
            } else {
                reason = null;
            }
        } else {
            messageHeader = null;
            statusCode = ResponseType.UNKNOWN;
            reasonLength = 0;
            reason = null;
        }
    }

    public EndLiveMediaProcessResponse(MessageHeader messageHeader, int statusCode, int reasonLength, String reason) {
        this.messageHeader = messageHeader;
        this.statusCode = statusCode;
        this.reasonLength = reasonLength;
        this.reason = reason;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public byte[] getByteData() {
        byte[] data = new byte[MIN_SIZE + reasonLength];
        int index = 0;

        byte[] headerByteData = this.messageHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] statusCodeByteData = ByteUtil.intToBytes(statusCode, true);
        System.arraycopy(statusCodeByteData, 0, data, index, statusCodeByteData.length);
        index += statusCodeByteData.length;

        byte[] reasonLengthByteData = ByteUtil.intToBytes(reasonLength, true);
        System.arraycopy(reasonLengthByteData, 0, data, index, reasonLengthByteData.length);
        index += reasonLengthByteData.length;

        if (reasonLength > 0 && reason != null) {
            byte[] reasonByteData = reason.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(reasonByteData, 0, data, index, reasonByteData.length);
            //index += reasonByteData.length;
        }

        return data;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MessageHeader getMessageHeader() {
        return messageHeader;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getReasonLength() {
        return reasonLength;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
