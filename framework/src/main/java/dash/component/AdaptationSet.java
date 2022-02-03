package dash.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.component.definition.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adaptation Set: Represents a set of interchangeable encoded versions of one or several media content components.
 * For example,
 *          there may be an Adaptation Set for video,
 *          one for primary audio,
 *          one for secondary audio,
 *          one for captions.
 * Adaptation Sets may also be multiplexed,
 *          in which case,
 *          interchangeable versions of the multiplex may be described as a single Adaptation Set.
 *          For example,
 *                  an Adaptation Set may contain both video and main audio for a Period.
 *
 * <AdaptationSet mimeType="video/mp4" codecs="avc1.640828" frameRate="30000/1001" segmentAlignment="true" startWithSAP="1">
 *      <BaseURL>video_1/</BaseURL>
 *      <SegmentTemplate timescale="90000" initialization="$Bandwidth%/init.mp4v" media="$Bandwidth$/$Number%05d$.mp4v"/>
 *      <Representation id="v0" width="320" height="240" bandwidth="250000"/>
 *      <Representation id="v1" width="640" height="480" bandwidth="500000"/>
 *      <Representation id="v2" width="960" height="720" bandwidth="1000000"/>
 * </AdaptationSet>
 *
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
 *
 */
public class AdaptationSet {

    ////////////////////////////////////////////////////////////
    private final String index;

    private String baseUrl;
    private String mimeType;
    private boolean isBitstreamSwitching;
    private MediaType mediaType;

    private final Map<String, Representation> representationHashMap = new HashMap<>();
    ////////////////////////////////////////////////////////////

    public AdaptationSet(String index) {
        this.index = index;
    }

    ////////////////////////////////////////////////////////////
    public String getIndex() {
        return index;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isBitstreamSwitching() {
        return isBitstreamSwitching;
    }

    public void setBitstreamSwitching(boolean bitstreamSwitching) {
        isBitstreamSwitching = bitstreamSwitching;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Map<String, Representation> getRepresentationHashMap() {
        return representationHashMap;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
