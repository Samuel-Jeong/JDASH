package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;

public class DashClientGetMpdAudioCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetMpdAudioCallBack.class);

    public DashClientGetMpdAudioCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // AUDIO INIT SEGMENT > Mandatory
        // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

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
