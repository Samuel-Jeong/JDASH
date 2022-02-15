package cam.panel;

import cam.module.GuiManager;

import javax.swing.*;
import java.awt.*;

public class MediaControlPanel extends JPanel {

    public MediaControlPanel() {
        GridLayout gridLayout = new GridLayout(2, 1);
        gridLayout.setVgap(3);
        gridLayout.setHgap(3);
        setLayout(gridLayout);
        // panel 설정
        GuiManager guiManager = GuiManager.getInstance();
        add(guiManager.getControlPanel(), BorderLayout.CENTER);
        add(guiManager.getPlaylistPanel(), BorderLayout.SOUTH);

        guiManager.getControlPanel().initButton();
    }
}
