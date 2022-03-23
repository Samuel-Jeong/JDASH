package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;

public class DashClientGetMpdVideoCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetMpdVideoCallBack.class);

    public DashClientGetMpdVideoCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // VIDEO INIT SEGMENT > Optional
        if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
            DashClient dashClient = (DashClient) stateUnit.getData();
            if (dashClient == null) { return null; }

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

        return stateUnit.getCurState();
    }


}
