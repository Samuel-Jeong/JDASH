package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RepresentationIndex {

    ////////////////////////////////////////////////////////////
    private String sourceUrl = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public RepresentationIndex() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
