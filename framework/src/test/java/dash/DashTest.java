package dash;

import dash.component.MPD;
import org.junit.Assert;
import org.junit.Test;

public class DashTest {

    @Test
    public void test() {
        DashManager dashManager = new DashManager();
        dashManager.start();

        MPD mpd = parseMpdTest(dashManager);
        Assert.assertNotNull(mpd);

        dashManager.stop();
    }

    public static MPD parseMpdTest(DashManager dashManager) {
        return dashManager.parseXml("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/mpd_example3.xml");
    }

}
