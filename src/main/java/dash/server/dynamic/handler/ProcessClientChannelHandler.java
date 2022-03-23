package dash.server.dynamic.handler;

import dash.server.dynamic.message.StreamingStartResponse;
import dash.server.dynamic.message.StreamingStopResponse;
import dash.server.dynamic.message.base.MessageHeader;
import dash.server.dynamic.message.base.MessageType;
import dash.server.dynamic.message.base.ResponseType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceManager;

public class ProcessClientChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ProcessClientChannelHandler.class);

    ////////////////////////////////////////////////////////////////////////////////

    public ProcessClientChannelHandler() {
        logger.debug("ClientHandler is created.");
    }

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
        ByteBuf buf = datagramPacket.content();
        if (buf == null) {
            logger.warn("[ProcessClientChannelHandler] DatagramPacket's content is null.");
            return;
        }

        int readBytes = buf.readableBytes();
        if (buf.readableBytes() <= 0) {
            logger.warn("[ProcessClientChannelHandler] Message is null.");
            return;
        }

        byte[] data = new byte[readBytes];
        buf.getBytes(0, data);
        if (data.length <= MessageHeader.SIZE) { return; }

        byte[] headerData = new byte[MessageHeader.SIZE];
        System.arraycopy(data, 0, headerData, 0, MessageHeader.SIZE);
        MessageHeader messageHeader = new MessageHeader(headerData);

        if (messageHeader.getMessageType() == MessageType.STREAMING_START_RES) {
            StreamingStartResponse streamingStartResponse = new StreamingStartResponse(data);
            int statusCode = streamingStartResponse.getStatusCode();
            String reason = streamingStartResponse.getReason();
            logger.debug("[ProcessClientChannelHandler] RECV StreamingStartResponse(statusCode={}, reason={})", statusCode, reason);

            if (statusCode == ResponseType.NOT_FOUND) {
                logger.debug("[ProcessClientChannelHandler] RECV StreamingStartResponse [404 NOT FOUND], Fail to start service.");
                ServiceManager.getInstance().stop();
            }
        } else if (messageHeader.getMessageType() == MessageType.STREAMING_STOP_RES) {
            StreamingStopResponse streamingStopResponse = new StreamingStopResponse(data);
            int statusCode = streamingStopResponse.getStatusCode();
            String reason = streamingStopResponse.getReason();
            logger.debug("[ProcessClientChannelHandler] RECV StreamingStopResponse(statusCode={}, reason={})", statusCode, reason);
        } else {
            logger.debug("[ProcessClientChannelHandler] RECV UnknownResponse (header={})", messageHeader);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("ProcessClientChannelHandler is inactive.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //logger.warn("ProcessClientChannelHandler.Exception", cause);
    }

}
