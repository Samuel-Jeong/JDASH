package network.socket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    transient private final BaseEnvironment baseEnvironment;
    private final NetAddress netAddress;
    transient private final NettyChannel nettyChannel;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public Socket(BaseEnvironment baseEnvironment, NetInterface netInterface, NetAddress netAddress, ChannelInitializer<?> channelHandler) {
        this.baseEnvironment = baseEnvironment;
        this.netAddress = netAddress;

        if (netAddress.getSocketProtocol().equals(SocketProtocol.TCP)) {
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
            baseEnvironment.printMsg(DebugLevel.WARN, "[Socket(%s:%s)] Fail to add the listen channel.",
                    netAddress.isIpv4()? netAddress.getInet4Address() : netAddress.getInet6Address(),
                    netAddress.getPort()
            );
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

    public void reset() {
        if (netAddress == null) { return; }
        netAddress.clear();
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
