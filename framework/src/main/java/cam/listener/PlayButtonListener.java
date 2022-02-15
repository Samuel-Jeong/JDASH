package cam.listener;

import cam.module.GuiManager;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PlayButtonListener implements ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(PlayButtonListener.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        if (GuiManager.getInstance().isUploaded()) {
            MediaPlayer mediaPlayer = GuiManager.getInstance().getVideoPanel().getMediaPlayer();
            if (mediaPlayer == null) {
                return;
            }

            mediaPlayer.play();
            GuiManager.getInstance().getControlPanel().applyPlayButtonStatus();
            return;
        }

        GuiManager.getInstance().getVideoControlPanel().setVideoProgressBar(-1.0);
    }
}
