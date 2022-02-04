package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.segment.definition.InitializationSegment;

/**
 * SegmentBase is the most trivial way of referencing segments in the MPEG-DASH standard
 *      as it will be used when only one media segment is present per Representation,
 *      which will then be referenced through a URL in the BaseURL element.
 *
 * If a Representation should contain more segments,
 *      either SegmentList or SegmentTemplate must be used.
 */

public class SegmentBase {

    ////////////////////////////////////////////////////////////
    private String indexRange;
    private InitializationSegment initializationSegment;
    private RepresentationIndex representationIndex;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentBase() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getIndexRange() {
        return indexRange;
    }

    public void setIndexRange(String indexRange) {
        this.indexRange = indexRange;
    }

    public InitializationSegment getInitializationSegment() {
        return initializationSegment;
    }

    public void setInitializationSegment(InitializationSegment initializationSegment) {
        this.initializationSegment = initializationSegment;
    }

    public RepresentationIndex getRepresentationIndex() {
        return representationIndex;
    }

    public void setRepresentationIndex(RepresentationIndex representationIndex) {
        this.representationIndex = representationIndex;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
