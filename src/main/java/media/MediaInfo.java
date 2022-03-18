package media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.unit.StreamType;

public class MediaInfo {

    ////////////////////////////////////////////////////////////
    private final StreamType streamType;
    private final String uri;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MediaInfo(StreamType streamType, String uri) {
        this.streamType = streamType;
        this.uri = uri;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public StreamType getStreamType() {
        return streamType;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
