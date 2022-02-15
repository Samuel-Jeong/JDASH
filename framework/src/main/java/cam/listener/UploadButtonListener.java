package cam.listener;

import cam.module.GuiManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UploadButtonListener implements ActionListener {

    private final JFileChooser fileUploader = new JFileChooser();

    public UploadButtonListener() {
        FileNameExtensionFilter fileNameExtensionFilter = new FileNameExtensionFilter(
                "Choose a MP4 File",
                "mp4"
        );
        fileUploader.setFileFilter(fileNameExtensionFilter);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        int ret = fileUploader.showOpenDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(null, "파일을 선택하지 않았습니다.", "경고", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filePath = fileUploader.getSelectedFile().getPath();
        GuiManager.getInstance().getVideoPanel().initMediaPlayer(filePath);
        GuiManager.getInstance().setUploaded(true);
        GuiManager.getInstance().getControlPanel().applyUploadButtonStatus();
    }
}
