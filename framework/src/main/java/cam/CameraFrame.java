package cam;

import javax.swing.*;
import java.awt.*;

public class CameraFrame extends JFrame {

    private final JPanel canvas;

    public CameraFrame() throws HeadlessException {
        setTitle("Camera");
        setSize(CameraManager.CAPTURE_WIDTH, CameraManager.CAPTURE_HEIGHT);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        canvas = new JPanel();
        canvas.setBorder(BorderFactory.createEtchedBorder());
        add(canvas);

        setLocationRelativeTo(null);
        setVisible(true);
        setResizable(false);
    }

    public JPanel getCanvas() {
        return canvas;
    }
}
