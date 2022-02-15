package cam;

import cam.module.GuiManager;
import cam.panel.MediaPanel;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class CameraFrame extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(CameraFrame.class);

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 600;

    private final String title;
    private final double gamma;
    private final CanvasFrame myCameraFrame;

    public CameraFrame(String title, double gamma) throws HeadlessException {
        this.title = title;
        this.gamma = gamma;
        this.myCameraFrame = new CanvasFrame(title, gamma);

        init();
    }

    public void init() {
        try {
            // 프레임 크기
            setSize(WIDTH, HEIGHT);
            // 화면 가운데 배치
            setLocationRelativeTo(null);
            // 닫을 때 메모리에서 제거되도록 설정
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            // layout 설정
            BorderLayout borderLayout = new BorderLayout();
            setLayout(borderLayout);
            // panel 설정
            GuiManager guiManager = GuiManager.getInstance();

            add(new MediaPanel(), BorderLayout.CENTER);
            add(guiManager.getUriPanel(), BorderLayout.SOUTH);

            // 보이게 설정
            setVisible(true);
        } catch (Exception e) {
            logger.warn("CameraFrame.init.Exception", e);
        }
    }

    public void streamToMyCameraFrame(Frame frame) {
        myCameraFrame.showImage(frame);
    }

    @Override
    public String getTitle() {
        return title;
    }

    public double getGamma() {
        return gamma;
    }

    public CanvasFrame getMyCameraFrame() {
        return myCameraFrame;
    }
}
