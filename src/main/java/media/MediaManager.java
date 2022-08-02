package media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import instance.BaseEnvironment;
import network.definition.NetAddress;
import network.socket.SocketProtocol;
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

    private final BaseEnvironment baseEnvironment;
    private final String mediaBasePath;
    private final String mediaListFilePath;
    private final List<MediaInfo> mediaInfoList = new ArrayList<>();

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////
    public MediaManager(BaseEnvironment baseEnvironment, String mediaListFilePath) {
        this.baseEnvironment = baseEnvironment;
        this.mediaListFilePath = mediaListFilePath;
        this.mediaBasePath = configManager.getMediaBasePath();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean loadUriList() {
        // 1) GET RAW FILE LIST
        List<String> fileLines = fileManager.readAllLines(mediaListFilePath);

        // 2) APPLY BASE PATH IN FRONT OF THE RAW FILE PATH
        if (fileLines != null && !fileLines.isEmpty()) {
            mediaInfoList.clear();

            int mediaInfoListIndex = 0;
            for (String rawUri : fileLines) {
                String[] elements = parseRawUri(rawUri);
                if (elements.length == 0) {
                    continue;
                }

                // GET StreamType
                StreamType streamType = getStreamTypeFromString(elements[0]);

                // GET StreamUri
                String streamUri = elements[1]; // test/stream1.mpd
                String localStreamPath = getLocalStreamPath(streamType, streamUri);

                // ADD Static DashUnit only
                String dashPathExtension = FileUtils.getExtension(streamUri);
                if (dashPathExtension.length() != 0) {
                    if (!streamType.equals(StreamType.STATIC)) {
                        continue;
                    }
                    if (!streamUri.endsWith(StreamConfigManager.MP4_POSTFIX) && !streamUri.endsWith(StreamConfigManager.DASH_POSTFIX)) {
                        continue;
                    }
                    if (!startDownloadStream(streamType, streamUri, localStreamPath)) {
                        continue;
                    }
                }

                // ADD MediaInfo
                addMediaInfo(mediaInfoListIndex++, streamType, localStreamPath);
            }
        }

        return !mediaInfoList.isEmpty();
    }

    private String[] parseRawUri(String rawUri) {
        if (rawUri == null || rawUri.isEmpty()) { return new String[0]; }

        rawUri = rawUri.trim();
        // 앞에 '#' 이 있으면 주석으로 처리
        if (rawUri.startsWith("#")) { return new String[0]; }

        // ex) D,live/jamesj
        String[] elements = rawUri.split(",");
        if (elements.length != 2) { return new String[0]; }
        return elements;
    }

    private StreamType getStreamTypeFromString(String streamTypeString) {
        /**
         * Stream Type
         * S: Static stream
         * D: Dynamic stream
         */
        StreamType streamType = StreamType.NONE;
        if (streamTypeString.equals("S")) {
            streamType = StreamType.STATIC;
        } else if (streamTypeString.equals("D")) {
            streamType = StreamType.DYNAMIC;
        }
        return streamType;
    }

    private String getLocalStreamPath(StreamType streamType, String streamUri) {
        if (streamType == StreamType.DYNAMIC) {
            return fileManager.concatFilePath(mediaBasePath, streamUri);
        } else {
            String localUri = fileManager.concatFilePath(fileManager.getParentPathFromUri(streamUri), fileManager.getFileNameFromUri(streamUri)); // test/stream1
            localUri = fileManager.concatFilePath(localUri, fileManager.getFileNameWithExtensionFromUri(streamUri)); // test/stream1/stream1.mpd
            return fileManager.concatFilePath(mediaBasePath, localUri); // /home/udash/udash/media/test/stream1/stream1.mpd
        }
    }

    private String makeDashUnitId(String localStreamPath) {
        return AppInstance.getInstance().getConfigManager().getHttpListenIp() + ":" + fileManager.getFilePathWithoutExtensionFromUri(localStreamPath);
    }

    private boolean startDownloadStream(StreamType streamType, String streamUri, String localStreamPath) {
        String dashUnitId = makeDashUnitId(localStreamPath);
        DashUnit dashUnit = ServiceManager.getInstance().getDashServer().getDashUnitById(dashUnitId);
        if (dashUnit != null) {
            logger.debug("[MediaManager] DashUnit({}) is already exist. ({})", dashUnitId, dashUnit);
            return false;
        }
        dashUnit = ServiceManager.getInstance().getDashServer().addDashUnit(
                streamType, dashUnitId,
                null, 0, false
        );

        if (dashUnit != null) {
            String mpdPath = makeMpdPath(localStreamPath);
            dashUnit.setInputFilePath(localStreamPath);
            dashUnit.setOutputFilePath(mpdPath);
            dashUnit.setMpdParentPath(fileManager.getParentPathFromUri(mpdPath));

            if (!configManager.isEnableClient() && configManager.isEnablePreloadWithDash()) {
                startDashClient(mpdPath, streamUri, dashUnit);
            }
        }

        return true;
    }

    private String makeMpdPath(String localStreamPath) {
        if (localStreamPath.endsWith(StreamConfigManager.MP4_POSTFIX)) {
            localStreamPath = localStreamPath.replace(StreamConfigManager.MP4_POSTFIX, StreamConfigManager.DASH_POSTFIX);
        }
        return localStreamPath;
    }

    private String makeHttpPath(String streamUri) {
        String httpPath = StreamConfigManager.HTTP_PREFIX + configManager.getHttpTargetIp() + ":" + configManager.getHttpTargetPort();
        return fileManager.concatFilePath(httpPath, streamUri);
    }

    private void startDashClient(String mpdPath, String streamUri, DashUnit dashUnit) {
        String httpPath = makeHttpPath(streamUri);
        DashClient dashClient = new DashClient(
                dashUnit.getId(),
                mpdPath, httpPath, fileManager.getParentPathFromUri(mpdPath),
                new MpdManager(dashUnit.getId(), mpdPath)
        );

        // GET STATIC MEDIA SOURCE from remote dash server
        NetAddress targetAddress = new NetAddress(
                configManager.getHttpTargetIp(),
                configManager.getHttpTargetPort(),
                true, SocketProtocol.TCP
        );
        if (dashClient.start(baseEnvironment.getScheduleManager(), targetAddress)) {
            dashClient.sendHttpGetRequest(httpPath, MessageType.MPD);
            dashUnit.setDashClient(dashClient);
        }
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
