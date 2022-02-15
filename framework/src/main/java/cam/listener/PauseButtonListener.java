package cam.listener;

import cam.module.GuiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PauseButtonListener implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(PauseButtonListener.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        if (GuiManager.getInstance().isUploaded()) {
            GuiManager.getInstance().getVideoPanel().getMediaPlayer().pause();
            GuiManager.getInstance().getControlPanel().applyPauseButtonStatus();
        }
    }
}
