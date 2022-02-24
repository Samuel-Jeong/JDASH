package network.stats;

import util.type.UINT32_T;

public class NetInterfaceTrafficStats {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private float totalNumPackets = 0;
    private float totalNumBytes = 0;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NetInterfaceTrafficStats() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public void countPacket(UINT32_T packetSize) {
        totalNumPackets += 1.0;
        totalNumBytes += packetSize.value;
    }

    public boolean haveSeenTraffic() {
        return totalNumPackets != 0.0;
    }

    public float getTotalNumPackets() {
        return totalNumPackets;
    }

    public float getTotalNumBytes() {
        return totalNumBytes;
    }

    public void clear() {
        totalNumPackets = 0;
        totalNumBytes = 0;
    }
    ////////////////////////////////////////////////////////////

}
