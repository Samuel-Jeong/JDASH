package dash;

import dash.component.MPD;
import dash.simulation.DashHttpMessageSender;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DashTest {

    private static final Logger logger = LoggerFactory.getLogger(DashTest.class);

    @Test
    public void test() {
        DashManager dashManager = new DashManager();
        dashManager.start();

        MPD mpd = parseMpdTest(dashManager);
        Assert.assertNotNull(mpd);

        /////////////////////////////////////////////
        // HTTP TEST
        DashHttpMessageSender dashHttpSender = new DashHttpMessageSender();
        dashHttpSender.start();

        TimeUnit timeUnit = TimeUnit.SECONDS;
        try {
            dashHttpSender.sendSampleMessage();

            timeUnit.sleep(2);
        } catch (Exception e) {
            logger.warn("DashTest.test.Exception", e);
        }

        dashHttpSender.stop();
        /////////////////////////////////////////////

        dashManager.stop();
    }

    public static MPD parseMpdTest(DashManager dashManager) {
        return dashManager.parseXml("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/mpd_example3.xml");
    }

}
