package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.segment.definition.InitializationSegment;

import java.util.HashMap;
import java.util.Map;

/**
 * The SegmentList contains a list of SegmentURL elements
 *      which should be played back by the client in the order at which they occur in the MPD.
 *
 * A SegmentURL element contains a URL to a segment and possibly a byte range.
 *
 * Additionally, an index segment could occur at the beginning of the SegmentList.
 */

public class SegmentList {

    ////////////////////////////////////////////////////////////
    private String timeScale;
    private String duration;

    private InitializationSegment initializationSegment = null;
    private RepresentationIndex representationIndex = null;

    private Map<String, SegmentFactory> segmentFactoryMap;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentList() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getTimeScale() {
        return timeScale;
    }

    public void setTimeScale(String timeScale) {
        this.timeScale = timeScale;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
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

    public Map<String, SegmentFactory> getSegmentFactoryMap() {
        return segmentFactoryMap;
    }

    public void setSegmentFactoryMap(Map<String, SegmentFactory> segmentFactoryMap) {
        this.segmentFactoryMap = segmentFactoryMap;
    }

    public void addSegmentLast(SegmentFactory segmentFactory) {
        if (segmentFactoryMap == null) {
            segmentFactoryMap = new HashMap<>();
        }

        String index = segmentFactory.getIndex();
        if (getSegmentByIndex(index) != null) {
            return;
        }

        segmentFactoryMap.putIfAbsent(index, segmentFactory);
    }

    public boolean removeSegmentByIndex(String index) {
        if (segmentFactoryMap == null) {
            return false;
        }

        return segmentFactoryMap.remove(index) != null;
    }

    public SegmentFactory getSegmentByIndex(String index) {
        if (segmentFactoryMap == null) {
            return null;
        }

        return segmentFactoryMap.get(index);
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
