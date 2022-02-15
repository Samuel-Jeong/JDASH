package cam.listener;

import cam.module.GuiManager;
import cam.panel.VideoControlPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StopButtonListener implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(StopButtonListener.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        if (GuiManager.getInstance().isUploaded()) {
            VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
            videoControlPanel.setVideoProgressBar(0.0);
            GuiManager.getInstance().getControlPanel().applyStopButtonStatus();
        }
    }
}
