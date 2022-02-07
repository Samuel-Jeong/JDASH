package service;

import config.ConfigManager;

public class AppInstance {

    private static AppInstance instance = null;

    private int instanceId = 0;

    private String configPath = null;
    private ConfigManager configManager = null;

    private int cpuUsage = 0;
    private int memUsage = 0;

    ////////////////////////////////////////////////////////////////////////////////

    public AppInstance() {
        // Nothing
    }

    public static AppInstance getInstance ( ) {
        if (instance == null) {
            instance = new AppInstance();
        }
        return instance;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getInstanceId ( ) {
        return instanceId;
    }

    public void setInstanceId (int instanceId) {
        this.instanceId = instanceId;
    }

    public String getConfigPath ( ) {
        return configPath;
    }

    public void setConfigPath (String configPath) {
        this.configPath = configPath;
    }

    public int getCpuUsage ( ) {
        return cpuUsage;
    }

    public void setCpuUsage (int cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public int getMemUsage ( ) {
        return memUsage;
    }

    public void setMemUsage (int memUsage) {
        this.memUsage = memUsage;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
}
