package network.user;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import network.user.register.channel.RegisterClientNettyChannel;
import network.user.register.channel.RegisterServerNettyChannel;
import service.AppInstance;

public class UserInfo {

    ////////////////////////////////////////////////////////////////////////////////
    private final String userId;
    private final long createdTime;
    private String streamingIp;
    private int streamingPort;

    private boolean isRegistered = false;
    private RegisterServerNettyChannel registerServerNettyChannel = null;
    private RegisterClientNettyChannel registerClientNettyChannel = null;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public UserInfo(String userId) {
        this.userId = userId;
        this.createdTime = System.currentTimeMillis();
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void addRegisterClientChannel() {
        if (registerClientNettyChannel != null) {
            return;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        registerServerNettyChannel = new RegisterServerNettyChannel(
                configManager.getRegisterListenIp(),
                configManager.getRegisterListenPort()
        );
        registerServerNettyChannel.run();
        registerServerNettyChannel.start();

        registerClientNettyChannel = new RegisterClientNettyChannel(
                configManager.getRegisterLocalIp(),
                configManager.getRegisterLocalPort()
        );
        registerClientNettyChannel.run();
        registerClientNettyChannel.connect(configManager.getRegisterTargetIp(), configManager.getRegisterTargetPort());
    }

    public void removeRegisterClientChannel() {
        if (registerClientNettyChannel == null) {
            return;
        }

        registerClientNettyChannel.stop();
        registerClientNettyChannel = null;
    }

    public RegisterClientNettyChannel getRegisterClientChannel() {
        return registerClientNettyChannel;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public boolean addRegisterServerChannel() {
        if (registerServerNettyChannel != null) {
            return false;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        registerServerNettyChannel = new RegisterServerNettyChannel(
                configManager.getRegisterListenIp(),
                configManager.getRegisterListenPort()
        );
        registerServerNettyChannel.run();
        registerServerNettyChannel.start();

        return true;
    }

    public void removeRegisterServerChannel() {
        if (registerServerNettyChannel == null) {
            return;
        }

        registerServerNettyChannel.stop();
        registerServerNettyChannel = null;
    }

    public RegisterServerNettyChannel getRegisterServerChannel() {
        return registerServerNettyChannel;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public String getUserId() {
        return userId;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public String getStreamingIp() {
        return streamingIp;
    }

    public void setStreamingIp(String streamingIp) {
        this.streamingIp = streamingIp;
    }

    public int getStreamingPort() {
        return streamingPort;
    }

    public void setStreamingPort(int streamingPort) {
        this.streamingPort = streamingPort;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////////////////////////

}
