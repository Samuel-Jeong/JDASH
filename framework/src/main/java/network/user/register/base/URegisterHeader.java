package network.user.register.base;

import network.user.register.exception.URegisterException;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class URegisterHeader {

    public static final int U_REGISTER_HEADER_SIZE = 22;

    private final String magicCookie;               // 2 bytes
    private final URegisterMessageType messageType;     // 4 bytes
    private final int seqNumber;                  // 4 bytes
    private final long timeStamp;                   // 8 bytes
    private int bodyLength;                     // 4 bytes

    public URegisterHeader(byte[] data) throws URegisterException {
        if (data.length >= U_REGISTER_HEADER_SIZE) {
            int index = 0;

            byte[] magicCookieByteData = new byte[2];
            System.arraycopy(data, index, magicCookieByteData, 0, magicCookieByteData.length);
            this.magicCookie = new String(magicCookieByteData, StandardCharsets.UTF_8);
            index += magicCookieByteData.length;

            byte[] messageTypeByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, messageTypeByteData, 0, messageTypeByteData.length);
            this.messageType = URegisterMessageType.values()[ByteUtil.bytesToInt(messageTypeByteData, true)];
            index += messageTypeByteData.length;

            byte[] seqNumberByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, seqNumberByteData, 0, seqNumberByteData.length);
            this.seqNumber = ByteUtil.bytesToInt(seqNumberByteData, true);
            index += seqNumberByteData.length;

            byte[] timeStampByteData = new byte[ByteUtil.NUM_BYTES_IN_LONG];
            System.arraycopy(data, index, timeStampByteData, 0, timeStampByteData.length);
            this.timeStamp = ByteUtil.bytesToShort(timeStampByteData, true);
            index += timeStampByteData.length;

            byte[] bodyLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, bodyLengthByteData, 0, bodyLengthByteData.length);
            this.bodyLength = ByteUtil.bytesToInt(bodyLengthByteData, true);
        } else {
            magicCookie = null;
            messageType = URegisterMessageType.UNKNOWN;
            seqNumber = 0;
            timeStamp = 0;
            bodyLength = 0;

            throw new URegisterException("[URegisterHeader] Fail to create the header. Data length is wrong. (" + data.length + ")");
        }
    }

    public URegisterHeader(String magicCookie, URegisterMessageType messageType, int seqNumber, long timeStamp, int bodyLength) {
        this.magicCookie = magicCookie;
        this.messageType = messageType;
        this.seqNumber = seqNumber;
        this.timeStamp = timeStamp;
        this.bodyLength = bodyLength;
    }

    public byte[] getByteData() {
        byte[] data = new byte[U_REGISTER_HEADER_SIZE];

        int index = 0;
        byte[] magicCookieByteData = magicCookie.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(magicCookieByteData, 0, data, index, magicCookieByteData.length);
        index += magicCookieByteData.length;

        byte[] messageTypeByteData = ByteUtil.intToBytes(URegisterMessageType.valueOf(messageType.name()).ordinal(), true);
        System.arraycopy(messageTypeByteData, 0, data, index, messageTypeByteData.length);
        index += messageTypeByteData.length;

        byte[] seqNumberByteData = ByteUtil.intToBytes(seqNumber, true);
        System.arraycopy(seqNumberByteData, 0, data, index, ByteUtil.NUM_BYTES_IN_INT);
        index += seqNumberByteData.length;

        byte[] timeStampByteData = ByteUtil.longToBytes(timeStamp, true);
        System.arraycopy(timeStampByteData, 0, data, index, ByteUtil.NUM_BYTES_IN_LONG);
        index += timeStampByteData.length;

        byte[] bodyLengthByteData = ByteUtil.intToBytes(bodyLength, true);
        System.arraycopy(bodyLengthByteData, 0, data, index, bodyLengthByteData.length);

        return data;
    }

    public String getMagicCookie() {
        return magicCookie;
    }

    public URegisterMessageType getMessageType() {
        return messageType;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }

    public int getBodyLength() {
        return bodyLength;
    }
}
