package media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import util.module.FileManager;

import java.util.ArrayList;
import java.util.List;

public class MediaManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MediaManager.class);

    private final String mediaBasePath;
    private final String mediaListFilePath;
    private List<String> uriList = null;

    ////////////////////////////////////////////////////////////
    public MediaManager(String mediaListFilePath) {
        this.mediaListFilePath = mediaListFilePath;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        this.mediaBasePath = configManager.getMediaBasePath();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean loadUriList() {
        ////////////////////////////////
        // 1) GET RAW FILE LIST
        uriList = FileManager.readAllLines(mediaListFilePath);
        ////////////////////////////////

        ////////////////////////////////
        // 2) APPLY BASE PATH IN FRONT OF THE RAW FILE PATH
        if (uriList != null) {
            // CLEAR ALL STATIC DASH UNIT
            ServiceManager.getInstance().getDashManager().deleteDashUnitsByType(StreamType.STATIC);

            List<String> newUriList = new ArrayList<>();
            for (String rawUri : uriList) {
                if (rawUri == null || rawUri.isEmpty()) {
                    continue;
                }

                rawUri = rawUri.trim();
                String fullPath = FileManager.concatFilePath(mediaBasePath, rawUri);
                newUriList.add(fullPath);

                // ADD STATIC DASH UNIT
                String dashPathExtension = FileUtils.getExtension(rawUri);
                if (dashPathExtension.length() != 0) {
                    if (!rawUri.endsWith(".mp4") && !rawUri.endsWith(".mpd")) { continue; }

                    logger.debug("@@@ rawUri: {}", rawUri);
                    DashUnit dashUnit = ServiceManager.getInstance().getDashManager().addDashUnit(
                            StreamType.STATIC,
                             AppInstance.getInstance().getConfigManager().getHttpListenIp() + ":" +
                                     FileManager.getFilePathWithoutExtensionFromUri(fullPath),
                            null
                    );

                    if (dashUnit != null) {
                        dashUnit.setInputFilePath(fullPath);

                        String mpdPath = fullPath;
                        if (mpdPath.endsWith(".mp4")) {
                            mpdPath = mpdPath.replace(".mp4", ".mpd");
                        }
                        dashUnit.setOutputFilePath(mpdPath);
                        dashUnit.setLiveStreaming(false);
                    }
                }
            }
            uriList = newUriList;
        }
        ////////////////////////////////

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
