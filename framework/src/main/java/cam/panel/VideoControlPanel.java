package cam.panel;

import cam.module.GuiManager;
import cam.listener.PauseButtonListener;
import cam.listener.PlayButtonListener;
import cam.listener.StopButtonListener;
import cam.listener.VolumeButtonListener;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import service.AppInstance;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class VideoControlPanel extends JPanel {

    private final String iconRootPath;
    private final ImageIcon playIcon;
    private final ImageIcon pauseIcon;
    private final ImageIcon stopIcon;
    private final ImageIcon volumeIcon;
    private final ImageIcon muteIcon;

    private final JButton playButton;
    private final JButton pauseButton;
    private final JButton stopButton;

    private final ProgressBar videoProgressBar = new ProgressBar();
    private final JLabel videoStatus = new JLabel();

    private final JButton volumeButton;
    private final Slider volumeSlider = new Slider();

    private boolean isMute = false;
    private double currentVolume = 100.0;

    public VideoControlPanel() {
        this.setLayout(new GridBagLayout());

        String curIconRootPath = AppInstance.getInstance().getConfigManager().getIconRootPath();
        if (!curIconRootPath.startsWith("/")) {
            String curUserDir = System.getProperty("user.dir");
            curIconRootPath = curUserDir + File.separator + curIconRootPath;
        }
        if (!curIconRootPath.endsWith(File.separator)) {
            curIconRootPath += File.separator;
        }

        iconRootPath = curIconRootPath;

        playIcon = new ImageIcon(iconRootPath + "playButton.png");
        playButton = new JButton(playIcon);

        pauseIcon = new ImageIcon(iconRootPath + "pauseButton.png");
        pauseButton = new JButton(pauseIcon);

        stopIcon = new ImageIcon(iconRootPath + "stopButton.png");
        stopButton = new JButton(stopIcon);

        volumeIcon = new ImageIcon(iconRootPath + "volumeButton.png");
        volumeButton = new JButton(volumeIcon);

        muteIcon = new ImageIcon(iconRootPath + "muteButton.png");

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weighty=0.1;
        gridBagConstraints.gridy=0;

        resizeImageIcon(20, 20);
        initButton(gridBagConstraints);
    }

    private void resizeImageIcon(int width, int height) {
        playIcon.setImage(playIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
        pauseIcon.setImage(pauseIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
        stopIcon.setImage(stopIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
        volumeIcon.setImage(volumeIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
        muteIcon.setImage(muteIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    private void initButton(GridBagConstraints gridBagConstraints) {
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        int index = 0;

        gridBagConstraints.weightx=0.1;
        gridBagConstraints.gridx=index++;
        this.add(playButton, gridBagConstraints);

        gridBagConstraints.weightx=0.1;
        gridBagConstraints.gridx=index++;
        this.add(pauseButton, gridBagConstraints);

        gridBagConstraints.weightx=0.1;
        gridBagConstraints.gridx=index++;
        this.add(stopButton, gridBagConstraints);

        index = initProgressBar(gridBagConstraints, index);

        gridBagConstraints.weightx=0.1;
        gridBagConstraints.gridx=index++;
        this.add(volumeButton, gridBagConstraints);

        initSlider(gridBagConstraints, index);


        playButton.addActionListener(new PlayButtonListener());
        pauseButton.addActionListener(new PauseButtonListener());
        stopButton.addActionListener(new StopButtonListener());
        volumeButton.addActionListener(new VolumeButtonListener());
    }

    private int initProgressBar(GridBagConstraints gridBagConstraints, int index) {

        final JFXPanel pFXPanel = new JFXPanel();
        Group root  =  new  Group();
        Scene scene  =  new  Scene(root);
        videoProgressBar.prefWidthProperty().bind(root.getScene().widthProperty());
        videoProgressBar.prefHeightProperty().bind(root.getScene().heightProperty());
        videoProgressBar.setProgress(0.0);

        videoProgressBar.setOnMouseClicked(event -> {
            MediaPlayer videoPlayer = GuiManager.getInstance().getVideoPanel().getMediaPlayer();
            if (videoPlayer != null) {
                videoPlayer.seek(new Duration(videoPlayer.getTotalDuration().toMillis() * (event.getX() / videoProgressBar.getWidth())));
            }
        });

        root.getChildren().add(videoProgressBar);
        pFXPanel.setScene(scene);
        setVideoStatus(0, 0);

        gridBagConstraints.weightx=10.0;
        gridBagConstraints.gridx=index++;
        this.add(pFXPanel, gridBagConstraints);

        gridBagConstraints.weightx=0.1;
        gridBagConstraints.gridx=index++;
        this.add(videoStatus, gridBagConstraints);

        return index;
    }

    private void initSlider(GridBagConstraints gridBagConstraints, int index) {
        final JFXPanel pFXPanel = new JFXPanel();

        Group root  =  new  Group();
        Scene scene  =  new  Scene(root);
        volumeSlider.prefWidthProperty().bind(root.getScene().widthProperty());
        volumeSlider.prefHeightProperty().bind(root.getScene().heightProperty());
        volumeSlider.setValue(currentVolume);

        root.getChildren().add(volumeSlider);
        pFXPanel.setScene(scene);

        gridBagConstraints.weightx=1.0;
        gridBagConstraints.gridx=index;
        this.add(pFXPanel, gridBagConstraints);
    }

    public JButton getPlayButton() {
        return playButton;
    }

    public JButton getPauseButton() {
        return pauseButton;
    }

    public JButton getStopButton() {
        return stopButton;
    }

    public JButton getVolumeButton() {
        return volumeButton;
    }

    public void setVideoProgressBar(double progressValue) {
        Platform.runLater(() -> videoProgressBar.setProgress(progressValue));
    }

    public void setVideoStatus(int curTime, int totalTime) {
        videoStatus.setText(" " + intToTimeString(curTime) + "/" + intToTimeString(totalTime));
    }

    public Slider getVolumeSlider() {
        return volumeSlider;
    }

    public void setVolumeSlider() {

        currentVolume = volumeSlider.getValue();

        volumeSlider.valueProperty().addListener(observable -> {
            MediaPlayer mediaPlayer = GuiManager.getInstance().getVideoPanel().getMediaPlayer();
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volumeSlider.getValue() / 100);
            }

            if (volumeSlider.getValue() == 0.0 && !isMute()) {
                setMute(true);
            } else if (isMute()) {
                setMute(false);
            }

        });
    }

    public String getIconRootPath() {
        return iconRootPath;
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
        volumeButton.setIcon(isMute() ? muteIcon : volumeIcon);
    }

    public double getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume(double currentVolume) {
        this.currentVolume = currentVolume;
    }

    private String intToTimeString(int time) {
        return String.format("%02d:%02d:%02d",  time / 3600, time / 60, time % 60);
    }
}
