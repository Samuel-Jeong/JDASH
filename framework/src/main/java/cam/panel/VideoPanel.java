package cam.panel;

import cam.module.GuiManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class VideoPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MediaPanel.class);

    private final JFXPanel vFXPanel = new JFXPanel();
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;

    ////////////////////////////////////////////////////////////////////////////////

    public VideoPanel() {
        this.setLayout(new BorderLayout());
        this.add(vFXPanel, BorderLayout.CENTER);

        initMediaView();
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void initMediaView() {
        mediaView = new MediaView();

        // add video to stackPane
        StackPane root = new StackPane();
        root.getChildren().add(mediaView);
        final Scene scene = new Scene(root);

        // resize video based on screen size
        final DoubleProperty width = mediaView.fitWidthProperty();
        final DoubleProperty height = mediaView.fitHeightProperty();

        width.bind(Bindings.selectDouble(mediaView.sceneProperty(), "width"));
        height.bind(Bindings.selectDouble(mediaView.sceneProperty(), "height"));
        mediaView.setPreserveRatio(true);

        vFXPanel.setScene(scene);
    }

    public void initMediaPlayer(String path) {
        File videoFile = new File(path);
        if (videoFile.exists() && videoFile.isFile()) {
            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);

            GuiManager guiManager = GuiManager.getInstance();
            VideoControlPanel videoControlPanel = guiManager.getVideoControlPanel();

            mediaPlayer.setOnEndOfMedia(() -> {
                videoControlPanel.setVideoProgressBar(1.0);
                mediaPlayer.seek(new Duration(0));
                videoControlPanel.setVideoProgressBar(0.0);

                if (guiManager.isUploaded()) {
                    guiManager.getVideoPanel().getMediaPlayer().stop();
                    guiManager.getControlPanel().applyStopButtonStatus();
                    return;
                }
            });

            mediaPlayer.setOnReady(() -> {
                mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                    double curTime = mediaPlayer.getCurrentTime().toSeconds();
                    double totalTime = mediaPlayer.getTotalDuration().toSeconds();
                    double timeRate = curTime / totalTime;

                    videoControlPanel.setVideoProgressBar(timeRate);
                    videoControlPanel.setVideoStatus((int) curTime, (int) totalTime);
                });
            });
            videoControlPanel.setVolumeSlider();

            mediaView.setMediaPlayer(mediaPlayer);
            logger.debug("Success to init media player. (path={})", path);
        } else {
            logger.warn("Fail to init media player. Video file is not exist or not file. (path={})", path);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
}
