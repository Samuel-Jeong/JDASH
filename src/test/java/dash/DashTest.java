package dash;

import config.ConfigManager;
import dash.server.DashServer;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dash.mpd.parser.mpd.MPD;
import service.AppInstance;

public class DashTest {

    private static final Logger logger = LoggerFactory.getLogger(DashTest.class);

    @Test
    public void test() {
        ////////////////////////////////////////////////////////////
        String configPath = "/Users/jamesj/GIT_PROJECTS/udash/src/main/resources/config/user_conf.ini";
        ConfigManager configManager = new ConfigManager(configPath);
        AppInstance appInstance = AppInstance.getInstance();
        appInstance.setConfigManager(configManager);
        appInstance.setConfigPath(configPath);
        ////////////////////////////////////////////////////////////

        /////////////////////////////////////////////
        // 1) MPD PARSING TEST
        Assert.assertTrue(parseMpdTest(new DashServer()));
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        // 2) HTTP COMMUNICATION TEST
        /*DashHttpMessageSender dashHttpSender = new DashHttpMessageSender();
        dashHttpSender.start();

        TimeUnit timeUnit = TimeUnit.SECONDS;
        try {
            dashHttpSender.sendSampleMessage();

            *//*timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();

            timeUnit.sleep(1);
            dashHttpSender.sendSampleMessage();*//*

            timeUnit.sleep(2);
        } catch (Exception e) {
            logger.warn("DashTest.test.Exception", e);
        }

        dashHttpSender.stop();*/
        /////////////////////////////////////////////

        /////////////////////////////////////////////
        //testRtmpSubscribe1();
        /////////////////////////////////////////////
    }

    public static boolean parseMpdTest(DashServer dashServer) {
        return dashServer
                .getMpdManager()
                .parseMpd(
                        "/Users/jamesj/GIT_PROJECTS/JDASH/src/test/resources/mpd_examples/mpd_example4.xml"
                );
    }

}
