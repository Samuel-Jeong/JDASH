package dash.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Period: interval of the Media Presentation,
 * where a contiguous sequence of all Periods constitutes the Media Presentation.
 *
 * <Period start="PT0.00S" duration="PT1800S" " id="M1">
 *      <AssetIdentifier schemeIdUri="urn:org:example:asset-id:2013" value="md:cid:EIDR:10.5240%2f0EFB-02CD-126E-8092-1E49-W">
 *          <AdaptationSet mimeType="video/mp4" codecs="avc1.640828" frameRate="30000/1001" segmentAlignment="true" startWithSAP="1">
 *              <BaseURL>video_1/</BaseURL>
 *              <SegmentTemplate timescale="90000" initialization="$Bandwidth%/init.mp4v" media="$Bandwidth$/$Number%05d$.mp4v"/>
 *              <Representation id="v0" width="320" height="240" bandwidth="250000"/>
 *              <Representation id="v1" width="640" height="480" bandwidth="500000"/>
 *              <Representation id="v2" width="960" height="720" bandwidth="1000000"/>
 *      </AdaptationSet>
 * </Period>
 *
 *   <Period start="PT0S">
 *     <AdaptationSet bitstreamSwitching="true">
 *       <Representation id="0" codecs="avc1" mimeType="video/mp4" width="320" height="240" startWithSAP="1" bandwidth="45351">
 *         <SegmentBase>
 *           <Initialization sourceURL="bunny_15s_50kbit/bunny_50kbit_dash.mp4"/>
 *         </SegmentBase>
 *         <SegmentList duration="15">
 *           <SegmentURL media="bunny_15s_50kbit/bunny_15s1.m4s"/>
 *           <SegmentURL media="bunny_15s_50kbit/bunny_15s2.m4s"/>
 *           <!-- ... -->
 *           <SegmentURL media="bunny_15s_50kbit/bunny_15s39.m4s"/>
 *           <SegmentURL media="bunny_15s_50kbit/bunny_15s40.m4s"/>
 *         </SegmentList>
 *       </Representation>
 *       <!-- ... -->
 *     </AdaptationSet>
 *   </Period>
 *
 */
public class Period {

    ////////////////////////////////////////////////////////////
    private final String index;
    private String baseUrl;
    private long start = 0;
    private double duration = 0;
    private final Map<String, AdaptationSet> adaptationSetMap = new HashMap<>();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public Period(String index) {
        this.index = index;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getIndex() {
        return index;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public Map<String, AdaptationSet> getAdaptationSetMap() {
        return adaptationSetMap;
    }

    public void addLast(AdaptationSet adaptationSet) {
        String index = adaptationSet.getIndex();
        if (getByIndex(index) != null) {
            return;
        }

        adaptationSetMap.putIfAbsent(index, adaptationSet);
    }

    public boolean removeByIndex(String index) {
        return adaptationSetMap.remove(index) != null;
    }

    public AdaptationSet getByIndex(String index) {
        return adaptationSetMap.get(index);
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
