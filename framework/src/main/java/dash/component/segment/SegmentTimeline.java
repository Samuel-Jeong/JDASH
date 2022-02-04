package dash.component.segment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SegmentTimeline {

    ////////////////////////////////////////////////////////////
    private String t = null;
    private String r = null;
    private String d = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public SegmentTimeline(String t, String r, String d) {
        this.t = t;
        this.r = r;
        this.d = d;
    }

    public SegmentTimeline() {}
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }

    public String getR() {
        return r;
    }

    public void setR(String r) {
        this.r = r;
    }

    public String getD() {
        return d;
    }

    public void setD(String d) {
        this.d = d;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
