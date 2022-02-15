package cam.listener;

import cam.module.GuiManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ClearButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        GuiManager.getInstance().getVideoPanel().getMediaPlayer().stop();
        GuiManager.getInstance().getVideoPanel().getMediaPlayer().dispose();
        GuiManager.getInstance().getVideoPanel().initMediaView();
        GuiManager.getInstance().getControlPanel().applyClearButtonStatus();
        GuiManager.getInstance().setUploaded(false);
    }
}
