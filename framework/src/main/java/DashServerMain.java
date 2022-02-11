import cam.CameraManager;
import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;

public class DashServerMain {

    private static final Logger logger = LoggerFactory.getLogger(DashServerMain.class);

    public static void main(String[] args) {
        if (args.length != 2) {
            logger.error("Argument Error. (&0: DashServerMain, &1: config_path)");
            return;
        }

        String configPath = args[1].trim();
        logger.debug("[DashServerMain] Config path: {}", configPath);
        ConfigManager configManager = new ConfigManager(configPath);

        AppInstance appInstance = AppInstance.getInstance();
        appInstance.setConfigManager(configManager);

        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.loop();
    }

}
