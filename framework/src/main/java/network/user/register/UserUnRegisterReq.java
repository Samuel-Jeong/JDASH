package network.user.register;

import network.user.register.base.URtspHeader;
import network.user.register.base.URtspMessage;
import network.user.register.base.URtspMessageType;
import network.user.register.exception.URtspException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class UserUnRegisterReq extends URtspMessage {

    private static final Logger log = LoggerFactory.getLogger(UserUnRegisterReq.class);

    private final URtspHeader uRtspHeader;

    private final int idLength;         // 4 bytes
    private final String id;            // idLength bytes
    private final short listenPort;     // 2 bytes

    public UserUnRegisterReq(byte[] data) throws URtspException {
        if (data.length >= URtspHeader.U_RTSP_HEADER_SIZE + ByteUtil.NUM_BYTES_IN_INT + ByteUtil.NUM_BYTES_IN_SHORT) {
            int index = 0;

            byte[] headerByteData = new byte[URtspHeader.U_RTSP_HEADER_SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.uRtspHeader = new URtspHeader(headerByteData);
            index += headerByteData.length;

            byte[] idLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, idLengthByteData, 0, idLengthByteData.length);
            idLength = ByteUtil.bytesToInt(idLengthByteData, true);
            index += idLengthByteData.length;

            byte[] idByteData = new byte[idLength];
            System.arraycopy(data, index, idByteData, 0, idByteData.length);
            id = new String(idByteData);
            index += idByteData.length;

            byte[] listenPortByteData = new byte[ByteUtil.NUM_BYTES_IN_SHORT];
            System.arraycopy(data, index, listenPortByteData, 0, listenPortByteData.length);
            listenPort = ByteUtil.bytesToShort(listenPortByteData, true);
        } else {
            this.uRtspHeader = null;
            this.idLength = 0;
            this.id = null;
            this.listenPort = 0;
        }
    }

    public UserUnRegisterReq(String magicCookie, URtspMessageType messageType, int seqNumber, long timeStamp, String id, short listenPort) {
        int bodyLength = id.length() + ByteUtil.NUM_BYTES_IN_INT + ByteUtil.NUM_BYTES_IN_SHORT;

        this.uRtspHeader = new URtspHeader(magicCookie, messageType, seqNumber, timeStamp, bodyLength);
        this.idLength = id.getBytes(StandardCharsets.UTF_8).length;
        this.id = id;
        this.listenPort = listenPort;
    }

    @Override
    public byte[] getByteData() {
        byte[] data = new byte[URtspHeader.U_RTSP_HEADER_SIZE + this.uRtspHeader.getBodyLength()];
        int index = 0;

        byte[] headerByteData = this.uRtspHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] idLengthByteData = ByteUtil.intToBytes(idLength, true);
        System.arraycopy(idLengthByteData, 0, data, index, idLengthByteData.length);
        index += idLengthByteData.length;

        byte[] idByteData = id.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(idByteData, 0, data, index, idByteData.length);
        index += idByteData.length;

        byte[] listenPortByteData = ByteUtil.shortToBytes(listenPort, true);
        System.arraycopy(listenPortByteData, 0, data, index, listenPortByteData.length);

        return data;
    }

    public URtspHeader getURtspHeader() {
        return uRtspHeader;
    }

    public String getId() {
        return id;
    }

    public short getListenPort() {
        return listenPort;
    }

}
