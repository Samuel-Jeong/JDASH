package network.user.register;

import network.user.register.base.URtspHeader;
import network.user.register.base.URtspMessage;
import network.user.register.base.URtspMessageType;
import network.user.register.exception.URtspException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class UserUnRegisterRes extends URtspMessage {

    private static final Logger logger = LoggerFactory.getLogger(UserUnRegisterRes.class);

    public static final int SUCCESS = 200;
    public static final int NOT_ACCEPTED = 400;
    public static final int STATE_ERROR = 402;

    private final URtspHeader uRtspHeader;

    private final int statusCode;
    private int reasonLength;
    private String reason = "";

    public UserUnRegisterRes(byte[] data) throws URtspException {
        if (data.length >= URtspHeader.U_RTSP_HEADER_SIZE + ByteUtil.NUM_BYTES_IN_INT * 2) {
            int index = 0;

            byte[] headerByteData = new byte[URtspHeader.U_RTSP_HEADER_SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.uRtspHeader = new URtspHeader(headerByteData);
            index += headerByteData.length;

            byte[] statusCodeByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, statusCodeByteData, 0, statusCodeByteData.length);
            statusCode = ByteUtil.bytesToInt(statusCodeByteData, true);
            index += statusCodeByteData.length;

            byte[] reasonLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, reasonLengthByteData, 0, reasonLengthByteData.length);
            reasonLength = ByteUtil.bytesToInt(reasonLengthByteData, true);
            if (reasonLength > 0) {
                index += reasonLengthByteData.length;

                byte[] reasonByteData = new byte[reasonLength];
                System.arraycopy(data, index, reasonByteData, 0, reasonByteData.length);
                reason = new String(reasonByteData, StandardCharsets.UTF_8);
            }
        } else {
            this.uRtspHeader = null;
            this.statusCode = 0;
        }
    }

    public UserUnRegisterRes(String magicCookie, URtspMessageType messageType, int seqNumber, long timeStamp, int statusCode) {
        int bodyLength = ByteUtil.NUM_BYTES_IN_INT * 2;

        this.uRtspHeader = new URtspHeader(magicCookie, messageType, seqNumber, timeStamp, bodyLength);
        this.statusCode = statusCode;
    }

    @Override
    public byte[] getByteData(){
        byte[] data = new byte[URtspHeader.U_RTSP_HEADER_SIZE + this.uRtspHeader.getBodyLength()];
        int index = 0;

        byte[] headerByteData = this.uRtspHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] statusCodeByteData = ByteUtil.intToBytes(statusCode, true);
        System.arraycopy(statusCodeByteData, 0, data, index, statusCodeByteData.length);
        index += statusCodeByteData.length;

        byte[] reasonLengthByteData = ByteUtil.intToBytes(reasonLength, true);
        System.arraycopy(reasonLengthByteData, 0, data, index, reasonLengthByteData.length);

        if (reasonLength > 0 && reason.length() > 0) {
            byte[] reasonByteData = reason.getBytes(StandardCharsets.UTF_8);
            byte[] newData = new byte[data.length];
            System.arraycopy(data, 0, newData, 0, data.length);
            index += reasonLengthByteData.length;
            System.arraycopy(reasonByteData, 0, newData, index, reasonByteData.length);
            data = newData;
        }

        return data;
    }

    public URtspHeader getURtspHeader() {
        return uRtspHeader;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reasonLength = reason.getBytes(StandardCharsets.UTF_8).length;
        this.reason = reason;

        uRtspHeader.setBodyLength(uRtspHeader.getBodyLength() + reasonLength);
    }

}
