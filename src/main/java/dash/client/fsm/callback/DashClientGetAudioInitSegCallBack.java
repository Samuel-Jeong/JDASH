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
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashClientGetAudioInitSegCallBack extends CallBack {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashClientGetAudioInitSegCallBack.class);

    private static final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashClientGetAudioInitSegCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // GET MPD DONE > PARSE MPD & GET META DATA
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

        MpdManager mpdManager = dashClient.getMpdManager();
        if (mpdManager != null) {
            for (Representation representation : mpdManager.getRepresentations(MpdManager.CONTENT_AUDIO_TYPE)) {
                if (representation == null) { continue; }
                logger.debug("AUDIO INIT CALL BACK representation: {}", representation);

                long audioSegmentDuration = dashClient.getMpdManager().getAudioSegmentDuration(representation.getId());
                if (audioSegmentDuration > 0) { // 1000000
                    try {
                        timeUnit.sleep(audioSegmentDuration);
                        logger.trace("[DashClientGetAudioInitSegCallBack({})] [AUDIO({})] Waiting... ({})",
                                dashClient.getDashUnitId(), representation.getId(), audioSegmentDuration
                        );
                    } catch (Exception e) {
                        //logger.warn("");
                    }
                }

                String audioSegmentName = mpdManager.getAudioMediaSegmentName(representation.getId());
                logger.debug("[DashClientGetAudioInitSegCallBack({})] RepresentationId={}, audioSegmentName={}",
                        dashClient.getDashUnitId(), representation.getId(), audioSegmentName
                );
                dashClient.sendHttpGetRequest(
                        fileManager.concatFilePath(
                                dashClient.getSrcPath(),
                                audioSegmentName
                        ),
                        MessageType.AUDIO
                );
            }
        }
        ////////////////////////////

        return stateUnit.getCurState();
    }
    ////////////////////////////////////////////////////////////

}
