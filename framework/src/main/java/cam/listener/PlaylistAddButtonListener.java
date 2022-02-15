package cam.listener;

import cam.module.GuiManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PlaylistAddButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        GuiManager guiManager = GuiManager.getInstance();
        guiManager.getPlaylistPanel().addPlaylist(0, guiManager.getUriPanel().getUriTextField().getText());
    }
}
