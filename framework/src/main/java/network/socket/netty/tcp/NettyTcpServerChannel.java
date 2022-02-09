package network.socket.netty.tcp;

import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import network.socket.netty.NettyChannel;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NettyTcpServerChannel extends NettyChannel {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ServerBootstrap serverBootstrap;

    private Channel listenChannel = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NettyTcpServerChannel(BaseEnvironment baseEnvironment, long sessionId, int threadCount, int recvBufSize, ChannelInitializer<SocketChannel> childHandler) {
        super(baseEnvironment, sessionId, threadCount, 0, recvBufSize);

        bossGroup = new NioEventLoopGroup(threadCount);
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup);
        serverBootstrap.channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_RCVBUF, recvBufSize)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE)
                .childHandler(childHandler);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    @Override
    public void stop () {
        getRecvBuf().clear();
        closeListenChannel();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public Channel openListenChannel(String ip, int port) {
        if (listenChannel != null) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "Channel is already opened.");
            return null;
        }

        InetAddress address;
        ChannelFuture channelFuture;

        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "UnknownHostException is occurred. (ip=%s) (%s)", ip, e.toString());
            return null;
        }

        try {
            channelFuture = serverBootstrap.bind(address, port).sync();
            this.listenChannel = channelFuture.channel();
            getBaseEnvironment().printMsg("Channel is opened. (ip=%s, port=%s)", address, port);

            return this.listenChannel;
        } catch (Exception e) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "Channel is interrupted. (address=%s:%s) (%s)", ip, port, e.toString());
            return null;
        }
    }

    @Override
    public void closeListenChannel() {
        if (listenChannel == null) { return; }

        listenChannel.close();
        listenChannel = null;
    }
    ////////////////////////////////////////////////////////////

}
