package network.definition;

import network.socket.netty.NettyChannel;

public class DestinationRecord {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final String sessionId;
    private final GroupEndpointId groupEndpointId;
    private final NettyChannel nettyChannel;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public DestinationRecord(String sessionId, GroupEndpointId groupEndpointId, NettyChannel nettyChannel) {
        this.sessionId = sessionId;
        this.groupEndpointId = groupEndpointId;
        this.nettyChannel = nettyChannel;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public String getSessionId() {
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
