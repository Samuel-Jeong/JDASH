package cam.module;

import cam.util.FileStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class PlaylistFileManager {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistFileManager.class);

    private FileStream playlistFile = null;

    ////////////////////////////////////////////////////////////////////////////////

    public PlaylistFileManager() {
        // nothing
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void createPlaylistFile(String recentPlaylistFilePath) {
        if (recentPlaylistFilePath == null || recentPlaylistFilePath.length() == 0) { return; }

        try {
            playlistFile = new FileStream(recentPlaylistFilePath, 0);
            playlistFile.createFile(false);
        } catch (Exception e) {
            logger.warn("({}) Fail to create the recent playlist file. (path={})", recentPlaylistFilePath, e);
        }
    }

    public boolean openPlaylistFile() {
        if (playlistFile == null) {
            return false;
        }

        return playlistFile.openFileStream(playlistFile.getFile(), false);
    }

    public boolean closePlaylistFile() {
        if (playlistFile == null) {
            return false;
        }

        return playlistFile.closeFileStream();
    }

    public void removePlaylistFile() {
        if (playlistFile == null) {
            return;
        }

        playlistFile.removeFile();
        playlistFile = null;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public boolean writeUriToPlaylistFile(byte[] data) {
        if (playlistFile == null) {
            logger.warn("Fail to write the data. Playlist file is not defined.");
            return false;
        }

        if (data == null || data.length == 0) {
            logger.warn("Fail to write the data. Data is null.");
            return false;
        }

        try {
            return playlistFile.writeFileStream(data);
        } catch (Exception e) {
            logger.warn("({}) Fail to write the recent playlist file. (path={})", playlistFile.getFilePath(), e);
            return false;
        }
    }

    public List<String> readUriFromPlaylistFile() {
        if (playlistFile == null) { return Collections.emptyList(); }

        return playlistFile.readFileStreamToLine();
    }

    ////////////////////////////////////////////////////////////////////////////////

    public String getPlaylistFilePath() {
        return playlistFile.getFilePath();
    }

}
