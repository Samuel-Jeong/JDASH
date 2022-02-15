package network.user.register.handler;

import config.ConfigManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import network.user.UserInfo;
import network.user.UserManager;
import network.user.register.UserRegisterReq;
import network.user.register.UserRegisterRes;
import network.user.register.UserUnRegisterReq;
import network.user.register.UserUnRegisterRes;
import network.user.register.base.URtspHeader;
import network.user.register.base.URtspMessageType;
import network.user.register.channel.RegisterServerNettyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RegisterReqChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterReqChannelHandler.class);

    private final String ip;
    private final int port;

    ////////////////////////////////////////////////////////////////////////////////

    public RegisterReqChannelHandler(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) {
        try {
            UserInfo myUserInfo = ServiceManager.getInstance().getDashManager().getMyUserInfo();

            RegisterServerNettyChannel registerServerNettyChannel = myUserInfo.getRegisterServerChannel();
            if (registerServerNettyChannel == null) {
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

            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            UserManager userManager = ServiceManager.getInstance().getDashManager().getUserManager();

            URtspHeader uRtspHeader = new URtspHeader(data);
            if (uRtspHeader.getMessageType() == URtspMessageType.REGISTER) {
                UserRegisterReq userRegisterReq = new UserRegisterReq(data);
                logger.debug("[>] {} ({})", userRegisterReq, readBytes);

                String userId = userRegisterReq.getId();
                String nonce = userRegisterReq.getNonce();

                UserInfo curUserInfo = userManager.getUserInfo(userId);
                if (curUserInfo == null) { // NOT AUTHORIZED
                    UserRegisterRes userRegisterRes = new UserRegisterRes(
                            configManager.getMagicCookie(),
                            userRegisterReq.getURtspHeader().getMessageType(),
                            userRegisterReq.getURtspHeader().getSeqNumber(),
                            userRegisterReq.getURtspHeader().getTimeStamp(),
                            configManager.getServiceName(),
                            UserRegisterRes.NOT_AUTHORIZED
                    );
                    userRegisterRes.setReason("NOT_AUTHORIZED");

                    // RTSP ID 등록
                    userManager.addUserInfo(userId);
                    UserInfo userInfo = userManager.getUserInfo(userId);
                    if (userInfo != null) {
                        registerServerNettyChannel.sendResponse(datagramPacket.sender().getAddress().getHostAddress(), userRegisterReq.getListenPort(), userRegisterRes);
                    }
                } else {
                    UserRegisterRes registerRtspUnitRes;

                    if (!curUserInfo.isRegistered()) {
                        // 1) Check nonce
                        // 2) If ok, open rtsp channel
                        // 3) If not, reject
                        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                        messageDigest.update(configManager.getServiceName().getBytes(StandardCharsets.UTF_8));
                        messageDigest.update(UserManager.getHashKey().getBytes(StandardCharsets.UTF_8));
                        byte[] a1 = messageDigest.digest();
                        messageDigest.reset();
                        messageDigest.update(a1);

                        String curNonce = new String(messageDigest.digest());
                        if (curNonce.equals(nonce)) {
                            // RTSP Channel OPEN (New RtspUnit)
                            registerRtspUnitRes = new UserRegisterRes(
                                    configManager.getMagicCookie(),
                                    userRegisterReq.getURtspHeader().getMessageType(),
                                    userRegisterReq.getURtspHeader().getSeqNumber(),
                                    userRegisterReq.getURtspHeader().getTimeStamp(),
                                    configManager.getServiceName(),
                                    UserRegisterRes.SUCCESS
                            );
                            curUserInfo.setRegistered(true);
                        } else {
                            registerRtspUnitRes = new UserRegisterRes(
                                    configManager.getMagicCookie(),
                                    userRegisterReq.getURtspHeader().getMessageType(),
                                    userRegisterReq.getURtspHeader().getSeqNumber(),
                                    userRegisterReq.getURtspHeader().getTimeStamp(),
                                    configManager.getServiceName(),
                                    UserRegisterRes.NOT_AUTHORIZED
                            );
                            registerRtspUnitRes.setReason("WRONG_NONCE");

                            // RTSP ID 등록 해제
                            userManager.deleteUserInfo(userId);
                            curUserInfo.setRegistered(false);
                        }
                    } else {
                        registerRtspUnitRes = new UserRegisterRes(
                                configManager.getMagicCookie(),
                                userRegisterReq.getURtspHeader().getMessageType(),
                                userRegisterReq.getURtspHeader().getSeqNumber(),
                                userRegisterReq.getURtspHeader().getTimeStamp(),
                                configManager.getServiceName(),
                                UserRegisterRes.SUCCESS
                        );
                    }

                    registerServerNettyChannel.sendResponse(
                            datagramPacket.sender().getAddress().getHostAddress(),
                            userRegisterReq.getListenPort(),
                            registerRtspUnitRes
                    );
                }
            } else if (uRtspHeader.getMessageType() == URtspMessageType.UNREGISTER) {
                UserUnRegisterReq unRegisterRtspUnitReq = new UserUnRegisterReq(data);
                logger.debug("[>] {} ({})", unRegisterRtspUnitReq, readBytes);

                String userId = unRegisterRtspUnitReq.getId();
                UserInfo curUserInfo = userManager.getUserInfo(userId);

                UserUnRegisterRes unRegisterRtspUnitRes;
                if (curUserInfo == null) {
                    unRegisterRtspUnitRes = new UserUnRegisterRes(
                            configManager.getMagicCookie(),
                            unRegisterRtspUnitReq.getURtspHeader().getMessageType(),
                            unRegisterRtspUnitReq.getURtspHeader().getSeqNumber(),
                            unRegisterRtspUnitReq.getURtspHeader().getTimeStamp(),
                            UserUnRegisterRes.NOT_ACCEPTED
                    );
                } else {
                    unRegisterRtspUnitRes = new UserUnRegisterRes(
                            configManager.getMagicCookie(),
                            unRegisterRtspUnitReq.getURtspHeader().getMessageType(),
                            unRegisterRtspUnitReq.getURtspHeader().getSeqNumber(),
                            unRegisterRtspUnitReq.getURtspHeader().getTimeStamp(),
                            UserUnRegisterRes.SUCCESS
                    );

                    // RTSP ID 등록 해제
                    userManager.deleteUserInfo(userId);
                    curUserInfo.setRegistered(false);
                }

                registerServerNettyChannel.sendResponse(
                        datagramPacket.sender().getAddress().getHostAddress(),
                        unRegisterRtspUnitReq.getListenPort(),
                        unRegisterRtspUnitRes
                );
            }
        } catch (Exception e) {
            logger.warn("RtspRegisterChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("RtspRegisterChannelHandler is inactive.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("RtspRegisterChannelHandler.Exception", cause);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
