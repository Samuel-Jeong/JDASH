package network.definition;

import util.type.UINT8_T;

public class GroupEndpointId {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private NetAddress groupAddress;
    private NetAddress sourceFilterAddress; // > null 이 아니면 SSM group
    private UINT8_T ttl;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public GroupEndpointId() {}

    public GroupEndpointId(NetAddress groupAddress) {
        this.groupAddress = groupAddress;
    }

    public GroupEndpointId(NetAddress groupAddress, NetAddress sourceFilterAddress) {
        this.groupAddress = groupAddress;
        this.sourceFilterAddress = sourceFilterAddress;
    }

    public GroupEndpointId(NetAddress groupAddress, NetAddress sourceFilterAddress, UINT8_T ttl) {
        this.groupAddress = groupAddress;
        this.sourceFilterAddress = sourceFilterAddress;
        this.ttl = ttl;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    public NetAddress getGroupAddress() {
        return groupAddress;
    }

    public void setGroupAddress(NetAddress groupAddress) {
        this.groupAddress = groupAddress;
    }

    public NetAddress getSourceFilterAddress() {
        return sourceFilterAddress;
    }

    public void setSourceFilterAddress(NetAddress sourceFilterAddress) {
        this.sourceFilterAddress = sourceFilterAddress;
    }

    public UINT8_T getTtl() {
        return ttl;
    }

    public void setTtl(UINT8_T ttl) {
        this.ttl = ttl;
    }

    public boolean isIpv4() {
        return groupAddress.isIpv4();
    }

    public boolean isSsm() {
        if (sourceFilterAddress == null) { return false; }
        return sourceFilterAddress.addressIsNull();
    }

    @Override
    public String toString() {
        return "GroupEndpointId{" +
                "groupAddress=" + groupAddress +
                ", sourceFilterAddress=" + sourceFilterAddress +
                ", ttl=" + ttl +
                '}';
    }
    ////////////////////////////////////////////////////////////

}
