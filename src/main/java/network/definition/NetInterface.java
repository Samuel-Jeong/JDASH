package network.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import instance.BaseEnvironment;
import network.stats.NetInterfaceTrafficStats;

public class NetInterface {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    transient private final BaseEnvironment baseEnvironment;
    private final boolean isStream; // tcp or udp
    private final boolean listenOnly; // tcp 일 때만 적용됨
    private final int threadCount;
    private final int sendBufSize;
    private final int recvBufSize;
    private NetInterfaceTrafficStats netInterfaceTrafficStats = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NetInterface(BaseEnvironment baseEnvironment, boolean isStream, boolean listenOnly, int threadCount, int sendBufSize, int recvBufSize) {
        this.baseEnvironment = baseEnvironment;
        this.isStream = isStream;
        this.listenOnly = listenOnly;
        this.threadCount = threadCount;
        this.sendBufSize = sendBufSize;
        this.recvBufSize = recvBufSize;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public int getThreadCount() {
        return threadCount;
    }

    public int getSendBufSize() {
        return sendBufSize;
    }

    public int getRecvBufSize() {
        return recvBufSize;
    }

    public void createNewTrafficStats() {
        if (netInterfaceTrafficStats != null) {
            netInterfaceTrafficStats.clear();
        } else {
            netInterfaceTrafficStats = new NetInterfaceTrafficStats();
        }
    }

    public float getTotalNumPackets() {
        if (netInterfaceTrafficStats == null) { return 0; }
        return netInterfaceTrafficStats.getTotalNumPackets();
    }

    public float getTotalNumBytes() {
        if (netInterfaceTrafficStats == null) { return 0; }
        return netInterfaceTrafficStats.getTotalNumBytes();
    }

    public void clearTrafficStats() {
        if (netInterfaceTrafficStats == null) { return; }
        netInterfaceTrafficStats.clear();
    }

    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public boolean isStream() {
        return isStream;
    }

    public boolean isListenOnly() {
        return listenOnly;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
