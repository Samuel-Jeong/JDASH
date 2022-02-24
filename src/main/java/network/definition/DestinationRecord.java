package network.definition;

import network.socket.netty.NettyChannel;

public class DestinationRecord {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final long sessionId;
    private final GroupEndpointId groupEndpointId;
    private final NettyChannel nettyChannel;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public DestinationRecord(long sessionId, GroupEndpointId groupEndpointId, NettyChannel nettyChannel) {
        this.sessionId = sessionId;
        this.groupEndpointId = groupEndpointId;
        this.nettyChannel = nettyChannel;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public long getSessionId() {
        return sessionId;
    }

    public GroupEndpointId getGroupEndpointId() {
        return groupEndpointId;
    }

    public NettyChannel getNettyChannel() {
        return nettyChannel;
    }

    @Override
    public String toString() {
        return "DestinationRecord{" +
                "sessionId=" + sessionId +
                ", groupEndpointId=" + groupEndpointId +
                ", nettyChannel=" + nettyChannel +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
