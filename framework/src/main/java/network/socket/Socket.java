package network.socket;

import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import network.definition.NetAddress;
import network.definition.NetInterface;
import network.socket.netty.NettyChannel;
import network.socket.netty.tcp.NettyTcpClientChannel;
import network.socket.netty.tcp.NettyTcpServerChannel;
import network.socket.netty.udp.NettyUdpChannel;

public class Socket {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final BaseEnvironment baseEnvironment;
    private final NetAddress netAddress;
    private final SocketProtocol socketProtocol;
    private final NettyChannel nettyChannel;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public Socket(BaseEnvironment baseEnvironment, NetInterface netInterface, NetAddress netAddress, ChannelInitializer<?> channelHandler) {
        this.baseEnvironment = baseEnvironment;
        this.netAddress = netAddress;
        this.socketProtocol = netAddress.getSocketProtocol();

        if (socketProtocol.equals(SocketProtocol.TCP)) {
            if (netInterface.isListenOnly()) {
                nettyChannel = new NettyTcpServerChannel(
                        baseEnvironment,
                        0,
                        netInterface.getThreadCount(),
                        netInterface.getRecvBufSize(),
                        (ChannelInitializer<SocketChannel>) channelHandler
                );
            } else {
                nettyChannel = new NettyTcpClientChannel(
                        baseEnvironment,
                        0,
                        netInterface.getThreadCount(),
                        netInterface.getRecvBufSize(),
                        (ChannelInitializer<NioSocketChannel>) channelHandler
                );
            }
        } else {
            nettyChannel = new NettyUdpChannel(
                    baseEnvironment,
                    0,
                    netInterface.getThreadCount(),
                    netInterface.getSendBufSize(),
                    netInterface.getRecvBufSize(),
                    (ChannelInitializer<NioDatagramChannel>) channelHandler
            );
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public boolean openListenChannel() {
        Channel channel;
        if (netAddress.isIpv4()) {
            channel = nettyChannel.openListenChannel(netAddress.getInet4Address().getHostAddress(), netAddress.getPort());
        } else {
            channel = nettyChannel.openListenChannel(netAddress.getInet6Address().getHostAddress(), netAddress.getPort());
        }
        if (channel == null) {
            nettyChannel.closeListenChannel();
            baseEnvironment.printMsg(DebugLevel.WARN, "Fail to add the listen channel.");
            return false;
        }
        return true;
    }

    public void closeListenChannel() {
        nettyChannel.closeListenChannel();
    }

    public void stop() {
        nettyChannel.stop();
    }

    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public NetAddress getNetAddress() {
        return netAddress;
    }

    public SocketProtocol getSocketProtocol() {
        return socketProtocol;
    }

    public void reset() {
        if (netAddress == null) { return; }
        netAddress.clear();
    }

    @Override
    public String toString() {
        return "Socket{" +
                "baseEnvironment=" + baseEnvironment +
                ", netAddress=" + netAddress +
                ", socketProtocol=" + socketProtocol.name() +
                ", nettyChannel=" + nettyChannel +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
