package register.channel;

import config.ConfigManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import register.base.URtmpMessageType;
import register.handler.RtmpRegisterClientHandler;
import register.message.RtmpRegisterReq;
import register.message.RtmpUnRegisterReq;
import service.AppInstance;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class RtmpRegisterNettyChannel {

    private static final Logger log = LoggerFactory.getLogger(RtmpRegisterNettyChannel.class);

    private final String ip;
    private final int port;

    private Channel sendChannel = null;
    private Channel listenChannel = null;
    private Bootstrap bootstrap;

    private int seqNum = 1;

    ////////////////////////////////////////////////////////////////////////////////

    public RtmpRegisterNettyChannel(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void run () {
        bootstrap = new Bootstrap();
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);

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
                        pipeline.addLast(new RtmpRegisterClientHandler(ip, port));
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
                log.warn("Fail to start the rtmp register listen channel. (ip={}, port={})", ip, port);
                return;
            }

            listenChannel = channelFuture.channel();
            log.debug("Success to start the rtmp register listen channel. (ip={}, port={})", ip, port);
        } catch (Exception e) {
            log.warn("Fail to start the rtmp register listen channel. (ip={}, port={})", ip, port, e);
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
                log.warn("Fail to start the rtmp register send channelFuture. (ip={}, port={})", targetIp, targetPort);
                return;
            }

            sendChannel = channelFuture.channel();
            log.debug("Success to start the rtmp register send channel. (ip={}, port={})", targetIp, targetPort);
        } catch (Exception e) {
            log.warn("Fail to start the rtmp register send channel. (ip={}, port={}) {}", targetIp, targetPort, e);
        }
    }

    public void stop() {
        if (listenChannel == null) {
            log.warn("Fail to stop the rtmp register listen channel. (ip={}, port={})", ip, port);
            return;
        }

        listenChannel.close();
        listenChannel = null;

        if (sendChannel == null) {
            log.warn("Fail to stop the rtmp register send channel. (ip={}, port={})", ip, port);
            return;
        }

        sendChannel.close();
        sendChannel = null;

        seqNum = 1;
        log.debug("Success to stop the rtmp register channels. (ip={}, port={})", ip, port);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void sendRegister(String dashUnitId, String targetIp, int targetPort, String nonce) {
        if (sendChannel == null) {
            return;
        }

        RtmpRegisterReq registerRtspUnitReq = new RtmpRegisterReq(
                AppInstance.getInstance().getConfigManager().getRegisterMagicCookie(),
                URtmpMessageType.REGISTER, seqNum, System.currentTimeMillis(),
                dashUnitId, 7200, (short) port
        );

        if (nonce != null) {
            registerRtspUnitReq.setNonce(nonce);
        }

        InetSocketAddress inetSocketAddress = new InetSocketAddress(targetIp, targetPort);
        sendChannel.writeAndFlush(
                new DatagramPacket(
                        Unpooled.copiedBuffer(registerRtspUnitReq.getByteData()),
                        inetSocketAddress
                )
        );

        seqNum++;
        log.debug("[{}:{} <] {} ({})", targetIp, targetPort, registerRtspUnitReq, registerRtspUnitReq.getByteData().length);
    }

    public void sendUnRegister(String dashUnitId, String targetIp, int targetPort) {
        if (sendChannel == null) {
            return;
        }

        RtmpUnRegisterReq unRegisterRtspUnitReq = new RtmpUnRegisterReq(
                AppInstance.getInstance().getConfigManager().getRegisterMagicCookie(),
                URtmpMessageType.UNREGISTER, seqNum, System.currentTimeMillis(),
                dashUnitId, (short) port
        );

        InetSocketAddress inetSocketAddress = new InetSocketAddress(targetIp, targetPort);
        sendChannel.writeAndFlush(
                new DatagramPacket(
                        Unpooled.copiedBuffer(unRegisterRtspUnitReq.getByteData()),
                        inetSocketAddress
                )
        );

        seqNum++;
        log.debug("[{}:{} <] {} ({})", targetIp, targetPort, unRegisterRtspUnitReq, unRegisterRtspUnitReq.getByteData().length);
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
