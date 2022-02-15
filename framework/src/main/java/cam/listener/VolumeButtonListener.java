package cam.listener;

import cam.module.GuiManager;
import cam.panel.VideoControlPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VolumeButtonListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        if (videoControlPanel.isMute()) {
            videoControlPanel.getVolumeSlider().setValue(videoControlPanel.getCurrentVolume());
        } else {
            videoControlPanel.setCurrentVolume(videoControlPanel.getVolumeSlider().getValue());
            videoControlPanel.getVolumeSlider().setValue(0.0);
        }
    }
}
