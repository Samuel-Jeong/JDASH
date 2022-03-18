package dash.client.fsm;

import dash.client.DashClient;
import dash.client.fsm.callback.DashClientGetInitSegCallBack;
import dash.client.fsm.callback.DashClientGetMpdCallBack;
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
        DashClientGetMpdCallBack dashClientGetMpdCallBack = new DashClientGetMpdCallBack(stateManager, DashClientEvent.GET_MPD);
        dashClientStateHandler.addState(
                DashClientEvent.GET_MPD,
                DashClientState.IDLE, DashClientState.MPD_DONE,
                dashClientGetMpdCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_INIT_SEG
        // MPD_DONE > INIT_SEG_DONE
        DashClientGetInitSegCallBack dashClientGetInitSegCallBack = new DashClientGetInitSegCallBack(stateManager, DashClientEvent.GET_INIT_SEG);
        dashClientStateHandler.addState(
                DashClientEvent.GET_INIT_SEG,
                DashClientState.MPD_DONE, DashClientState.INIT_SEG_DONE,
                dashClientGetInitSegCallBack,
                null,
                null, 0, 0
        );

        // EVENT : GET_MEDIA_SEG
        // INIT_SEG_DONE > MEDIA_SEG_DONE
        dashClientStateHandler.addState(
                DashClientEvent.GET_MEDIA_SEG,
                DashClientState.INIT_SEG_DONE, DashClientState.MEDIA_SEG_DONE,
                null,
                null,
                null, 0, 0
        );

        // EVENT : IDLE
        // MPD_DONE, INIT_SEG_DONE, MEDIA_SEG_DONE > IDLE
        HashSet<String> idlePrevStateSet = new HashSet<>(
                Arrays.asList(
                        DashClientState.MPD_DONE, DashClientState.INIT_SEG_DONE, DashClientState.MEDIA_SEG_DONE
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
