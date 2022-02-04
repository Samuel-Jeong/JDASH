package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The SegmentTemplate element provides a mechanism to construct a list of segments from a given template.
 *
 * This means that specific identifiers will be substituted by dynamic values to create a list of segments.
 *
 * This has several advantages,
 *      e.g., SegmentList based MPDs can become very large because each segment needs to be referenced individually,
 *              compared with SegmentTemplate,
 *              this list could be described by a few lines that indicate how to build a large list of segments.
 *
 * 1) Number based SegmentTemplate
 * <Representation mimeType="video/mp4"
 *                    frameRate="24"
 *                    bandwidth="1558322"
 *                    codecs="avc1.4d401f" width="1277" height="544">
 *   <SegmentTemplate media="http://cdn.bitmovin.net/bbb/video-1500/segment-$Number$.m4s"
 *                       initialization="http://cdn.bitmovin.net/bbb/video-1500/init.mp4"
 *                       startNumber="0"
 *                       timescale="24"
 *                       duration="48"/>
 * </Representation>
 *
 * 2) Time-Based SegmentTemplate
 * - specifying arbitrary segment durations
 * - specifying exact segment durations
 * - specifying discontinuities in the media timeline
 * - at least one sidx box shall be present
 * - all values of the SegmentTimeline shall describe accurate timing, equal to the information in the sidx box
 *
 * <Representation mimeType="video/mp4"
 *                    frameRate="24"
 *                    bandwidth="1558322"
 *                    codecs="avc1.4d401f" width="1277" height="544">
 *   <SegmentTemplate media="http://cdn.bitmovin.net/bbb/video-1500/segment-$Time$.m4s"
 *                       initialization="http://cdn.bitmovin.net/bbb/video-1500/init.mp4"
 *                       timescale="24">
 *     <SegmentTimeline>
 *       <S t="0" d="48" r="5"/>
 *     </SegmentTimeline>
 *   </SegmentTemplate>
 * </Representation>
 *
 * The resulting segment requests of the client would be as follows:
 *      > http://cdn.bitmovin.net/bbb/video-1500/init.mp4
 *      > http://cdn.bitmovin.net/bbb/video-1500/segment-0.m4s
 *      > http://cdn.bitmovin.net/bbb/video-1500/segment-48.m4s
 *      > http://cdn.bitmovin.net/bbb/video-1500/segment-96.m4s
 *
 */
public class SegmentTemplate {

    ////////////////////////////////////////////////////////////
    private String media;
    private String initialization;
    private String startNumber;
    private String timeScale;
    private String duration;

    private RepresentationIndex representationIndex = null;
    private SegmentTimeline segmentTimeline = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentTemplate() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getMedia() {
        return media;
    }

    public void setMedia(String media) {
        this.media = media;
    }

    public String getInitialization() {
        return initialization;
    }

    public void setInitialization(String initialization) {
        this.initialization = initialization;
    }

    public String getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(String startNumber) {
        this.startNumber = startNumber;
    }

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

    public RepresentationIndex getRepresentationIndex() {
        return representationIndex;
    }

    public void setRepresentationIndex(RepresentationIndex representationIndex) {
        this.representationIndex = representationIndex;
    }

    public SegmentTimeline getSegmentTimeline() {
        return segmentTimeline;
    }

    public void setSegmentTimeline(SegmentTimeline segmentTimeline) {
        this.segmentTimeline = segmentTimeline;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
