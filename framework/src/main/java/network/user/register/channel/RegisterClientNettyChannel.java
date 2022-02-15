package network.user.register.channel;

import config.ConfigManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import network.user.register.UserRegisterReq;
import network.user.register.UserUnRegisterReq;
import network.user.register.base.URegisterMessageType;
import network.user.register.handler.RegisterResChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class RegisterClientNettyChannel {

    private static final Logger log = LoggerFactory.getLogger(RegisterClientNettyChannel.class);

    private final String ip;
    private final int port;

    private Channel sendChannel = null;
    private Channel listenChannel = null;
    private Bootstrap bootstrap;

    private int seqNum = 1;

    ////////////////////////////////////////////////////////////////////////////////

    public RegisterClientNettyChannel(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void run () {
        bootstrap = new Bootstrap();
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup(configManager.getThreadCount());

        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .option(ChannelOption.SO_SNDBUF, configManager.getSendBufSize())
                .option(ChannelOption.SO_RCVBUF, configManager.getRecvBufSize())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    public void initChannel (final NioDatagramChannel ch) {
                        final ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new RegisterResChannelHandler(ip, port));
                    }
                });

        start();
    }

    private void start() {
        if (listenChannel != null) {
            return;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            log.warn("UnknownHostException is occurred. (ip={})", ip, e);
            return;
        }

        try {
            ChannelFuture channelFuture = bootstrap.bind(address, port).sync();
            if (channelFuture == null) {
                log.warn("Fail to start the dash register client listen channel. (ip={}, port={})", ip, port);
                return;
            }

            listenChannel = channelFuture.channel();
            log.debug("Success to start the dash register client listen channel. (ip={}, port={})", ip, port);
        } catch (Exception e) {
            log.warn("Fail to start the dash register client listen channel. (ip={}, port={})", ip, port, e);
            Thread.currentThread().interrupt();
        }
    }

    public void connect(String targetIp, int targetPort) {
        if (sendChannel != null) {
            return;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(targetIp);
        } catch (UnknownHostException e) {
            log.warn("UnknownHostException is occurred. (ip={})", targetIp, e);
            return;
        }

        try {
            ChannelFuture channelFuture = bootstrap.connect(address, targetPort).sync();
            if (channelFuture == null) {
                log.warn("Fail to start the dash register send channelFuture. (ip={}, port={})", targetIp, targetPort);
                return;
            }

            sendChannel = channelFuture.channel();
            log.debug("Success to start the dash register client send channel. (ip={}, port={})", targetIp, targetPort);
        } catch (Exception e) {
            log.warn("Fail to start the dash register client send channel. (ip={}, port={}) {}", targetIp, targetPort, e);
        }
    }

    public void stop() {
        if (listenChannel == null) {
            log.warn("Fail to stop the dash register client listen listen channel. (ip={}, port={})", ip, port);
            return;
        }

        listenChannel.close();
        listenChannel = null;

        if (sendChannel == null) {
            log.warn("Fail to stop the dash register client listen send channel. (ip={}, port={})", ip, port);
            return;
        }

        sendChannel.close();
        sendChannel = null;

        seqNum = 1;
        log.debug("Success to stop the dash register client listen channels. (ip={}, port={})", ip, port);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void sendRegister(String id, String targetIp, int targetPort, String nonce) {
        if (sendChannel == null) {
            return;
        }

        UserRegisterReq userRegisterReq = new UserRegisterReq(
                AppInstance.getInstance().getConfigManager().getMagicCookie(),
                URegisterMessageType.REGISTER, seqNum, System.currentTimeMillis(),
                id, 7200, (short) port
        );

        if (nonce != null) {
            userRegisterReq.setNonce(nonce);
        }

        InetSocketAddress inetSocketAddress = new InetSocketAddress(targetIp, targetPort);
        sendChannel.writeAndFlush(
                new DatagramPacket(
                        Unpooled.copiedBuffer(userRegisterReq.getByteData()),
                        inetSocketAddress
                )
        );

        seqNum++;
        log.debug("[<] {} ({})", userRegisterReq, userRegisterReq.getByteData().length);
    }

    public void sendUnRegister(String userId, String targetIp, int targetPort) {
        if (sendChannel == null) {
            return;
        }

        UserUnRegisterReq userUnRegisterReq = new UserUnRegisterReq(
                AppInstance.getInstance().getConfigManager().getMagicCookie(),
                URegisterMessageType.UNREGISTER, seqNum, System.currentTimeMillis(),
                userId, (short) port
        );

        InetSocketAddress inetSocketAddress = new InetSocketAddress(targetIp, targetPort);
        sendChannel.writeAndFlush(
                new DatagramPacket(
                        Unpooled.copiedBuffer(userUnRegisterReq.getByteData()),
                        inetSocketAddress
                )
        );

        seqNum++;
        log.debug("[<] {} ({})", userUnRegisterReq, userUnRegisterReq.getByteData().length);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Channel getListenChannel() {
        return listenChannel;
    }
}
