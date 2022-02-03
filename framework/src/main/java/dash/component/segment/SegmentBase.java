package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.segment.definition.InitializationSegment;

public class SegmentBase {

    ////////////////////////////////////////////////////////////
    private final InitializationSegment initializationSegment;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentBase(InitializationSegment initializationSegment) {
        this.initializationSegment = initializationSegment;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public InitializationSegment getInitializationSegment() {
        return initializationSegment;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
