package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ContentComponent {

    ////////////////////////////////////////////////////////////
    private final int id;
    private final String contentType;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public ContentComponent(int id, String contentType) {
        this.id = id;
        this.contentType = contentType;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getContentType() {
        return contentType;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
