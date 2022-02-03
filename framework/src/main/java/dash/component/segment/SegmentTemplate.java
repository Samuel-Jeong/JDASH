package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * <SegmentTemplate timescale="90000" initialization="$Bandwidth%/init.mp4v" media="$Bandwidth$/$Number%05d$.mp4v"/>
 */
public class SegmentTemplate {

    ////////////////////////////////////////////////////////////
    private final String timeScale;
    private final String initialization;
    private final String media;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentTemplate(String timeScale, String initialization, String media) {
        this.timeScale = timeScale;
        this.initialization = initialization;
        this.media = media;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getTimeScale() {
        return timeScale;
    }

    public String getInitialization() {
        return initialization;
    }

    public String getMedia() {
        return media;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
