package dash.dynamic.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.dynamic.message.base.MessageFactory;
import dash.dynamic.message.base.MessageHeader;
import dash.dynamic.message.exception.MessageException;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class EndLiveMediaProcessRequest extends MessageFactory {

    ////////////////////////////////////////////////////////////
    public static final int MIN_SIZE = MessageHeader.SIZE + ByteUtil.NUM_BYTES_IN_INT * 2;

    private final MessageHeader messageHeader; // 22 bytes

    private final int sourceIpLength; // 4 bytes
    private final String sourceIp; // 15 bytes
    private final int uriLength; // 4 bytes
    private final String uri; // [uriLength] bytes
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public EndLiveMediaProcessRequest(byte[] data) throws MessageException {
        if (data.length >= MIN_SIZE) {
            int index = 0;

            byte[] headerByteData = new byte[MessageHeader.SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.messageHeader = new MessageHeader(headerByteData);
            index += headerByteData.length;

            byte[] sourceIpLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, sourceIpLengthByteData, 0, sourceIpLengthByteData.length);
            sourceIpLength = ByteUtil.bytesToInt(sourceIpLengthByteData, true);
            index += sourceIpLengthByteData.length;

            if (sourceIpLength > 0) {
                byte[] sourceIpByteData = new byte[sourceIpLength];
                System.arraycopy(data, index, sourceIpByteData, 0, sourceIpLength);
                sourceIp = new String(sourceIpByteData);
                index += sourceIpLength;
            } else {
                sourceIp = null;
            }

            byte[] uriLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, uriLengthByteData, 0, uriLengthByteData.length);
            uriLength = ByteUtil.bytesToInt(uriLengthByteData, true);
            index += uriLengthByteData.length;

            if (uriLength > 0) {
                byte[] uriByteData = new byte[uriLength];
                System.arraycopy(data, index, uriByteData, 0, uriLength);
                uri = new String(uriByteData, StandardCharsets.UTF_8);
            } else {
                uri = null;
            }
        } else {
            messageHeader = null;
            sourceIpLength = 0;
            sourceIp = null;
            uriLength = 0;
            uri = null;
        }
    }

    public EndLiveMediaProcessRequest(MessageHeader messageHeader, int sourceIpLength, String sourceIp, int uriLength, String uri) {
        this.messageHeader = messageHeader;
        this.sourceIpLength = sourceIpLength;
        this.sourceIp = sourceIp;
        this.uriLength = uriLength;
        this.uri = uri;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public byte[] getByteData() {
        byte[] data = new byte[MIN_SIZE + sourceIpLength + uriLength];
        int index = 0;

        byte[] headerByteData = this.messageHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] sourceIpLengthByteData = ByteUtil.intToBytes(sourceIpLength, true);
        System.arraycopy(sourceIpLengthByteData, 0, data, index, sourceIpLengthByteData.length);
        index += sourceIpLengthByteData.length;

        if (sourceIpLength > 0 && sourceIp != null) {
            byte[] sourceIpByteData = sourceIp.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(sourceIpByteData, 0, data, index, sourceIpByteData.length);
            index += sourceIpByteData.length;
        }

        byte[] uriLengthByteData = ByteUtil.intToBytes(uriLength, true);
        System.arraycopy(uriLengthByteData, 0, data, index, uriLengthByteData.length);
        index += uriLengthByteData.length;

        if (uriLength > 0 && uri != null) {
            byte[] uriByteData = uri.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(uriByteData, 0, data, index, uriByteData.length);
        }

        return data;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MessageHeader getMessageHeader() {
        return messageHeader;
    }

    public int getSourceIpLength() {
        return sourceIpLength;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public int getUriLength() {
        return uriLength;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
