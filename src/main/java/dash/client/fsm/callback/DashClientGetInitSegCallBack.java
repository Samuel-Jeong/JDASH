package dash.client.fsm.callback;

import dash.client.DashClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

public class DashClientGetInitSegCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetInitSegCallBack.class);

    public DashClientGetInitSegCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // GET MPD DONE > PARSE MPD & GET META DATA
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

        dashClient.sendHttpGetRequest(
                FileManager.concatFilePath(
                        dashClient.getSrcBasePath(),
                        dashClient.getMpdManager().getMediaSegmentName()
                )
        );
        ////////////////////////////

        return stateUnit.getCurState();
    }


}
