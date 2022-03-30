package dash;

import config.ConfigManager;
import dash.mpd.MpdManager;
import dash.server.DashServer;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.module.FileManager;

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
        //Assert.assertTrue(parseMpdTest(new DashServer()));

        DashServer dashServer = new DashServer();
        MpdManager mpdManager = dashServer.getMpdManager();
        mpdManager.parseMpd(
                "/Users/jamesj/GIT_PROJECTS/udash/src/test/resources/test/jamesj2.mpd",
                false
        );

        logger.debug("Audio segment start number : {}",
                calculateMediaSegmentNumberTest(mpdManager, MpdManager.CONTENT_AUDIO_TYPE)
        );
        logger.debug("Video segment start number : {}",
                calculateMediaSegmentNumberTest(mpdManager, MpdManager.CONTENT_VIDEO_TYPE)
        );
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

        // 3) KAFKA TEST
        // 3-1) DashStartReq MpdUri Parsing
        Assert.assertTrue(handleDashStartReqTest("http://127.0.0.1:5858/live/jamesj"));

        /////////////////////////////////////////////
        //testRtmpSubscribe1();
        /////////////////////////////////////////////
    }

    public static boolean parseMpdTest(DashServer dashServer) {
        return dashServer
                .getMpdManager()
                .parseMpd(
                        "/Users/jamesj/GIT_PROJECTS/udash/src/test/resources/mpd_examples/mpd_example4.xml",
                        false
                );
    }

    public boolean handleDashStartReqTest(String mpdUri) {
        // http://127.0.0.1:5858/live/jamesj

        FileManager fileManager = new FileManager();

        String publishType = fileManager.getParentPathFromUri(mpdUri); // http://127.0.0.1:5858/live
        String address = fileManager.getParentPathFromUri(publishType);
        String ipAddress = address.substring(address.lastIndexOf("/") + 1);
        int portNumber = Integer.parseInt(ipAddress.substring(ipAddress.lastIndexOf(":") + 1));
        ipAddress = ipAddress.substring(0, ipAddress.lastIndexOf(":"));
        publishType = fileManager.getFileNameFromUri(publishType); // live
        String streamKey = fileManager.getFileNameFromUri(mpdUri); // jamesj

        Assert.assertEquals(publishType, "live");
        Assert.assertEquals(streamKey, "jamesj");
        logger.debug("[handleDashStartReqTest] publishType: {}, streamKey: {}", publishType, streamKey);
        logger.debug("[handleDashStartReqTest] ipAddress: {}, portNumber: {}", ipAddress, portNumber);

        return true;
    }

    public long calculateMediaSegmentNumberTest(MpdManager mpdManager, String contentType) {
        mpdManager.calculateSegmentNumber(contentType);
        if (contentType.equals(MpdManager.CONTENT_VIDEO_TYPE)) {
            return mpdManager.getVideoSegmentSeqNum();
        } else {
            return mpdManager.getAudioSegmentSeqNum();
        }
    }

}
