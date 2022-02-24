package service.system;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

public class SystemManager {

    private static SystemManager systemManager = null;

    private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    public SystemManager() {
        // Nothing
    }

    public static SystemManager getInstance ( ) {
        if (systemManager == null) {
            systemManager = new SystemManager();
        }

        return systemManager;
    }

    public String getCpuUsage () {
        return String.format("%.2f",
                osBean.getSystemCpuLoad() * 100
        );
    }

    public String getMemoryFreeSpace () {
        return String.format("%.2f",
                (double) osBean.getFreePhysicalMemorySize() / 1024 / 1024 / 1024
        );
    }

    public String getMemoryTotalSpace () {
        return String.format("%.2f",
                (double) osBean.getTotalPhysicalMemorySize() / 1024 / 1024 / 1024
        );
    }

    public String getMemoryUsage () {
        return String.format("%.2f",
                (double) (osBean.getTotalPhysicalMemorySize() - osBean.getFreePhysicalMemorySize()) / 1024 / 1024 / 1024
        );
    }

    public String getHeapMemoryUsage () {
        long curHeapMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxHeapMemory = Runtime.getRuntime().maxMemory();
        return String.format("%.2f/%.2f",
                (double) (curHeapMemory) / 1024 / 1024,
                (double) (maxHeapMemory) / 1024 / 1024
        );
    }

    public String getOs () {
        return System.getProperty("os.name").toLowerCase();
    }

}
