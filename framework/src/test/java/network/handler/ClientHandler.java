package network.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    ////////////////////////////////////////////////////////////////////////////////

    public ClientHandler() {
        logger.debug("ClientHandler is created.");
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void channelRead0 (ChannelHandlerContext ctx, DatagramPacket msg) {
        // ignore
    }

    @Override
    public void exceptionCaught (ChannelHandlerContext ctx, Throwable cause) {
        // ignore
    }

}
