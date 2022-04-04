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
import network.definition.DestinationRecord;
import network.definition.GroupEndpointId;
import network.definition.NetAddress;
import network.definition.NetInterface;
import network.socket.netty.NettyChannel;
import network.socket.netty.tcp.NettyTcpClientChannel;
import network.socket.netty.tcp.NettyTcpServerChannel;
import network.socket.netty.udp.NettyUdpChannel;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class GroupSocket { // SEND-ONLY

    ////////////////////////////////////////////////////////////
    // VARIABLES
    transient private final BaseEnvironment baseEnvironment;
    private final NetInterface netInterface;
    private final GroupEndpointId incomingGroupEndpointId;

    private final Socket listenSocket;
    transient private final HashMap<Long, DestinationRecord> destinationMap = new HashMap<>();
    transient private final ReentrantLock destinationMapLock = new ReentrantLock();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public GroupSocket(BaseEnvironment baseEnvironment, NetInterface netInterface, NetAddress listenAddress, ChannelInitializer<?> channelHandler) {
        this.baseEnvironment = baseEnvironment;
        this.netInterface = netInterface;
        this.incomingGroupEndpointId = new GroupEndpointId(listenAddress);
        this.listenSocket = new Socket(baseEnvironment, netInterface, listenAddress, channelHandler);
    }

    public GroupSocket(BaseEnvironment baseEnvironment, NetInterface netInterface, NetAddress listenAddress, NetAddress sourceFilterAddress, ChannelInitializer<?> channelHandler) {
        this.baseEnvironment = baseEnvironment;
        this.netInterface = netInterface;
        this.incomingGroupEndpointId = new GroupEndpointId(listenAddress, sourceFilterAddress);
        this.listenSocket = new Socket(baseEnvironment, netInterface, listenAddress, channelHandler);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public boolean addDestination(NetAddress netAddress, NetAddress sourceFilterAddress, long sessionId, ChannelInitializer<?> channelHandler) {
        if (netAddress == null) { return false; }
        if (netAddress.isIpv4() != incomingGroupEndpointId.isIpv4()) { return false; }

        // Destination 추가 시, SessionId & GroupAddress 같으면 안된다. 둘 중 하나는 달라도 된다.
        int isSameDestination = 0;
        DestinationRecord destinationRecord = getDestination(sessionId);
        if (destinationRecord != null) {
            long curSessionId = destinationRecord.getSessionId();
            if (curSessionId == sessionId) {
                isSameDestination += 1;
            }

            GroupEndpointId groupEndpointId = destinationRecord.getGroupEndpointId();
            if (groupEndpointId != null) {
                NetAddress curNetAddress = groupEndpointId.getGroupAddress();
                if (curNetAddress != null) {
                    if (netAddress.isIpv4()) {
                        Inet4Address curInet4Address = curNetAddress.getInet4Address();
                        Inet4Address inet4Address = netAddress.getInet4Address();
                        if (curInet4Address != null && inet4Address != null) {
                            if (curInet4Address.equals(inet4Address)) {
                                isSameDestination += 1;
                            }
                        }
                    } else {
                        Inet6Address curInet6Address = curNetAddress.getInet6Address();
                        Inet6Address inet6Address = netAddress.getInet6Address();
                        if (curInet6Address != null && inet6Address != null) {
                            if (curInet6Address.equals(inet6Address)) {
                                isSameDestination += 1;
                            }
                        }
                    }
                }
            }
        }
        if (isSameDestination == 2) {
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to add the channel. Duplicated destination is detected. (key=%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(),
                    sessionId
            );
            return false;
        }

        NettyChannel nettyChannel;
        if (netAddress.getSocketProtocol().equals(SocketProtocol.TCP)) {
            if (netInterface.isListenOnly()) {
                nettyChannel = new NettyTcpServerChannel(
                        baseEnvironment,
                        sessionId,
                        netInterface.getThreadCount(),
                        netInterface.getRecvBufSize(),
                        (ChannelInitializer<SocketChannel>) channelHandler
                );
            } else {
                nettyChannel = new NettyTcpClientChannel(
                        baseEnvironment,
                        sessionId,
                        netInterface.getThreadCount(),
                        netInterface.getRecvBufSize(),
                        (ChannelInitializer<NioSocketChannel>) channelHandler
                );
            }
        } else {
            nettyChannel = new NettyUdpChannel(
                    baseEnvironment,
                    sessionId,
                    netInterface.getThreadCount(),
                    netInterface.getSendBufSize(),
                    netInterface.getRecvBufSize(),
                    (ChannelInitializer<NioDatagramChannel>) channelHandler
            );
        }

        Channel channel;
        if (netAddress.isIpv4()) {
            channel = nettyChannel.openConnectChannel(netAddress.getInet4Address().getHostAddress(), netAddress.getPort());
        } else {
            channel = nettyChannel.openConnectChannel(netAddress.getInet6Address().getHostAddress(), netAddress.getPort());
        }
        if (channel == null) {
            nettyChannel.closeConnectChannel();
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to add the channel. (key=%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(),
                    sessionId
            );
            return false;
        }

        destinationMapLock.lock();
        try {
            destinationMap.put(
                    sessionId,
                    new DestinationRecord(
                            sessionId,
                            new GroupEndpointId(netAddress, sourceFilterAddress),
                            nettyChannel
                    )
            );
        } catch (Exception e) {
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to add the channel. (key=%s) (%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(),
                    sessionId, e.toString()
            );
            return false;
        } finally {
            destinationMapLock.unlock();
        }

        return true;
    }

    public boolean removeDestination(long sessionId) {
        if (sessionId < 0) { return false; }

        DestinationRecord destinationRecord = getDestination(sessionId);
        if (destinationRecord == null) { return false; }

        destinationRecord.getNettyChannel().closeConnectChannel();
        destinationRecord.getNettyChannel().stop();

        destinationMapLock.lock();
        try {
            destinationMap.remove(sessionId);
        } catch (Exception e) {
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to remove the channel. (key=%s) (%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(),
                    sessionId, e.toString()
            );
            return false;
        } finally {
            destinationMapLock.unlock();
        }

        return true;
    }

    public void removeAllDestinations() {
        try {
            destinationMapLock.lock();

            if (!destinationMap.isEmpty()) {
                int totalEntryCount = 0;

                for (Map.Entry<Long, DestinationRecord> entry : getCloneDestinationMap().entrySet()) {
                    DestinationRecord destinationRecord = entry.getValue();
                    if (destinationRecord == null) {
                        continue;
                    }

                    NettyChannel nettyChannel = destinationRecord.getNettyChannel();
                    if (nettyChannel == null) {
                        continue;
                    }

                    destinationMap.remove(entry.getKey());
                    nettyChannel.stop();
                    totalEntryCount++;
                }

                baseEnvironment.printMsg("[GroupSocket(%s:%s)] Success to close all destination channel(s). (totalEntryCount={})",
                        listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                        listenSocket.getNetAddress().getPort(),
                        totalEntryCount);
            }
        } catch (Exception e) {
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to close all destination(s). (%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(), e.toString()
            );
        } finally {
            destinationMapLock.unlock();
        }

        destinationMap.clear();
    }

    public Map<Long, DestinationRecord> getCloneDestinationMap() {
        HashMap<Long, DestinationRecord> cloneMap;

        try {
            destinationMapLock.lock();

            try {
                cloneMap = (HashMap<Long, DestinationRecord>) destinationMap.clone();
            } catch (Exception e) {
                baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] Fail to clone the destination map.",
                        listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                        listenSocket.getNetAddress().getPort()
                );
                cloneMap = destinationMap;
            }
        } catch (Exception e) {
            baseEnvironment.printMsg(DebugLevel.WARN, "[GroupSocket(%s:%s)] getCloneDestinationMap.Exception (%s)",
                    listenSocket.getNetAddress().isIpv4()? listenSocket.getNetAddress().getInet4Address() : listenSocket.getNetAddress().getInet6Address(),
                    listenSocket.getNetAddress().getPort(), e.toString()
            );
            return null;
        } finally {
            destinationMapLock.unlock();
        }

        return cloneMap;
    }

    public DestinationRecord getDestination(long sessionId) {
        return destinationMap.get(sessionId);
    }

    public Socket getListenSocket() {
        return listenSocket;
    }

    public GroupEndpointId getIncomingGroupEndpointId() {
        return incomingGroupEndpointId;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
