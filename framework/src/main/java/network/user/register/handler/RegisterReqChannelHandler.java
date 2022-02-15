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
import network.user.register.base.URegisterHeader;
import network.user.register.base.URegisterMessageType;
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

            URegisterHeader URegisterHeader = new URegisterHeader(data);
            if (URegisterHeader.getMessageType() == URegisterMessageType.REGISTER) {
                UserRegisterReq userRegisterReq = new UserRegisterReq(data);
                logger.debug("[>] {} ({})", userRegisterReq, readBytes);

                String userId = userRegisterReq.getId();
                String nonce = userRegisterReq.getNonce();

                UserInfo curUserInfo = userManager.getUserInfo(userId);
                if (curUserInfo == null) { // NOT AUTHORIZED
                    UserRegisterRes userRegisterRes = new UserRegisterRes(
                            configManager.getMagicCookie(),
                            userRegisterReq.getURegisterHeader().getMessageType(),
                            userRegisterReq.getURegisterHeader().getSeqNumber(),
                            userRegisterReq.getURegisterHeader().getTimeStamp(),
                            configManager.getServiceName(),
                            UserRegisterRes.NOT_AUTHORIZED
                    );
                    userRegisterRes.setReason("NOT_AUTHORIZED");

                    // User ID 등록
                    UserInfo userInfo = userManager.addUserInfo(userId);
                    if (userInfo != null) {
                        registerServerNettyChannel.sendResponse(datagramPacket.sender().getAddress().getHostAddress(), userRegisterReq.getListenPort(), userRegisterRes);
                    }
                } else {
                    UserRegisterRes userRegisterRes;

                    if (!curUserInfo.isRegistered()) {
                        // 1) Check nonce
                        // 2) If ok, open dash channel
                        // 3) If not, reject
                        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                        messageDigest.update(configManager.getServiceName().getBytes(StandardCharsets.UTF_8));
                        messageDigest.update(UserManager.getHashKey().getBytes(StandardCharsets.UTF_8));
                        byte[] a1 = messageDigest.digest();
                        messageDigest.reset();
                        messageDigest.update(a1);

                        String curNonce = new String(messageDigest.digest());
                        if (curNonce.equals(nonce)) {
                            // DASH Channel OPEN (New UserInfo)
                            userRegisterRes = new UserRegisterRes(
                                    configManager.getMagicCookie(),
                                    userRegisterReq.getURegisterHeader().getMessageType(),
                                    userRegisterReq.getURegisterHeader().getSeqNumber(),
                                    userRegisterReq.getURegisterHeader().getTimeStamp(),
                                    configManager.getServiceName(),
                                    UserRegisterRes.SUCCESS
                            );
                            curUserInfo.setRegistered(true);
                        } else {
                            userRegisterRes = new UserRegisterRes(
                                    configManager.getMagicCookie(),
                                    userRegisterReq.getURegisterHeader().getMessageType(),
                                    userRegisterReq.getURegisterHeader().getSeqNumber(),
                                    userRegisterReq.getURegisterHeader().getTimeStamp(),
                                    configManager.getServiceName(),
                                    UserRegisterRes.NOT_AUTHORIZED
                            );
                            userRegisterRes.setReason("WRONG_NONCE");

                            // UserInfo ID 등록 해제
                            if (userManager.deleteUserInfo(userId) != null) {
                                curUserInfo.setRegistered(false);
                            }
                        }
                    } else {
                        userRegisterRes = new UserRegisterRes(
                                configManager.getMagicCookie(),
                                userRegisterReq.getURegisterHeader().getMessageType(),
                                userRegisterReq.getURegisterHeader().getSeqNumber(),
                                userRegisterReq.getURegisterHeader().getTimeStamp(),
                                configManager.getServiceName(),
                                UserRegisterRes.SUCCESS
                        );
                    }

                    registerServerNettyChannel.sendResponse(
                            datagramPacket.sender().getAddress().getHostAddress(),
                            userRegisterReq.getListenPort(),
                            userRegisterRes
                    );
                }
            } else if (URegisterHeader.getMessageType() == URegisterMessageType.UNREGISTER) {
                UserUnRegisterReq userUnRegisterReq = new UserUnRegisterReq(data);
                logger.debug("[>] {} ({})", userUnRegisterReq, readBytes);

                String userId = userUnRegisterReq.getId();
                UserInfo curUserInfo = userManager.getUserInfo(userId);

                UserUnRegisterRes userUnRegisterRes;
                if (curUserInfo == null) {
                    userUnRegisterRes = new UserUnRegisterRes(
                            configManager.getMagicCookie(),
                            userUnRegisterReq.getuRegisterHeader().getMessageType(),
                            userUnRegisterReq.getuRegisterHeader().getSeqNumber(),
                            userUnRegisterReq.getuRegisterHeader().getTimeStamp(),
                            UserUnRegisterRes.NOT_ACCEPTED
                    );
                } else {
                    userUnRegisterRes = new UserUnRegisterRes(
                            configManager.getMagicCookie(),
                            userUnRegisterReq.getuRegisterHeader().getMessageType(),
                            userUnRegisterReq.getuRegisterHeader().getSeqNumber(),
                            userUnRegisterReq.getuRegisterHeader().getTimeStamp(),
                            UserUnRegisterRes.SUCCESS
                    );

                    // DASH ID 등록 해제
                    if (userManager.deleteUserInfo(userId) != null) {
                        curUserInfo.setRegistered(false);
                    }
                }

                registerServerNettyChannel.sendResponse(
                        datagramPacket.sender().getAddress().getHostAddress(),
                        userUnRegisterReq.getListenPort(),
                        userUnRegisterRes
                );
            }
        } catch (Exception e) {
            logger.warn("RegisterReqChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("RegisterReqChannelHandler is inactive.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("RegisterReqChannelHandler.Exception", cause);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
