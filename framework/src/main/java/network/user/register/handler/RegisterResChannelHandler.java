package network.user.register.handler;

import cam.module.GuiManager;
import config.ConfigManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import network.user.UserInfo;
import network.user.UserManager;
import network.user.register.channel.RegisterClientNettyChannel;
import network.user.register.UserRegisterRes;
import network.user.register.UserUnRegisterRes;
import network.user.register.base.URtspHeader;
import network.user.register.base.URtspMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RegisterResChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterResChannelHandler.class);

    private final String ip;
    private final int port;

    ////////////////////////////////////////////////////////////////////////////////

    public RegisterResChannelHandler(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) {
        try {
            UserInfo userInfo = ServiceManager.getInstance().getDashManager().getMyUserInfo();

            RegisterClientNettyChannel rtspRegisterNettyChannel = userInfo.getRegisterClientChannel();
            if (rtspRegisterNettyChannel == null) {
                return;
            }

            ByteBuf buf = datagramPacket.content();
            if (buf == null) {
                logger.warn("DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("Message is null.");
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            URtspHeader uRtspHeader = new URtspHeader(data);
            if (uRtspHeader.getMessageType() == URtspMessageType.REGISTER) {
                UserRegisterRes userRegisterRes = new UserRegisterRes(data);
                logger.debug("[>] {} ({})", userRegisterRes, readBytes);

                int status = userRegisterRes.getStatusCode();
                if (status == UserRegisterRes.SUCCESS) { // OK
                    // RTSP Channel OPEN (New RtspUnit)
                    GuiManager.getInstance().getControlPanel().applyRegistrationButtonStatus();
                    userInfo.setRegistered(true);
                } else if (status == UserRegisterRes.NOT_AUTHORIZED) { // NOT AUTHORIZED
                    // KEY 를 사용하여 MD5 해싱한 값을 다시 REGISTER 에 담아서 전송
                    ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                    messageDigest.update(userRegisterRes.getRealm().getBytes(StandardCharsets.UTF_8));
                    messageDigest.update(UserManager.getHashKey().getBytes(StandardCharsets.UTF_8));
                    byte[] a1 = messageDigest.digest();
                    messageDigest.reset();
                    messageDigest.update(a1);

                    String nonce = new String(messageDigest.digest());
                    rtspRegisterNettyChannel.sendRegister(
                            userInfo.getUserId(),
                            configManager.getRegisterTargetIp(),
                            configManager.getRegisterTargetPort(),
                            nonce
                    );
                    userInfo.setRegistered(false);
                } else {
                    logger.warn("({}) Fail to register the dashUnit. (code={})", userInfo.getUserId(), status);
                }
            } else if (uRtspHeader.getMessageType() == URtspMessageType.UNREGISTER) {
                UserUnRegisterRes userUnRegisterRes = new UserUnRegisterRes(data);
                logger.debug("[>] {} ({})", userUnRegisterRes, readBytes);

                int status = userUnRegisterRes.getStatusCode();
                if (status == UserUnRegisterRes.SUCCESS) { // OK
                    GuiManager.getInstance().getControlPanel().initButtonStatus();
                } else {
                    logger.warn("({}) Fail to unregister the dashUnit. (code={})", userInfo.getUserId(), status);
                }
            }
        } catch (Exception e) {
            logger.warn("RegisterChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("RegisterChannelHandler is inactive.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("RegisterChannelHandler.Exception", cause);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

}
