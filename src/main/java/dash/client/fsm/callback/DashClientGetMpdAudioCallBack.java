package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.mpd.parser.mpd.Representation;
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

        MpdManager mpdManager = dashClient.getMpdManager();
        if (mpdManager != null) {
            for (Representation representation : mpdManager.getRepresentations(MpdManager.CONTENT_AUDIO_TYPE)) {
                if (representation == null) { continue; }

                String audioInitSegmentName = mpdManager.getAudioInitSegmentName(representation.getId());
                if (audioInitSegmentName == null) {
                    logger.warn("[DashClientGetMpdAudioCallBack] Fail to send http get request for init segment. Audio init segment is not exists. (dashClient={}, representationId={})", dashClient, representation.getId());
                    continue;
                }

                logger.debug("[DashClientGetMpdAudioCallBack] ({}) audioInitSegmentName: {}", representation.getId(), audioInitSegmentName);

                // outdoor_market_ambiance_Dolby_init1.m4s
                String targetAudioInitSegPath = dashClient.getTargetPath(audioInitSegmentName);
                dashClient.setTargetAudioInitSegPath(targetAudioInitSegPath);
                dashClient.sendHttpGetRequest(
                        dashClient.getSourcePath(audioInitSegmentName),
                        MessageType.AUDIO
                );
            }
        }
        ////////////////////////////

        return stateUnit.getCurState();
    }


}
