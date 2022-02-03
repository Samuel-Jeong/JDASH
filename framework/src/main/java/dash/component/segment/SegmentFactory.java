package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Segment: Content within a Representation may be further divided in time
 * into Segments of fixed or variable length.
 *
 * Each segment is referenced in the MPD by means of a URL.
 * Thus a Segment defines the largest data unit
 * that can be accessed by means of a single HTTP request.
 *
 * Segments contain encoded chunks of media components.
 * They may also include information on how to map the media segments
 * into the media presentation timeline for switching and
 * synchronous presentation with other Representations.
 */
public abstract class SegmentFactory {

    ////////////////////////////////////////////////////////////
    private final String index;
    private final String url;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentFactory(String index, String url) {
        this.index = index;
        this.url = url;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getIndex() {
        return index;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
