package register.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import register.base.URtmpHeader;
import register.base.URtmpMessage;
import register.base.URtmpMessageType;
import register.exception.URtmpException;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class RtmpUnRegisterReq extends URtmpMessage {

    private static final Logger log = LoggerFactory.getLogger(RtmpUnRegisterReq.class);

    private final URtmpHeader uRtspHeader;

    private final int idLength;         // 4 bytes
    private final String id;            // idLength bytes
    private final short listenPort;     // 2 bytes

    public RtmpUnRegisterReq(byte[] data) throws URtmpException {
        if (data.length >= URtmpHeader.U_RTSP_HEADER_SIZE + ByteUtil.NUM_BYTES_IN_INT + ByteUtil.NUM_BYTES_IN_SHORT) {
            int index = 0;

            byte[] headerByteData = new byte[URtmpHeader.U_RTSP_HEADER_SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.uRtspHeader = new URtmpHeader(headerByteData);
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

    public RtmpUnRegisterReq(String magicCookie, URtmpMessageType messageType, int seqNumber, long timeStamp, String id, short listenPort) {
        int bodyLength = id.length() + ByteUtil.NUM_BYTES_IN_INT + ByteUtil.NUM_BYTES_IN_SHORT;

        this.uRtspHeader = new URtmpHeader(magicCookie, messageType, seqNumber, timeStamp, bodyLength);
        this.idLength = id.getBytes(StandardCharsets.UTF_8).length;
        this.id = id;
        this.listenPort = listenPort;
    }

    @Override
    public byte[] getByteData() {
        byte[] data = new byte[URtmpHeader.U_RTSP_HEADER_SIZE + this.uRtspHeader.getBodyLength()];
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

    public URtmpHeader getURtspHeader() {
        return uRtspHeader;
    }

    public String getId() {
        return id;
    }

    public short getListenPort() {
        return listenPort;
    }

}
