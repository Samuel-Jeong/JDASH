package cam;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

public class CameraManager extends Job {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(CameraManager.class);

    private CameraFrame cameraFrame;
    private static final int WEBCAM_DEVICE_INDEX = 0;
    public static final int CAPTURE_WIDTH = 600;
    public static final int CAPTURE_HEIGHT = 600;
    public static final int FRAME_RATE = 30;
    public static final int GOP_LENGTH_IN_FRAMES = 60;
    private static final int GAMMA = 1;

    private static OpenCVFrameGrabber grabber = null;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public CameraManager(ScheduleManager scheduleManager, String name, int initialDelay, int interval, TimeUnit timeUnit, int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        initMedia();
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        loop();
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void initMedia(){
        grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX);
        grabber.setImageWidth(CAPTURE_WIDTH);
        grabber.setImageHeight(CAPTURE_HEIGHT);

        cameraFrame = new CameraFrame();
    }

    public void loop() {
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        try {
            grabber.start();

            Frame capturedFrame;
            Java2DFrameConverter paintConverter = new Java2DFrameConverter();
            long startTime = System.currentTimeMillis();
            long curTime;
            long frame = 0;

            while ((capturedFrame = grabber.grab()) != null) {
                BufferedImage buff = paintConverter.getBufferedImage(capturedFrame, GAMMA);
                Graphics graphics = cameraFrame.getCanvas().getGraphics();
                graphics.drawImage(buff, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, 0, 0, buff.getWidth(), buff.getHeight(), null);
                frame++;

                curTime = System.currentTimeMillis();
                long waitMillis = (1000 * frame) / FRAME_RATE - (curTime - startTime);
                while (waitMillis <= 0) {
                    // If this error appeared, better to consider lower FRAME_RATE.
                    //logger.trace("[ERROR] grab image operation is too slow to encode, skip grab image at frame = [{}] (waitMillis=[{}])", frame, waitMillis);
                    frame++;
                    waitMillis = (1000 * frame) / FRAME_RATE - (curTime - startTime);
                }
                //logger.trace("frame [{}], curTime=[{}]ms, waitMillis=[{}]", frame, curTime, waitMillis);
                timeUnit.sleep(waitMillis);
            }
        } catch (Exception e) {
            logger.warn("CameraManager.start.Exception", e);
            stop();
        }
    }

    public void stop() {
        try {
            grabber.stop();
        } catch (Exception e) {
            logger.warn("CameraManager.stop.Exception", e);
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

}
