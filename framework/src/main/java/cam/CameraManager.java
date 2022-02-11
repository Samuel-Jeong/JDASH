package cam;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import org.junit.rules.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class CameraManager extends Job {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(CameraManager.class);

    private static final int WEBCAM_DEVICE_INDEX = 0;
    public static final int CAPTURE_WIDTH = 1280;
    public static final int CAPTURE_HEIGHT = 720;
    public static final int FRAME_RATE = 30;
    public static final int GOP_LENGTH_IN_FRAMES = 60;
    private static final int GAMMA = 1;
    private static final int SEGMENT_INTERVAL = 5000; // ms

    private final CameraFrame cameraFrame;
    private final OpenCVFrameGrabber grabber;
    private final Mp4Maker mp4Maker;
    private boolean alive = true;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public CameraManager(ScheduleManager scheduleManager, String name,
                         int initialDelay, int interval, TimeUnit timeUnit,
                         int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX);
        grabber.setImageWidth(CAPTURE_WIDTH);
        grabber.setImageHeight(CAPTURE_HEIGHT);
        cameraFrame = new CameraFrame();

        //////////////////////////////////////
        // SET MP4MAKER
        String mp4Path = "/Users/jamesj/GIT_PROJECTS/JDASH/framework/src/test/resources/mp4test/output" + System.currentTimeMillis() + ".mp4";
        mp4Maker = new Mp4Maker(mp4Path);
        if (mp4Maker.init()) {
            logger.debug("[CameraManager] Success to init the mp4 maker. ({})", mp4Path);
        }
        //////////////////////////////////////
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public void run() {
        loop();
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public void loop() {
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        try {
            grabber.start();

            Frame capturedFrame;
            Java2DFrameConverter paintConverter = new Java2DFrameConverter();
            long startTime = System.currentTimeMillis();
            long curTime;
            long prevTime = startTime;
            long frame = 0;

            while ((capturedFrame = grabber.grab()) != null) {
                if (alive) {
                    BufferedImage bufferedImage = paintConverter.getBufferedImage(capturedFrame, GAMMA);
                    mp4Maker.write(bufferedImage);

                    Graphics graphics = cameraFrame.getCanvas().getGraphics();
                    graphics.drawImage(bufferedImage, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null);
                    frame++;

                    curTime = System.currentTimeMillis();

                    // 5초마다 MP4 생성
                    long interval = curTime - prevTime;
                    if (interval >= SEGMENT_INTERVAL) {
                        prevTime = curTime;
                        mp4Maker.finish();
                        mp4Maker.init();
                        logger.debug("[CameraManager] [{}]ms", interval);
                    }

                    long waitMillis = (1000 * frame) / FRAME_RATE - (curTime - startTime);
                    while (waitMillis <= 0) {
                        // If this error appeared, better to consider lower FRAME_RATE.
                        //logger.trace("[ERROR] grab image operation is too slow to encode, skip grab image at frame = [{}] (waitMillis=[{}])", frame, waitMillis);
                        frame++;
                        waitMillis = (1000 * frame) / FRAME_RATE - (curTime - startTime);
                    }
                    //logger.trace("frame [{}], curTime=[{}]ms, waitMillis=[{}]", frame, curTime, waitMillis);
                    timeUnit.sleep(waitMillis);
                } else {
                    timeUnit.sleep(1000);
                }
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
