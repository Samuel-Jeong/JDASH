package stream;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import util.module.ConcurrentCyclicFIFO;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CameraCanvasController extends Job {

    private static final Logger logger = LoggerFactory.getLogger(CameraCanvasController.class);

    private final ConcurrentCyclicFIFO<Frame> frameQueue;
    private final CanvasFrame cameraFrame;
    private final boolean isLocal;

    private final OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();
    private final Point point = new Point(15, 45);
    private final Scalar scalar = new Scalar(0, 200, 255, 0);
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CameraCanvasController(ScheduleManager scheduleManager, String name,
                                       int initialDelay, int interval, TimeUnit timeUnit,
                                       int priority, int totalRunCount, boolean isLasted,
                                       boolean isLocal, ConcurrentCyclicFIFO<Frame> frameQueue, double gamma) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.frameQueue = frameQueue;
        this.isLocal = isLocal;
        this.cameraFrame = new CanvasFrame("[" + (isLocal? "LOCAL" : "REMOTE") + "] STREAM", CanvasFrame.getDefaultGamma() / gamma);

        logger.debug("[{}] [CameraCanvasController] is initiated.", (isLocal? "LOCAL" : "REMOTE"));
    }

    @Override
    public void run() {

        try {
            Frame frame = frameQueue.poll();
            if (frame == null) { return; }

            if (cameraFrame.isVisible()) {
                Date curDate = new Date();

                Mat mat = openCVConverter.convertToMat(frame);
                if (mat != null) {
                    curDate.setTime(System.currentTimeMillis());
                    opencv_imgproc.putText(mat, simpleDateFormat.format(curDate), point, opencv_imgproc.CV_FONT_VECTOR0, 0.8, scalar, 1, 0, false);
                    frame = openCVConverter.convert(mat);
                    if (frame == null) { return; }
                }
                cameraFrame.showImage(frame);
            }
        } catch (Exception e) {
            logger.warn("[{}] [CameraCanvasController] run.Exception", (isLocal? "LOCAL" : "REMOTE"), e);
        }
    }
}
