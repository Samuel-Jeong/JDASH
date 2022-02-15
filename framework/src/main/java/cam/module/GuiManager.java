package cam.module;

import cam.CameraFrame;
import cam.panel.*;
import java.awt.*;

public class GuiManager {

    private static GuiManager guiManager = null;

    private final VideoPanel videoPanel;
    private final VideoControlPanel videoControlPanel;
    private final ControlPanel controlPanel;
    private final PlaylistPanel playlistPanel;
    private final UriPanel uriPanel;

    private CameraFrame cameraFrame = null;
    private final TextEditor textEditor;

    private boolean isUploaded = false;

    ////////////////////////////////////////////////////////////////////////////////

    public GuiManager() {
        videoPanel = new VideoPanel();
        videoControlPanel = new VideoControlPanel();
        videoPanel.add(videoControlPanel, BorderLayout.SOUTH);
        controlPanel = new ControlPanel();
        playlistPanel = new PlaylistPanel();
        uriPanel = new UriPanel();
        textEditor = new TextEditor();
    }

    public static GuiManager getInstance() {
        if (guiManager == null) {
            guiManager = new GuiManager();
        }

        return guiManager;
    }

    public TextEditor getTextEditor() {
        return textEditor;
    }

    public boolean isUploaded() {
        return isUploaded;
    }

    public void setUploaded(boolean uploaded) {
        isUploaded = uploaded;
    }

    public VideoPanel getVideoPanel() {
        return videoPanel;
    }

    public VideoControlPanel getVideoControlPanel() {
        return videoControlPanel;
    }

    public ControlPanel getControlPanel() {
        return controlPanel;
    }

    public PlaylistPanel getPlaylistPanel() {
        return playlistPanel;
    }

    public UriPanel getUriPanel() {
        return uriPanel;
    }

    public void setCameraFrame(CameraFrame cameraFrame) {
        this.cameraFrame = cameraFrame;
    }

    public CameraFrame getCameraFrame() {
        return cameraFrame;
    }

    public String getSelectPlaylist() {
        return playlistPanel.getSelectedUri();
    }

    ////////////////////////////////////////////////////////////////////////////////

}
