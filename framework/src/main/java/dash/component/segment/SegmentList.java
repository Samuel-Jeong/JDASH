package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

public class SegmentList {

    ////////////////////////////////////////////////////////////
    private final double duration;
    private Map<String, SegmentFactory> segmentFactoryMap;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentList(double duration, Map<String, SegmentFactory> segmentFactoryList) {
        this.duration = duration;
        this.segmentFactoryMap = segmentFactoryList;
    }

    public SegmentList(double duration) {
        this.duration = duration;
        segmentFactoryMap = new HashMap<>();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public double getDuration() {
        return duration;
    }

    public Map<String, SegmentFactory> getSegmentFactoryMap() {
        return segmentFactoryMap;
    }

    public void setSegmentFactoryMap(Map<String, SegmentFactory> segmentFactoryMap) {
        this.segmentFactoryMap = segmentFactoryMap;
    }

    public void addLast(SegmentFactory segmentFactory) {
        if (segmentFactoryMap == null) {
            segmentFactoryMap = new HashMap<>();
        }

        String index = segmentFactory.getIndex();
        if (getByIndex(index) != null) {
            return;
        }

        segmentFactoryMap.putIfAbsent(index, segmentFactory);
    }

    public boolean removeByIndex(String index) {
        if (segmentFactoryMap == null) {
            return false;
        }

        return segmentFactoryMap.remove(index) != null;
    }

    public SegmentFactory getByIndex(String index) {
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
