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
import util.module.FileManager;

import java.util.List;

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

        int representationId = 0;

        if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
            List<Representation> representations = dashClient.getMpdManager().getRepresentations(MpdManager.CONTENT_VIDEO_TYPE);
            if (representations != null && !representations.isEmpty()) {
                // VIDEO INIT SEGMENT
                // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
                String videoInitSegmentName = dashClient.getMpdManager().getRawInitializationSegmentName(representations.get(0));
                videoInitSegmentName = videoInitSegmentName.replace(
                        AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                        representationId + ""
                );
                String targetVideoInitSegPath = FileManager.concatFilePath(
                        dashClient.getTargetBasePath(),
                        // outdoor_market_ambiance_Dolby_init1.m4s
                        videoInitSegmentName
                );
                dashClient.setTargetVideoInitSegPath(targetVideoInitSegPath);
                if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
                    dashClient.sendHttpGetRequest(
                            FileManager.concatFilePath(
                                    dashClient.getSrcBasePath(),
                                    videoInitSegmentName
                            ),
                            MessageType.VIDEO
                    );
                }

                representationId++;
            }
        }

        List<Representation> representations = dashClient.getMpdManager().getRepresentations(MpdManager.CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // AUDIO INIT SEGMENT
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String audioInitSegmentName = dashClient.getMpdManager().getRawInitializationSegmentName(representations.get(0));
            audioInitSegmentName = audioInitSegmentName.replace(
                    AppInstance.getInstance().getConfigManager().getRepresentationIdFormat(),
                    representationId + ""
            );
            String targetAudioInitSegPath = FileManager.concatFilePath(
                    dashClient.getTargetBasePath(),
                    // outdoor_market_ambiance_Dolby_init0.m4s
                    audioInitSegmentName
            );
            dashClient.setTargetAudioInitSegPath(targetAudioInitSegPath);
            dashClient.sendHttpGetRequest(
                    FileManager.concatFilePath(
                            dashClient.getSrcBasePath(),
                            audioInitSegmentName
                    ),
                    MessageType.AUDIO
            );
        } else {
            logger.warn("[DashClientMpdDoneCallBack] Fail to send http get request for init segment. Representation is not exists. (dashClient={})", dashClient);
        }
        ////////////////////////////

        return stateUnit.getCurState();
    }


}
