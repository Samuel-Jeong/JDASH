package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;

public class DashClientGetMpdCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetMpdCallBack.class);

    public DashClientGetMpdCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // GET MPD DONE > PARSE MPD & GET META DATA
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

        if (!dashClient.getMpdManager().parseMpd(dashClient.getTargetMpdPath())) {
            logger.warn("[DashClientMpdDoneCallBack] Fail to parse the mpd. (dashClient={})", dashClient);
            return null;
        }

        if (AppInstance.getInstance().getConfigManager().isEnableValidation()) {
            if (dashClient.getMpdManager().validate()) {
                logger.debug("[DashHttpClientHandler({})] Success to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
            } else {
                logger.warn("[DashHttpClientHandler({})] Fail to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
                dashClient.stop();
                return null;
            }
        }
        ////////////////////////////

        ////////////////////////////
        // VIDEO INIT SEGMENT > Optional
        if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String videoInitSegmentName = dashClient.getMpdManager().getVideoInitSegmentName();
            if (videoInitSegmentName != null) {
                // outdoor_market_ambiance_Dolby_init0.m4s
                String targetVideoInitSegPath = dashClient.getTargetPath(videoInitSegmentName);
                dashClient.setTargetVideoInitSegPath(targetVideoInitSegPath);
                dashClient.sendHttpGetRequest(
                        dashClient.getSourcePath(videoInitSegmentName),
                        MessageType.VIDEO
                );
                logger.debug("videoInitSegmentName: {}", targetVideoInitSegPath);
            }
        }

        ////////////////////////////
        // AUDIO INIT SEGMENT > Mandatory
        // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
        String audioInitSegmentName = dashClient.getMpdManager().getAudioInitSegmentName();
        if (audioInitSegmentName == null) {
            logger.warn("[DashClientMpdDoneCallBack] Fail to send http get request for init segment. Audio init segment is not exists. (dashClient={})", dashClient);
            return null;
        }

        // outdoor_market_ambiance_Dolby_init1.m4s
        String targetAudioInitSegPath = dashClient.getTargetPath(audioInitSegmentName);
        dashClient.setTargetAudioInitSegPath(targetAudioInitSegPath);
        dashClient.sendHttpGetRequest(
                dashClient.getSourcePath(audioInitSegmentName),
                MessageType.AUDIO
        );
        logger.debug("audioInitSegmentName: {}", targetAudioInitSegPath);
        ////////////////////////////

        return stateUnit.getCurState();
    }


}
