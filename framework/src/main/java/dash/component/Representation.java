package dash.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Representation: Describes a deliverable encoded version of
 *                  one or several media content components.
 *
 * Any single Representation within an Adaptation Set
 *          should be sufficient to render the contained media content components.
 *
 * Clients may switch from Representation to Representation
 *          within an Adaptation Set in order to adapt to network conditions or other factors.
 *
 *
 * <Representation id="v0" width="320" height="240" bandwidth="250000"/>
 *
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
 *
 */
public class Representation {

    ////////////////////////////////////////////////////////////
    private final String index;
    private String codecs;
    private String mimeType;
    private String startWithSAP;
    private String bandwidth;
    private String audioSamplingRate;
    private String width;
    private String height;
    private SegmentInformation segmentInformation = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public Representation(String index) {
        this.index = index;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getIndex() {
        return index;
    }

    public String getCodecs() {
        return codecs;
    }

    public void setCodecs(String codecs) {
        this.codecs = codecs;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getStartWithSAP() {
        return startWithSAP;
    }

    public void setStartWithSAP(String startWithSAP) {
        this.startWithSAP = startWithSAP;
    }

    public String getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(String bandwidth) {
        this.bandwidth = bandwidth;
    }

    public String getAudioSamplingRate() {
        return audioSamplingRate;
    }

    public void setAudioSamplingRate(String audioSamplingRate) {
        this.audioSamplingRate = audioSamplingRate;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public SegmentInformation getSegmentInformation() {
        return segmentInformation;
    }

    public void setSegmentInformation(SegmentInformation segmentInformation) {
        this.segmentInformation = segmentInformation;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////


}
