package cam.util;

import cam.module.PlaylistFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistManager {

    private static final Logger log = LoggerFactory.getLogger(PlaylistManager.class);
    public final String playlistFileName = "playlist.txt";

    private final String playlistFilePath;
    private final PlaylistFileManager playListFileManager = new PlaylistFileManager();
    private final HashMap<Integer, String> playlistMap;
    private final int playlistMaxSize;

    ////////////////////////////////////////////////////////////////////////////////

    public PlaylistManager(int playlistMaxSize) {
        String playlistRootPath = AppInstance.getInstance().getConfigManager().getPlaylistRootPath();
        if (!playlistRootPath.startsWith("/")) {
            String curUserDir = System.getProperty("user.dir");
            playlistRootPath = curUserDir + File.separator + playlistRootPath;
        }

        if (playlistRootPath.endsWith("/")) {
            playlistFilePath = playlistRootPath + playlistFileName;
        } else {
            playlistFilePath = playlistRootPath + File.separator + playlistFileName;
        }

        this.playlistMap = new HashMap<>();
        this.playlistMaxSize = playlistMaxSize;
        playListFileManager.createPlaylistFile(playlistFilePath);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public Map<Integer, String> startPlaylist() {
        List<String> playlist = playListFileManager.readUriFromPlaylistFile();
        if (playlist.isEmpty()) {
            log.warn("Fail to start playlistFileManager. Playlist is empty.");
            return playlistMap;
        }

        playlistMap.clear();
        int index = 0;
        for (String value : playlist) {
            if (index >= playlistMaxSize) {break;}
            playlistMap.put(index++, value);
        }

        return playlistMap;
    }

    public void stopPlaylist() {
        if (playListFileManager.openPlaylistFile()) {
            if (playlistMap.isEmpty()) {return;}
            log.debug("Success to open the playlist file. (path={})", playListFileManager.getPlaylistFilePath());

            StringBuilder stringBuilder = new StringBuilder();
            playlistMap.keySet().stream().sorted().forEach(key -> stringBuilder.append(playlistMap.get(key)).append("\n"));

            if (playListFileManager.writeUriToPlaylistFile(stringBuilder.toString().getBytes(StandardCharsets.UTF_8))) {
                log.debug("Success to apply the playlist. (content=\n{})", stringBuilder);
            }

            if (playListFileManager.closePlaylistFile()) {
                log.debug("Success to close the playlist file. (path={})", playListFileManager.getPlaylistFilePath());
            }
        } else {
            log.warn("Fail to open the playlist file. (path={})", playListFileManager.getPlaylistFilePath());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void addPlaylist(int index, String data){
        if (index < 0 || index >= playlistMaxSize) {
            log.warn("Fail to add the data. Index is wrong. (index={})", index);
            return;
        }

        if (data == null || data.length() == 0) {
            log.warn("Fail to add the data. Data is null.");
            return;
        }

        // 기존 리스트에 이미 존재하는 경우
        if (playlistMap.containsValue(data)) {
            if (playlistMap.size() <= index) {
                index = playlistMap.size() - 1;
            }

            int oldIndex = index;
            for (Map.Entry<Integer, String> entry : playlistMap.entrySet()) {
                if (entry.getValue().equals(data)) {
                    if (entry.getKey() == oldIndex) {
                        return;
                    }
                    oldIndex = entry.getKey();
                    break;
                }
            }

            // 아래에서 위로 옮기는 경우
            if (index < oldIndex) {
                for (int key = oldIndex; key > index; key--) {
                    playlistMap.replace(key, playlistMap.get(key - 1));
                }
            }
            // 위에서 아래로 옮기는 경우
            else if (index > oldIndex) {
                for (int key = oldIndex; key < index; key++) {
                    playlistMap.replace(key, playlistMap.get(key + 1));
                }
            }
            playlistMap.replace(index, data);
        }
        // 새로운 data 인 경우
        else {
            if (playlistMap.size() < index) {
                index = playlistMap.size();
            }

            if (playlistMap.size() == index) {
                playlistMap.put(index, data);
            } else {
                if (playlistMap.size() < playlistMaxSize) {
                    playlistMap.put(playlistMap.size(), playlistMap.get(playlistMap.size()));
                }
                for (int key = playlistMap.size() - 1; key > index; key--) {
                    playlistMap.replace(key, playlistMap.get(key - 1));
                }
                playlistMap.replace(index, data);
            }
        }
    }

    public void removePlaylist(String data) {
        if (data.length() == 0 || !playlistMap.containsValue(data)) {
            return;
        }

        int index = -1;
        for (Map.Entry<Integer, String> entry : playlistMap.entrySet()) {
            if (entry.getValue().equals(data)) {
                index = entry.getKey();
                break;
            }
        }

        for (int key = index; key < playlistMap.size(); key++) {
            if (key == playlistMap.size()-1) {
                playlistMap.remove(key);
            } else {
                playlistMap.replace(key, playlistMap.get(key + 1));
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getPlaylistFilePath() {
        return playlistFilePath;
    }

    public Map<Integer, String> getPlaylistMap() {
        return playlistMap;
    }

    public int getPlaylistMapSize() {
        return playlistMap.size();
    }
}
