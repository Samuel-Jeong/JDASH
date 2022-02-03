package dash.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.segment.SegmentBase;
import dash.component.segment.SegmentList;

/**
 * Segment list for Representation
 */
public class SegmentInformation {

    ////////////////////////////////////////////////////////////
    private final SegmentBase segmentBase;
    private final SegmentList segmentList;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentInformation(SegmentBase segmentBase, SegmentList segmentList) {
        this.segmentBase = segmentBase;
        this.segmentList = segmentList;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentBase getSegmentBase() {
        return segmentBase;
    }

    public SegmentList getSegmentList() {
        return segmentList;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
