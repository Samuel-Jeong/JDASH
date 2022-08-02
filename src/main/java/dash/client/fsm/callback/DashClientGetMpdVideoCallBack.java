package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.mpd.parser.mpd.Representation;
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

            MpdManager mpdManager = dashClient.getMpdManager();
            if (mpdManager != null) {
                for (Representation representation : mpdManager.getRepresentations(MpdManager.CONTENT_VIDEO_TYPE)) {
                    if (representation == null) { continue; }

                    String videoInitSegmentName = mpdManager.getVideoInitSegmentName(representation.getId());
                    if (videoInitSegmentName == null) {
                        logger.warn("[DashClientGetMpdVideoCallBack] Fail to send http get request for init segment. Video init segment is not exists. (dashClient={}, representationId={})", dashClient, representation.getId());
                        continue;
                    }

                    logger.debug("[DashClientGetMpdVideoCallBack] ({}) videoInitSegmentName: {}", representation.getId(), videoInitSegmentName);

                    // outdoor_market_ambiance_Dolby_init0.m4s
                    String targetVideoInitSegPath = dashClient.getTargetPath(videoInitSegmentName);
                    dashClient.setTargetVideoInitSegPath(targetVideoInitSegPath);
                    dashClient.sendHttpGetRequest(
                            dashClient.getSourcePath(videoInitSegmentName),
                            MessageType.VIDEO
                    );
                }
            }
        }

        return stateUnit.getCurState();
    }


}
