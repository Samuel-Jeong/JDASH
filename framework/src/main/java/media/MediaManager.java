package media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.module.FileManager;

import java.util.List;

public class MediaManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MediaManager.class);

    private final String mediaListFilePath;
    private List<String> uriList = null;

    ////////////////////////////////////////////////////////////
    public MediaManager(String mediaListFilePath) {
        this.mediaListFilePath = mediaListFilePath;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean loadUriList() {
        uriList = FileManager.readAllLines(mediaListFilePath);
        return uriList != null && !uriList.isEmpty();
    }

    public void addUri(int index, String uri) {
        if (index < 0 || uri == null) {
            logger.warn("[MediaManager] Fail to add the uri. (index={}, uri={})", index, uri);
            return;
        }

        uriList.add(index, uri);
    }

    public String getUri(int index) {
        return uriList.get(index);
    }

    public List<String> getUriList() {
        return uriList;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getMediaListFilePath() {
        return mediaListFilePath;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
