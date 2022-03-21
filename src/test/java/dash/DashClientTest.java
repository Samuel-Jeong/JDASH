package dash;

import config.ConfigManager;
import dash.client.DashClient;
import instance.BaseEnvironment;
import instance.DebugLevel;
import org.junit.Test;
import service.AppInstance;
import service.scheduler.schedule.ScheduleManager;

import java.util.concurrent.TimeUnit;

public class DashClientTest {

    @Test
    public void test() {
        ////////////////////////////////////////////////////////////
        String configPath = "/Users/jamesj/GIT_PROJECTS/udash/src/main/resources/config/user_conf.ini";
        ConfigManager configManager = new ConfigManager(configPath);
        AppInstance appInstance = AppInstance.getInstance();
        appInstance.setConfigManager(configManager);
        appInstance.setConfigPath(configPath);

        BaseEnvironment baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                null,
                DebugLevel.DEBUG
        );
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        String targetBasePath = "/Users/jamesj/GIT_PROJECTS/udash/src/test/resources/client_test_resources/outdoor_market_ambiance_Dolby";
        DashClient dashClient = new DashClient(
                "TEST_1", baseEnvironment,
                "http://" + configManager.getHttpTargetIp() +
                        ":" + configManager.getHttpTargetPort() +
                        "/test/outdoor_market_ambiance_Dolby/outdoor_market_ambiance_Dolby.mpd",
                targetBasePath
        );
        dashClient.start();
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        dashClient.sendHttpGetRequest("http://192.168.7.33:5858/test/outdoor_market_ambiance_Dolby.mpd");
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        int curSec = 0;
        int limitSec = 5;
        TimeUnit timeUnit = TimeUnit.SECONDS;
        while (true) {
            try {
                timeUnit.sleep(1);
                curSec++;

                if (limitSec == curSec) {
                    break;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        dashClient.stop();
        ////////////////////////////////////////////////////////////
    }

}
