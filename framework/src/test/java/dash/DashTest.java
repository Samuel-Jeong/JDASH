package dash;

import dash.component.MPD;
import org.junit.Assert;
import org.junit.Test;

public class DashTest {

    @Test
    public void test() {
        MPD mpd = parseMpdTest();
        Assert.assertNotNull(mpd);
    }

    public static MPD parseMpdTest() {
        DashManager dashManager = new DashManager();
        return dashManager.parseXml("/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/mpd_example1.xml");
    }

}
