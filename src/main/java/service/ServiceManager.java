package service;

import config.ConfigManager;
import dash.server.DashServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.monitor.FileKeeper;
import service.monitor.HaHandler;
import service.monitor.LongSessionRemover;
import service.scheduler.job.Job;
import service.scheduler.job.JobBuilder;
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

    private final static ServiceManager serviceManager = new ServiceManager(); // lazy initialization

    private final ScheduleManager scheduleManager = new ScheduleManager();
    public static final String MAIN_SCHEDULE_JOB = "MAIN";
    public static final String LONG_SESSION_REMOVE_SCHEDULE_JOB = "LONG_SESSION_REMOVE_JOB";
    public static final int DELAY = 1000;

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    private final DashServer dashServer = new DashServer();

    private final String tmpdir = System.getProperty("java.io.tmpdir");
    private final File lockFile = new File(tmpdir, System.getProperty("lock_file", "jdash_server.lock"));
    private FileChannel fileChannel;
    private FileLock lock;
    private boolean isQuit = false;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private ServiceManager() {
        Runtime.getRuntime().addShutdownHook(new ShutDownHookHandler("ShutDownHookHandler", Thread.currentThread()));
    }
    
    public static ServiceManager getInstance ( ) {
        return serviceManager;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashServer getDashServer() {
        return dashServer;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private boolean start () {
        ////////////////////////////////////////
        // System Lock
        systemLock();
        ////////////////////////////////////////

        ////////////////////////////////////////
        // SCHEDULE MAIN JOBS
        int threadPoolSize = configManager.getThreadCount();

        if (scheduleManager.initJob(MAIN_SCHEDULE_JOB, threadPoolSize, threadPoolSize * 2)) {
            // FOR CHECKING the availability of this program
            Job haHandleJob = new JobBuilder()
                    .setScheduleManager(scheduleManager)
                    .setName(HaHandler.class.getSimpleName())
                    .setInitialDelay(0)
                    .setInterval(DELAY)
                    .setTimeUnit(TimeUnit.MILLISECONDS)
                    .setPriority(5)
                    .setTotalRunCount(1)
                    .setIsLasted(true)
                    .build();
            HaHandler haHandler = new HaHandler(haHandleJob);
            haHandler.start();
            if (scheduleManager.startJob(MAIN_SCHEDULE_JOB,
                    haHandler.getJob())) {
                logger.debug("[ServiceManager] [+RUN] HA Handler");
            } else {
                logger.warn("[ServiceManager] [-RUN FAIL] HA Handler");
                return false;
            }

            // FOR CHECKING [~/media_info/~]
            /**
             * scheduleManager,
             *                     FileKeeper.class.getSimpleName(),
             *                     0, DELAY, TimeUnit.MILLISECONDS,
             *                     10, 0, true
             */
            Job fileKeepJob = new JobBuilder()
                    .setScheduleManager(scheduleManager)
                    .setName(FileKeeper.class.getSimpleName())
                    .setInitialDelay(0)
                    .setInterval(DELAY)
                    .setTimeUnit(TimeUnit.MILLISECONDS)
                    .setPriority(10)
                    .setTotalRunCount(1)
                    .setIsLasted(true)
                    .build();
            FileKeeper fileKeeper = new FileKeeper(fileKeepJob);
            if (fileKeeper.init()) {
                fileKeeper.start();
                if (scheduleManager.startJob(MAIN_SCHEDULE_JOB, fileKeeper.getJob())) {
                    logger.debug("[ServiceManager] [+RUN] File Keeper");
                } else {
                    logger.warn("[ServiceManager] [-RUN FAIL] File Keeper");
                    scheduleManager.stopAll(MAIN_SCHEDULE_JOB);
                    return false;
                }
            } else {
                logger.warn("[ServiceManager] [-RUN FAIL] File Keeper");
                scheduleManager.stopAll(MAIN_SCHEDULE_JOB);
                return false;
            }
        }

        if (configManager.isEnableAutoDeleteUselessSession() || configManager.isEnableAutoDeleteUselessDir()) {
            if (scheduleManager.initJob(LONG_SESSION_REMOVE_SCHEDULE_JOB, 1, 1)) {
                // FOR REMOVING the old session & folder for this service
                Job longSessionRemoveJob = new JobBuilder()
                        .setScheduleManager(scheduleManager)
                        .setName(LongSessionRemover.class.getSimpleName())
                        .setInitialDelay(0)
                        .setInterval(DELAY)
                        .setTimeUnit(TimeUnit.MILLISECONDS)
                        .setPriority(3)
                        .setTotalRunCount(1)
                        .setIsLasted(true)
                        .build();
                LongSessionRemover longSessionRemover = new LongSessionRemover(longSessionRemoveJob);
                longSessionRemover.start();
                if (scheduleManager.startJob(LONG_SESSION_REMOVE_SCHEDULE_JOB,
                        longSessionRemover.getJob())) {
                    logger.debug("[ServiceManager] [+RUN] Long session remover");
                } else {
                    logger.warn("[ServiceManager] [-RUN FAIL] Long session remover");
                    scheduleManager.stopAll(MAIN_SCHEDULE_JOB);
                    return false;
                }
            }
        }
        ////////////////////////////////////////

        ////////////////////////////////////////
        // INITIATE DASH MANAGER
        if (dashServer.start()) {
            logger.debug("[ServiceManager] [+RUN] Dash server");
        } else {
            logger.warn("[ServiceManager] [-RUN FAIL] Dash server");
            scheduleManager.stopAll(MAIN_SCHEDULE_JOB);
            scheduleManager.stopAll(LONG_SESSION_REMOVE_SCHEDULE_JOB);
            return false;
        }
        ////////////////////////////////////////

        logger.debug("| All services are opened.");
        return true;
    }

    public void stop () {
        ////////////////////////////////////////
        // FINISH DASH MANAGER
        dashServer.stop();
        ////////////////////////////////////////

        ////////////////////////////////////////
        // FINISH ALL MAIN JOBS
        if (configManager.isEnableAutoDeleteUselessSession() && configManager.isEnableAutoDeleteUselessDir()) {
            scheduleManager.stopAll(LONG_SESSION_REMOVE_SCHEDULE_JOB);
        }
        scheduleManager.stopAll(MAIN_SCHEDULE_JOB);
        ////////////////////////////////////////

        ////////////////////////////////////////
        // System Unlock
        systemUnLock();
        ////////////////////////////////////////

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
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private void systemLock () {
        try {
            fileChannel = FileChannel.open(lockFile.toPath(), CREATE, READ, WRITE);
            lock = fileChannel.tryLock();
            if (lock == null) {
                logger.error("DASH process is already running.");
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
    ////////////////////////////////////////////////////////////////////////////////

}
