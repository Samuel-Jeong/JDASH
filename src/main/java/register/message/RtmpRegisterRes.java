package register.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import register.base.URtmpHeader;
import register.base.URtmpMessage;
import register.base.URtmpMessageType;
import register.exception.URtmpException;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class RtmpRegisterRes extends URtmpMessage {

    private static final Logger logger = LoggerFactory.getLogger(RtmpRegisterRes.class);

    public static final int SUCCESS = 200;
    public static final int NOT_AUTHORIZED = 401;
    public static final int STATE_ERROR = 402;

    private final URtmpHeader uRtspHeader;

    private final int realmLength;
    private final String realm;
    private final int statusCode;
    private int reasonLength;
    private String reason = "";

    public RtmpRegisterRes(byte[] data) throws URtmpException {
        if (data.length >= URtmpHeader.U_RTSP_HEADER_SIZE + ByteUtil.NUM_BYTES_IN_INT * 3) {
            int index = 0;

            byte[] headerByteData = new byte[URtmpHeader.U_RTSP_HEADER_SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.uRtspHeader = new URtmpHeader(headerByteData);
            index += headerByteData.length;

            byte[] realmLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, realmLengthByteData, 0, realmLengthByteData.length);
            realmLength = ByteUtil.bytesToInt(realmLengthByteData, true);
            index += realmLengthByteData.length;

            byte[] realmByteData = new byte[realmLength];
            System.arraycopy(data, index, realmByteData, 0, realmByteData.length);
            realm = new String(realmByteData, StandardCharsets.UTF_8);
            index += realmByteData.length;

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
            this.realmLength = 0;
            this.realm = null;
            this.statusCode = 0;
        }
    }

    public RtmpRegisterRes(String magicCookie, URtmpMessageType messageType, int seqNumber, long timeStamp, String realm, int statusCode) {
        int bodyLength = realm.length() + ByteUtil.NUM_BYTES_IN_INT * 3;

        this.uRtspHeader = new URtmpHeader(magicCookie, messageType, seqNumber, timeStamp, bodyLength);
        this.realmLength = realm.getBytes(StandardCharsets.UTF_8).length;
        this.realm = realm;
        this.statusCode = statusCode;
    }

    @Override
    public byte[] getByteData(){
        byte[] data = new byte[URtmpHeader.U_RTSP_HEADER_SIZE + this.uRtspHeader.getBodyLength()];
        int index = 0;

        byte[] headerByteData = this.uRtspHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] realmLengthByteData = ByteUtil.intToBytes(realmLength, true);
        System.arraycopy(realmLengthByteData, 0, data, index, realmLengthByteData.length);
        index += realmLengthByteData.length;

        byte[] realmByteData = realm.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(realmByteData, 0, data, index, realmByteData.length);
        index += realmByteData.length;

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

    public URtmpHeader getURtspHeader() {
        return uRtspHeader;
    }

    public String getRealm() {
        return realm;
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
