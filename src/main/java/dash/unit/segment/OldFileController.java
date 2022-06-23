package dash.unit.segment;

import config.ConfigManager;
import dash.mpd.MpdManager;
import dash.unit.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.job.Job;
import service.scheduler.job.JobContainer;
import util.module.FileManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class OldFileController extends JobContainer {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(OldFileController.class);

    private final MpdManager mpdManager;
    private final String dashUnitId; // DashUnit ID
    private final String dashPath; // 현재 DASH Streaming 경로
    private final long limitTime; // 제한 시간

    private final String[] exceptFileNameList = new String[] { "init" };
    private final String[] exceptFileExtensionList = new String[] { "mpd" };

    private final FileManager fileManager = new FileManager();
    private final MediaSegmentInfo mediaSegmentInfo;

    private final AtomicLong prevRequestedSegmentIndex = new AtomicLong(0);
    private final AtomicInteger curTimeOffset = new AtomicInteger(0);
    private final int remoteTimeOffset;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public OldFileController(Job oldFileControlJob, MpdManager manager, String dashUnitId, String dashPath, MediaSegmentInfo mediaSegmentInfo) {
        setJob(oldFileControlJob);

        this.mpdManager = manager;
        this.dashUnitId = dashUnitId;
        this.dashPath = dashPath;
        this.mediaSegmentInfo = mediaSegmentInfo;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        this.limitTime = configManager.getChunkFileDeletionWindowSize();
        this.remoteTimeOffset = (int) configManager.getRemoteTimeOffset();

        logger.debug("[DashUnit(id={})] OldFileController is initiated. (dashPath={}, timeLimit={})",
                dashUnitId, dashPath, limitTime
        );
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void init() {
       getJob().setRunnable(() -> {
           if (dashPath == null) { return; }

           try {
               // 삭제 조건 확인
               long totalSegmentCount = mediaSegmentInfo.getTotalSegmentCount();
               if (totalSegmentCount <= 0) { return; }

               long requestedSegmentIndex = mediaSegmentInfo.getRequestedSegmentNumber();
               long firstSegmentIndex = mediaSegmentInfo.getFirstSegmentNumber();
               long lastSegmentIndex = mediaSegmentInfo.getLastSegmentNumber();
               long currentSegmentFrontInterval = mediaSegmentInfo.getCurrentSegmentFrontInterval();
               long currentSegmentRearInterval = mediaSegmentInfo.getCurrentSegmentRearInterval();
               long segmentInterval = mediaSegmentInfo.getSegmentInterval();

               /*logger.debug("{} totalSegmentCount={}, firstSegmentIndex: {}, lastSegmentIndex: {}, requestedSegmentIndex: {}, currentSegmentFrontInterval: {}, currentSegmentRearInterval: {}, segmentInterval: {}",
                       mediaSegmentInfo.getMediaType().name(), totalSegmentCount, firstSegmentIndex, lastSegmentIndex, requestedSegmentIndex, currentSegmentFrontInterval, currentSegmentRearInterval, segmentInterval
               );*/

               // 세그먼트 요청 시간만큼 계산
               if (requestedSegmentIndex > 0
                       && requestedSegmentIndex == prevRequestedSegmentIndex.get()) {
                   if (curTimeOffset.get() >= remoteTimeOffset) {
                       curTimeOffset.set(0);
                       mediaSegmentInfo.setRequestedSegmentNumber(0);
                       logger.debug("[DashUnit(id={})] RequestedSegmentNumber is reset to [ {} ].", dashUnitId, mediaSegmentInfo.getRequestedSegmentNumber());
                   } else {
                       curTimeOffset.incrementAndGet();
                   }
               }
               prevRequestedSegmentIndex.set(requestedSegmentIndex);

               if (firstSegmentIndex > lastSegmentIndex) { return; }
               if (requestedSegmentIndex > 0 &&
                       (requestedSegmentIndex < firstSegmentIndex
                               || requestedSegmentIndex > lastSegmentIndex)) { return; }
               if (currentSegmentFrontInterval <= segmentInterval
                       && currentSegmentRearInterval <= segmentInterval) { return; }

               String segmentFileName = mediaSegmentInfo.getMediaType() == MediaType.AUDIO ?
                       mpdManager.getAudioMediaSegmentName(firstSegmentIndex) : mpdManager.getVideoMediaSegmentName(firstSegmentIndex);
               long changedFirstSegmentNumber = mediaSegmentInfo.incAndGetFirstSegmentNumber();

               if (fileManager.deleteFile(fileManager.concatFilePath(dashPath, segmentFileName))) {
                   logger.trace("[DashUnit(id={})] First segment({}) is changed : {}", dashUnitId, firstSegmentIndex, changedFirstSegmentNumber);
               } else {
                   if (fileManager.deleteOldFileBySecond(
                           dashPath,
                           exceptFileNameList,
                           exceptFileExtensionList,
                           limitTime)) {
                       logger.trace("[DashUnit(id={})] First segment({}) is changed : {}", dashUnitId, firstSegmentIndex, changedFirstSegmentNumber);
                   }
               }
           } catch (Exception e) {
               logger.warn("[DashUnit(id={})] OldFileController.run.Exception", dashUnitId, e);
               getJob().getScheduleManager().stopJob(getJob().getScheduleUnitKey(), getJob());
           }
       });
    }
    ////////////////////////////////////////////////////////////

}
