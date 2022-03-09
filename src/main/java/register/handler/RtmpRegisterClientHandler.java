package register.handler;

import config.ConfigManager;
import dash.DashManager;
import dash.unit.DashUnit;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import register.base.URtmpHeader;
import register.base.URtmpMessageType;
import register.channel.RtmpRegisterNettyChannel;
import register.message.RtmpRegisterRes;
import register.message.RtmpUnRegisterRes;
import service.AppInstance;
import service.ServiceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RtmpRegisterClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(RtmpRegisterClientHandler.class);

    private final String ip;
    private final int port;

    ////////////////////////////////////////////////////////////////////////////////

    public RtmpRegisterClientHandler(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
        try {
            DashManager dashManager = ServiceManager.getInstance().getDashManager();
            RtmpRegisterNettyChannel rtspRegisterNettyChannel = dashManager.getRegisterChannel();
            if (rtspRegisterNettyChannel == null) {
                logger.warn("[RtmpRegisterClientHandler] RtmpRegister Channel is not defined.");
                return;
            }

            DashUnit dashUnit = dashManager.getMyDashUnit();
            if (dashUnit == null) {
                logger.warn("[RtmpRegisterClientHandler] Fail to process the message. DashUnit is null.");
                return;
            }

            ByteBuf buf = datagramPacket.content();
            if (buf == null) {
                logger.warn("[RtmpRegisterClientHandler] DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[RtmpRegisterClientHandler] Message is null.");
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            URtmpHeader uRtmpHeader = new URtmpHeader(data);
            if (uRtmpHeader.getMessageType() == URtmpMessageType.REGISTER) {
                RtmpRegisterRes rtmpRegisterRes = new RtmpRegisterRes(data);
                logger.debug("[RtmpRegisterClientHandler] [>] {} ({})", rtmpRegisterRes, readBytes);

                int status = rtmpRegisterRes.getStatusCode();
                if (status == RtmpRegisterRes.SUCCESS) { // OK
                    logger.warn("[RtmpRegisterClientHandler] ({}) Success to register the dashUnit. (code={})", dashUnit.getId(), status);
                } else if (status == RtmpRegisterRes.NOT_AUTHORIZED) { // NOT AUTHORIZED
                    // KEY 를 사용하여 MD5 해싱한 값을 다시 REGISTER 에 담아서 전송
                    ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                    messageDigest.update(rtmpRegisterRes.getRealm().getBytes(StandardCharsets.UTF_8));
                    messageDigest.update(configManager.getRegisterHashKey().getBytes(StandardCharsets.UTF_8));
                    byte[] a1 = messageDigest.digest();
                    messageDigest.reset();
                    messageDigest.update(a1);

                    String nonce = new String(messageDigest.digest());
                    rtspRegisterNettyChannel.sendRegister(
                            dashUnit.getId(),
                            configManager.getRegisterTargetIp(),
                            configManager.getRegisterTargetPort(),
                            nonce
                    );
                    dashUnit.setRegistered(false);
                } else {
                    logger.warn("[RtmpRegisterClientHandler] ({}) Fail to register the dashUnit. (code={})", dashUnit.getId(), status);
                    System.exit(1);
                }
            } else if (uRtmpHeader.getMessageType() == URtmpMessageType.UNREGISTER) {
                RtmpUnRegisterRes rtmpUnRegisterRes = new RtmpUnRegisterRes(data);
                logger.debug("[RtmpRegisterClientHandler] [>] {} ({})", rtmpUnRegisterRes, readBytes);

                int status = rtmpUnRegisterRes.getStatusCode();
                if (status == RtmpUnRegisterRes.SUCCESS) { // OK
                    logger.warn("[RtmpRegisterClientHandler] ({}) Success to unregister the dashUnit. (code={})", dashUnit.getId(), status);
                } else {
                    logger.warn("[RtmpRegisterClientHandler] ({}) Fail to unregister the dashUnit. (code={})", dashUnit.getId(), status);
                }
            }
        } catch (Exception e) {
            logger.warn("[RtmpRegisterClientHandler] RtmpRegisterChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("[RtmpRegisterClientHandler] RtmpRegisterChannelHandler is inactive.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("[RtmpRegisterClientHandler] RtmpRegisterChannelHandler.Exception", cause);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

}
