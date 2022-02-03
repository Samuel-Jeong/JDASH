package dash.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.definition.MpdType;
import org.w3c.dom.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Media Presentation Description (MPD):
 * Describes the sequence of Periods that make up a DASH Media Presentation
 *
 * <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *      xmlns="urn:mpeg:dash:schema:mpd:2011"
 *      xsi:schemaLocation="urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd"
 *      type="dynamic"
 *      minimumUpdatePeriod="PT2S"
 *      timeShiftBufferDepth="PT600S"
 *      minBufferTime="PT2S"
 *      profiles="urn:mpeg:dash:profile:isoff-live:2011"
 *      availabilityStartTime="2012-12-25T15:17:50"
 *      mediaPresentationDuration="PT238806S">
 *
 *      <BaseURL>http://cdn1.example.com/</BaseURL>
 *      <BaseURL>http://cdn2.example.com/</BaseURL>
 *
 *      <Period start="PT0.00S" duration="PT1800S" " id="M1">
 *          ...
 *      </Period>
 *
 *      <Period start="PT300.00S6S" id="A1"
 *          xlink:href="https://adserv.com/avail.mpd?acq-timeime=00:10:0054054000&id=1234567&
 *          sc35cue=DAIAAAAAAAAAAAQAAZ_I0VniQAQAgBDVUVJQAAAAH+cAAAAAA=="
 *          xlink:actuate="onRequest">
 *          ...
 *      </Period>
 *      ...
 * </MPD>
 *
 * <?xml version="1.0" encoding="UTF-8"?>
 * <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *      xmlns="urn:mpeg:DASH:schema:MPD:2011"
 *      xsi:schemaLocation="urn:mpeg:DASH:schema:MPD:2011"
 *      profiles="urn:mpeg:dash:profile:isoff-main:2011"
 *      type="static"
 *      mediaPresentationDuration="PT0H9M56.46S"
 *      minBufferTime="PT15.0S">
 *
 *   <BaseURL>http://www-itec.uni-klu.ac.at/ftp/datasets/mmsys12/BigBuckBunny/bunny_15s/</BaseURL>
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
 *       <Representation id="1" codecs="avc1" mimeType="video/mp4" width="320" height="240" startWithSAP="1" bandwidth="88563">
 *         <SegmentBase>
 *           <Initialization sourceURL="bunny_15s_100kbit/bunny_100kbit_dash.mp4"/>
 *         </SegmentBase>
 *         <SegmentList duration="15">
 *           <SegmentURL media="bunny_15s_100kbit/bunny_15s1.m4s"/>
 *           <SegmentURL media="bunny_15s_100kbit/bunny_15s2.m4s"/>
 *           <!-- ... -->
 *           <SegmentURL media="bunny_15s_100kbit/bunny_15s39.m4s"/>
 *           <SegmentURL media="bunny_15s_100kbit/bunny_15s40.m4s"/>
 *         </SegmentList>
 *       </Representation>
 *       <!-- ... -->
 *     </AdaptationSet>
 *   </Period>
 * </MPD>
 *
 */
public class MPD {

    ////////////////////////////////////////////////////////////
    private String uriPrefix;
    private String uriPostfix;
    private Document manifest;

    private MpdType mpdType;
    private double minimumUpdatePeriod;
    private double timeShiftBufferDepth;
    private double minBufferTime;
    private String availabilityStartTime;
    private double mediaPresentationDuration;
    private List<String> baseUrlList;
    private final Map<String, Period> periodMap = new HashMap<>();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MPD() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MpdType getMpdType() {
        return mpdType;
    }

    public void setMpdType(MpdType mpdType) {
        this.mpdType = mpdType;
    }

    public double getMinimumUpdatePeriod() {
        return minimumUpdatePeriod;
    }

    public void setMinimumUpdatePeriod(double minimumUpdatePeriod) {
        this.minimumUpdatePeriod = minimumUpdatePeriod;
    }

    public double getTimeShiftBufferDepth() {
        return timeShiftBufferDepth;
    }

    public void setTimeShiftBufferDepth(double timeShiftBufferDepth) {
        this.timeShiftBufferDepth = timeShiftBufferDepth;
    }

    public double getMinBufferTime() {
        return minBufferTime;
    }

    public void setMinBufferTime(double minBufferTime) {
        this.minBufferTime = minBufferTime;
    }

    public String getAvailabilityStartTime() {
        return availabilityStartTime;
    }

    public void setAvailabilityStartTime(String availabilityStartTime) {
        this.availabilityStartTime = availabilityStartTime;
    }

    public double getMediaPresentationDuration() {
        return mediaPresentationDuration;
    }

    public void setMediaPresentationDuration(double mediaPresentationDuration) {
        this.mediaPresentationDuration = mediaPresentationDuration;
    }

    public List<String> getBaseUrlList() {
        return baseUrlList;
    }

    public void setBaseUrlList(List<String> baseUrlList) {
        this.baseUrlList = baseUrlList;
    }

    public Map<String, Period> getPeriodMap() {
        return periodMap;
    }

    public void addLast(Period period) {
        String index = period.getIndex();
        if (getByIndex(index) != null) {
            return;
        }

        periodMap.putIfAbsent(index, period);
    }

    public boolean removeByIndex(String index) {
        return periodMap.remove(index) != null;
    }

    public Period getByIndex(String index) {
        return periodMap.get(index);
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
