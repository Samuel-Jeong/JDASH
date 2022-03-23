package dash.client.fsm;

import dash.client.DashClient;
import dash.client.fsm.callback.DashClientGetAudioInitSegCallBack;
import dash.client.fsm.callback.DashClientGetMpdAudioCallBack;
import dash.client.fsm.callback.DashClientGetMpdVideoCallBack;
import dash.client.fsm.callback.DashClientGetVideoInitSegCallBack;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @class public class DashClientFsmManager
 * @brief DashClientFsmManager class
 */
public class DashClientFsmManager {

    private final StateManager stateManager = new StateManager(2);

    ////////////////////////////////////////////////////////////////////////////////

    public DashClientFsmManager() {
        // Nothing
    }

    ////////////////////////////////////////////////////////////////////////////////

    public StateManager getStateManager() {
        return stateManager;
    }

    public void init(DashClient dashClient) {
        if (dashClient == null) {
            return;
        }

        stateManager.addStateHandler(DashClientState.NAME);
        StateHandler dashClientStateHandler = stateManager.getStateHandler(DashClientState.NAME);

        // EVENT : GET_MPD
        // IDLE > MPD_DONE
        DashClientGetMpdAudioCallBack dashClientGetMpdAudioCallBack = new DashClientGetMpdAudioCallBack(stateManager, DashClientEvent.GET_MPD_AUDIO);
        dashClientStateHandler.addState(
                DashClientEvent.GET_MPD_AUDIO,
                DashClientState.IDLE, DashClientState.MPD_DONE,
                dashClientGetMpdAudioCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_MPD
        // IDLE > MPD_DONE
        DashClientGetMpdVideoCallBack dashClientGetMpdVideoCallBack = new DashClientGetMpdVideoCallBack(stateManager, DashClientEvent.GET_MPD_VIDEO);
        dashClientStateHandler.addState(
                DashClientEvent.GET_MPD_VIDEO,
                DashClientState.IDLE, DashClientState.MPD_DONE,
                dashClientGetMpdVideoCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_AUDIO_INIT_SEG
        // MPD_DONE > INIT_SEG_DONE
        DashClientGetAudioInitSegCallBack dashClientGetAudioInitSegCallBack = new DashClientGetAudioInitSegCallBack(stateManager, DashClientEvent.GET_AUDIO_INIT_SEG);
        dashClientStateHandler.addState(
                DashClientEvent.GET_AUDIO_INIT_SEG,
                DashClientState.MPD_DONE, DashClientState.AUDIO_INIT_SEG_DONE,
                dashClientGetAudioInitSegCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_VIDEO_INIT_SEG
        // MPD_DONE > INIT_SEG_DONE
        DashClientGetVideoInitSegCallBack dashClientGetVideoInitSegCallBack = new DashClientGetVideoInitSegCallBack(stateManager, DashClientEvent.GET_VIDEO_INIT_SEG);
        dashClientStateHandler.addState(
                DashClientEvent.GET_VIDEO_INIT_SEG,
                DashClientState.MPD_DONE, DashClientState.VIDEO_INIT_SEG_DONE,
                dashClientGetVideoInitSegCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_AUDIO_MEDIA_SEG
        // INIT_SEG_DONE > MEDIA_SEG_DONE
        dashClientStateHandler.addState(
                DashClientEvent.GET_AUDIO_MEDIA_SEG,
                DashClientState.AUDIO_INIT_SEG_DONE, DashClientState.AUDIO_MEDIA_SEG_DONE,
                null,
                null,
                null, 0, 0
        );

        // EVENT : GET_VIDEO_MEDIA_SEG
        // INIT_SEG_DONE > MEDIA_SEG_DONE
        dashClientStateHandler.addState(
                DashClientEvent.GET_VIDEO_MEDIA_SEG,
                DashClientState.VIDEO_INIT_SEG_DONE, DashClientState.VIDEO_MEDIA_SEG_DONE,
                null,
                null,
                null, 0, 0
        );

        // EVENT : IDLE
        // MPD_DONE, INIT_SEG_DONE, MEDIA_SEG_DONE > IDLE
        HashSet<String> idlePrevStateSet = new HashSet<>(
                Arrays.asList(
                        DashClientState.MPD_DONE,
                        DashClientState.AUDIO_INIT_SEG_DONE, DashClientState.VIDEO_INIT_SEG_DONE,
                        DashClientState.AUDIO_MEDIA_SEG_DONE, DashClientState.VIDEO_MEDIA_SEG_DONE
                )
        );
        dashClientStateHandler.addState(
                DashClientEvent.IDLE,
                idlePrevStateSet, DashClientState.IDLE,
                null,
                null,
                null, 0, 0
        );
        //
    }

}
