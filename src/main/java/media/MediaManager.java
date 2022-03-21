package media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.client.DashClient;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.util.ArrayList;
import java.util.List;

public class MediaManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(MediaManager.class);

    private final String mediaBasePath;
    private final String mediaListFilePath;
    private final List<MediaInfo> mediaInfoList = new ArrayList<>();

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
        List<String> fileLines = FileManager.readAllLines(mediaListFilePath);
        ////////////////////////////////

        ////////////////////////////////
        // 2) APPLY BASE PATH IN FRONT OF THE RAW FILE PATH
        if (fileLines != null) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();

            // CLEAR ALL STATIC DASH UNIT
            ServiceManager.getInstance().getDashServer().deleteDashUnitsByType(StreamType.STATIC);
            mediaInfoList.clear();

            int mediaInfoListIndex = 0;
            for (String rawUri : fileLines) {
                if (rawUri == null || rawUri.isEmpty()) {
                    continue;
                }

                rawUri = rawUri.trim();
                // 앞에 '#' 이 있으면 주석으로 처리
                if (rawUri.startsWith("#")) { continue; }

                // ex) D,live/jamesj
                String[] elements = rawUri.split(",");
                if (elements.length != 2) { continue; }

                /**
                 * Stream Type
                 * S: Static stream
                 * D: Dynamic stream
                 */
                String streamTypeStr = elements[0];
                StreamType streamType = StreamType.NONE;
                if (streamTypeStr.equals("S")) {
                    streamType = StreamType.STATIC;
                } else if (streamTypeStr.equals("D")) {
                    streamType = StreamType.DYNAMIC;
                }

                String uri = elements[1];
                String fullPath = FileManager.concatFilePath(mediaBasePath, uri);

                // ADD MediaInfo
                addMediaInfo(mediaInfoListIndex, streamType, fullPath);

                // ADD STATIC DASH UNIT
                String dashPathExtension = FileUtils.getExtension(uri);
                if (dashPathExtension.length() != 0) {
                    if (!streamType.equals(StreamType.STATIC)) { continue; }
                    if (!uri.endsWith(".mp4") && !uri.endsWith(StreamConfigManager.DASH_POSTFIX)) { continue; }

                    DashUnit dashUnit = ServiceManager.getInstance().getDashServer().addDashUnit(
                            streamType,
                             AppInstance.getInstance().getConfigManager().getHttpListenIp() + ":" +
                                     FileManager.getFilePathWithoutExtensionFromUri(fullPath),
                            null, 0
                    );

                    if (dashUnit != null) {
                        dashUnit.setInputFilePath(fullPath);

                        String mpdPath = fullPath;
                        if (mpdPath.endsWith(".mp4")) {
                            mpdPath = mpdPath.replace(".mp4", StreamConfigManager.DASH_POSTFIX);
                        }
                        dashUnit.setOutputFilePath(mpdPath);

                        if (!configManager.isEnableClient() && configManager.isEnablePreloadWithDash()) {
                            // GET STATIC MEDIA SOURCE from remote dash server
                            String httpPath = "http://" + configManager.getHttpTargetIp() + ":" + configManager.getHttpTargetPort();
                            httpPath = FileManager.concatFilePath(httpPath, uri);
                            DashClient dashClient = new DashClient(
                                    dashUnit.getId(),
                                    ServiceManager.getInstance().getDashServer().getBaseEnvironment(),
                                    httpPath,
                                    FileManager.concatFilePath(
                                            FileManager.getParentPathFromUri(mpdPath),
                                            FileManager.getFileNameFromUri(mpdPath)
                                    )
                            );
                            dashClient.start();
                            dashClient.sendHttpGetRequest(httpPath);
                            dashUnit.setDashClient(dashClient);
                        }
                    }
                }

                mediaInfoListIndex++;
            }
        }
        ////////////////////////////////

        return !mediaInfoList.isEmpty();
    }

    public void addMediaInfo(int index, StreamType streamType, String uri) {
        if (index < 0 || uri == null) {
            logger.warn("[MediaManager] Fail to add the uri. (index={}, uri={})", index, uri);
            return;
        }

        mediaInfoList.add(
                index,
                new MediaInfo(
                        streamType,
                        uri
                )
        );
    }

    public MediaInfo getMediaInfo(int index) {
        return mediaInfoList.get(index);
    }

    public List<MediaInfo> getMediaInfoList() {
        return mediaInfoList;
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
