package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Segment list for Representation
 */
public class SegmentInformation {

    ////////////////////////////////////////////////////////////
    private SegmentBase segmentBase;
    private SegmentList segmentList;
    private SegmentTemplate segmentTemplate;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentInformation(SegmentBase segmentBase, SegmentList segmentList) {
        this.segmentBase = segmentBase;
        this.segmentList = segmentList;
    }

    public SegmentInformation(SegmentTemplate segmentTemplate) {
        this.segmentTemplate = segmentTemplate;
    }

    public SegmentInformation() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentBase getSegmentBase() {
        return segmentBase;
    }

    public void setSegmentBase(SegmentBase segmentBase) {
        this.segmentBase = segmentBase;
    }

    public SegmentList getSegmentList() {
        return segmentList;
    }

    public void setSegmentList(SegmentList segmentList) {
        this.segmentList = segmentList;
    }

    public SegmentTemplate getSegmentTemplate() {
        return segmentTemplate;
    }

    public void setSegmentTemplate(SegmentTemplate segmentTemplate) {
        this.segmentTemplate = segmentTemplate;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
