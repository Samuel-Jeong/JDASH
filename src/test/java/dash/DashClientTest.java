package dash;

import config.ConfigManager;
import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import instance.BaseEnvironment;
import instance.DebugLevel;
import network.definition.NetAddress;
import network.socket.SocketProtocol;
import service.AppInstance;
import service.scheduler.schedule.ScheduleManager;
import service.system.ResourceManager;

import java.util.concurrent.TimeUnit;

public class DashClientTest {

    public void test() {
        ////////////////////////////////////////////////////////////
        String configPath = System.getProperty("user.dir") + "/src/main/resources/config/user_conf.ini";
        ConfigManager configManager = new ConfigManager(configPath);
        AppInstance appInstance = AppInstance.getInstance();
        appInstance.setConfigManager(configManager);
        appInstance.setConfigPath(configPath);

        BaseEnvironment baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                new ResourceManager(5000, 7000),
                DebugLevel.DEBUG
        );
        baseEnvironment.start();
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        //String targetBasePath = "/Users/jamesj/GIT_PROJECTS/udash/src/test/resources/client_test_resources/outdoor_market_ambiance_Dolby";
        String targetBasePath = System.getProperty("user.dir") + "/src/test/resources/client_test_resources/jamesj";
        DashClient dashClient = new DashClient(
                "TEST_1",
                targetBasePath + "/live/jamesj/jamesj.mpd",
                "http://" + configManager.getHttpTargetIp() +
                        ":" + configManager.getHttpTargetPort() +
                        "/live/jamesj/jamesj.mpd",
                        //"/test/outdoor_market_ambiance_Dolby/outdoor_market_ambiance_Dolby.mpd",
                targetBasePath,
                new MpdManager("TEST_1", targetBasePath + "/live/jamesj/jamesj.mpd")
        );
        NetAddress targetAddress = new NetAddress(
                configManager.getHttpTargetIp(),
                configManager.getHttpTargetPort(),
                true, SocketProtocol.TCP
        );
        dashClient.start(new ScheduleManager(), targetAddress);
        ////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////
        dashClient.sendHttpGetRequest("http://192.168.7.33:5858/live/jamesj", MessageType.MPD);
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
