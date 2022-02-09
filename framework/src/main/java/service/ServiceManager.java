package service;

import dash.DashManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.monitor.FileKeeper;
import service.monitor.HaHandler;
import service.monitor.LongSessionRemover;
import service.scheduler.schedule.ScheduleManager;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.*;

public class ServiceManager {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

    private static ServiceManager serviceManager = null;

    private final ScheduleManager scheduleManager = new ScheduleManager();
    public static final String MAIN_SCHEDULE_JOB = "MAIN";

    private final String tmpdir = System.getProperty("java.io.tmpdir");
    private final File lockFile = new File(tmpdir, System.getProperty("lock_file", "urtsp_server.lock"));
    private FileChannel fileChannel;
    private FileLock lock;
    private static final int DELAY = 1000;
    private boolean isQuit = false;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public ServiceManager() {
        Runtime.getRuntime().addShutdownHook(new ShutDownHookHandler("ShutDownHookHandler", Thread.currentThread()));

    }
    
    public static ServiceManager getInstance ( ) {
        if (serviceManager == null) {
            serviceManager = new ServiceManager();
        }

        return serviceManager;
    }
    ////////////////////////////////////////////////////////////////////////////////
    
    ////////////////////////////////////////////////////////////////////////////////
    private boolean start () {
        // System Lock
        systemLock();

        if (scheduleManager.initJob(MAIN_SCHEDULE_JOB, 10, 10 * 2)) {
            scheduleManager.startJob(MAIN_SCHEDULE_JOB,
                    new HaHandler(
                            scheduleManager,
                            HaHandler.class.getSimpleName(),
                            0, DELAY, TimeUnit.MILLISECONDS,
                            5, 0, true
                    )
            );

            scheduleManager.startJob(MAIN_SCHEDULE_JOB,
                    new LongSessionRemover(
                            scheduleManager,
                            LongSessionRemover.class.getSimpleName(),
                            0, DELAY, TimeUnit.MILLISECONDS,
                            3, 0, true
                    )
            );

            FileKeeper fileKeeper = new FileKeeper(
                    scheduleManager,
                    FileKeeper.class.getSimpleName(),
                    0, DELAY, TimeUnit.MILLISECONDS,
                    10, 0, true
            );
            if (fileKeeper.init()) {
                scheduleManager.startJob(MAIN_SCHEDULE_JOB, fileKeeper);
            }
        }

        DashManager dashManager = DashManager.getInstance();
        dashManager.start();

        logger.debug("| All services are opened.");
        return true;
    }

    public void stop () {
        DashManager.getInstance().stop();
        scheduleManager.stopAll(MAIN_SCHEDULE_JOB);

        // System Unlock
        systemUnLock();

        isQuit = true;
        logger.debug("| All services are closed.");
    }

    /**
     * @fn public void loop ()
     * @brief Main Service Loop
     */
    public void loop () {
        if (!start()) {
            logger.error("Fail to start the program.");
            return;
        }

        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        while (!isQuit) {
            try {
                timeUnit.sleep(DELAY);
            } catch (InterruptedException e) {
                logger.warn("| ServiceManager.loop.InterruptedException", e);
            }
        }
    }

    private void systemLock () {
        try {
            fileChannel = FileChannel.open(lockFile.toPath(), CREATE, READ, WRITE);
            lock = fileChannel.tryLock();
            if (lock == null) {
                logger.error("RTSP SERVER process is already running.");
                Thread.sleep(500L);
                System.exit(1);
            }
        } catch (Exception e) {
            logger.error("ServiceManager.systemLock.Exception.", e);
        }
    }

    private void systemUnLock () {
        try {
            if (lock != null) {
                lock.release();
            }

            if (fileChannel != null) {
                fileChannel.close();
            }

            Files.delete(lockFile.toPath());
        } catch (IOException e) {
            //ignore
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @class private static class ShutDownHookHandler extends Thread
     * @brief Graceful Shutdown 을 처리하는 클래스
     * Runtime.getRuntime().addShutdownHook(*) 에서 사용됨
     */
    private static class ShutDownHookHandler extends Thread {

        // shutdown 로직 후에 join 할 thread
        private final Thread target;

        public ShutDownHookHandler (String name, Thread target) {
            super(name);

            this.target = target;
            logger.debug("| ShutDownHookHandler is initiated. (target={})", target.getName());
        }

        /**
         * @fn public void run ()
         * @brief 정의된 Shutdown 로직을 수행하는 함수
         */
        @Override
        public void run ( ) {
            try {
                shutDown();
                target.join();
                logger.debug("| ShutDownHookHandler's target is finished successfully. (target={})", target.getName());
            } catch (Exception e) {
                logger.warn("| ShutDownHookHandler.run.Exception", e);
            }
        }

        /**
         * @fn private void shutDown ()
         * @brief Runtime 에서 선언된 Handler 에서 사용할 서비스 중지 함수
         */
        private void shutDown ( ) {
            logger.warn("| Process is about to quit. (Ctrl+C)");
            ServiceManager.getInstance().stop();
        }
    }


}
