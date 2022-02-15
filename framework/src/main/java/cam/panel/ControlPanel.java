package cam.panel;

import cam.module.GuiManager;
import cam.base.ButtonType;
import cam.listener.*;

import javax.swing.*;
import java.awt.*;

public class ControlPanel extends JPanel {

    private final JButton registerButton = new JButton(ButtonType.REGISTER);
    private final JButton configButton = new JButton(ButtonType.CONFIG);
    //private final JButton playButton = new JButton(ButtonType.PLAY);
    //private final JButton pauseButton = new JButton(ButtonType.PAUSE);
    //private final JButton stopButton = new JButton(ButtonType.STOP);
    private final JButton unregisterButton = new JButton(ButtonType.UNREGISTER);
    private final JButton finishButton = new JButton(ButtonType.FINISH);
    private final JButton uploadButton = new JButton(ButtonType.UPLOAD);
    private final JButton clearButton = new JButton(ButtonType.CLEAR);

    public ControlPanel() {
        GridLayout gridLayout = new GridLayout(4, 2);
        gridLayout.setVgap(10);
        gridLayout.setHgap(5);
        setLayout(gridLayout);
    }

    public void initButton() {
        registerButton.addActionListener(new RegisterButtonListener());
        configButton.addActionListener(new ConfigButtonListener());
        //playButton.addActionListener(new PlayButtonListener());
        //pauseButton.addActionListener(new PauseButtonListener());
        //stopButton.addActionListener(new StopButtonListener());
        unregisterButton.addActionListener(new UnregisterButtonListener());
        finishButton.addActionListener(new FinishButtonListener());
        uploadButton.addActionListener(new UploadButtonListener());
        clearButton.addActionListener(new ClearButtonListener());

        initButtonStatus();

        this.add(registerButton);
        this.add(configButton);
        //this.add(playButton);
        //this.add(pauseButton);
        //this.add(stopButton);
        this.add(unregisterButton);
        this.add(finishButton);
        this.add(uploadButton);
        this.add(clearButton);
    }

    public void initButtonStatus() {
        registerButton.setEnabled(true);
        //playButton.setEnabled(false);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(false);
        unregisterButton.setEnabled(false);
        finishButton.setEnabled(true);
        uploadButton.setEnabled(true);
        clearButton.setEnabled(false);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(false);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(false);
        videoControlPanel.setVideoProgressBar(0.0);
        videoControlPanel.setVideoStatus(0, 0);
    }

    public void applyUploadButtonStatus() {
        registerButton.setEnabled(false);
        //playButton.setEnabled(true);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(false);
        finishButton.setEnabled(true);
        unregisterButton.setEnabled(false);
        uploadButton.setEnabled(false);
        clearButton.setEnabled(true);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(true);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(false);
    }

    public void applyClearButtonStatus() {
        registerButton.setEnabled(true);
        //playButton.setEnabled(false);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(false);
        finishButton.setEnabled(true);
        unregisterButton.setEnabled(false);
        uploadButton.setEnabled(true);
        clearButton.setEnabled(false);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(false);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(false);
        videoControlPanel.setVideoProgressBar(0.0);
        videoControlPanel.setVideoStatus(0, 0);
    }

    public void applyRegistrationButtonStatus() {
        registerButton.setEnabled(false);
        //playButton.setEnabled(true);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(false);
        finishButton.setEnabled(true);
        unregisterButton.setEnabled(true);
        uploadButton.setEnabled(false);
        clearButton.setEnabled(false);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(true);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(false);
    }

    public void applyPlayButtonStatus() {
        registerButton.setEnabled(false);
        //playButton.setEnabled(false);
        //pauseButton.setEnabled(true);
        //stopButton.setEnabled(true);
        finishButton.setEnabled(false);
        unregisterButton.setEnabled(false);
        uploadButton.setEnabled(false);
        clearButton.setEnabled(false);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(false);
        videoControlPanel.getPauseButton().setEnabled(true);
        videoControlPanel.getStopButton().setEnabled(true);
    }

    public void applyPauseButtonStatus() {
        registerButton.setEnabled(false);
        //playButton.setEnabled(true);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(true);
        finishButton.setEnabled(false);
        unregisterButton.setEnabled(!GuiManager.getInstance().isUploaded());
        uploadButton.setEnabled(false);
        clearButton.setEnabled(false);

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(true);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(true);
    }

    public void applyStopButtonStatus() {
        registerButton.setEnabled(false);
        //playButton.setEnabled(true);
        //pauseButton.setEnabled(false);
        //stopButton.setEnabled(false);
        finishButton.setEnabled(true);
        unregisterButton.setEnabled(!GuiManager.getInstance().isUploaded());
        uploadButton.setEnabled(false);
        clearButton.setEnabled(GuiManager.getInstance().isUploaded());

        VideoControlPanel videoControlPanel = GuiManager.getInstance().getVideoControlPanel();
        videoControlPanel.getPlayButton().setEnabled(true);
        videoControlPanel.getPauseButton().setEnabled(false);
        videoControlPanel.getStopButton().setEnabled(false);
    }
}
