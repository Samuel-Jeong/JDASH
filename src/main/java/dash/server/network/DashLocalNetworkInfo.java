package dash.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import network.definition.NetAddress;
import network.socket.GroupSocket;

public class DashLocalNetworkInfo {

    // ADDRESS : SOCKET = 1 : 1
    transient private NetAddress localListenAddress;
    private GroupSocket localGroupSocket;

    public DashLocalNetworkInfo(NetAddress localListenAddress, GroupSocket localGroupSocket) {
        this.localListenAddress = localListenAddress;
        this.localGroupSocket = localGroupSocket;
    }

    public NetAddress getLocalListenAddress() {
        return localListenAddress;
    }

    public void setLocalListenAddress(NetAddress localListenAddress) {
        this.localListenAddress = localListenAddress;
    }

    public GroupSocket getLocalGroupSocket() {
        return localGroupSocket;
    }

    public void setLocalGroupSocket(GroupSocket localGroupSocket) {
        this.localGroupSocket = localGroupSocket;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}
