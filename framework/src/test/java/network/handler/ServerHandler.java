package network.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    ////////////////////////////////////////////////////////////////////////////////

    public ServerHandler() {
        logger.debug("ServerHandler is created.");
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void channelRead0 (ChannelHandlerContext ctx, DatagramPacket msg) {
        ByteBuf buf = msg.content();
        if (buf == null) {
            return;
        }

        try {
            if (buf.readableBytes() > 0) {
                logger.debug("IN");
            }
        } catch (Exception e) {
            logger.warn("Fail to handle UDP Packet.", e);
        }
    }

    @Override
    public void exceptionCaught (ChannelHandlerContext ctx, Throwable cause) {
        //logger.warn("ServerHandler.exceptionCaught", cause);
        //ctx.close();
    }

}
