package network.socket;

import instance.BaseEnvironment;
import io.netty.channel.ChannelInitializer;
import network.definition.NetAddress;
import network.definition.NetInterface;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class SocketManager {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final BaseEnvironment baseEnvironment;
    private final NetInterface netInterface;

    private final HashMap<String, GroupSocket> groupSocketMap = new HashMap<>();
    private final ReentrantLock groupSocketMapLock = new ReentrantLock();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public SocketManager(BaseEnvironment baseEnvironment,
                         boolean isStream, boolean listenOnly,
                         int threadCount, int sendBufSize, int recvBufSize) {
        this.baseEnvironment = baseEnvironment;
        this.netInterface = new NetInterface(baseEnvironment, isStream, listenOnly, threadCount, sendBufSize, recvBufSize);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public boolean addSocket(NetAddress netAddress, ChannelInitializer<?> channelHandler) {
        GroupSocket groupSocket = new GroupSocket(baseEnvironment, netInterface, netAddress, channelHandler);

        groupSocketMapLock.lock();
        try {
            return groupSocketMap.putIfAbsent(netAddress.getAddressString(), groupSocket) == null;
        } catch (Exception e) {
            return false;
        } finally {
            groupSocketMapLock.unlock();
        }
    }

    public boolean removeSocket(NetAddress netAddress) {
        if (netAddress == null) { return false; }
        GroupSocket groupSocket = getSocket(netAddress);
        groupSocket.getListenSocket().stop();

        groupSocketMapLock.lock();
        try {
            return groupSocketMap.remove(netAddress.getAddressString()) != null;
        } catch (Exception e) {
            return false;
        } finally {
            groupSocketMapLock.unlock();
        }
    }

    public GroupSocket getSocket(NetAddress netAddress) {
        if (netAddress == null) { return null; }
        return groupSocketMap.get(netAddress.getAddressString());
    }
    ////////////////////////////////////////////////////////////

}
