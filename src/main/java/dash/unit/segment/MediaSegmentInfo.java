package dash.unit.segment;

import config.ConfigManager;
import dash.unit.MediaType;
import service.AppInstance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * - 공통 필수 구성 요소
 * 1) 마지막 세그먼트 번호 : L_S (Last Segment)
 * 2) 첫 세그먼트 번호 : F_S (First Segment)
 * 3) 요청받은 세그먼트 번호 : R_S (Requested Segment)
 * 4) 전체 세그먼트 파일 개수(초기화 세그먼트 파일(init) 제외) : T_S (Total Segment)
 * 5) 유지할 세그먼트 파일 개수 간격 : S_I (Segment Interval)
 * 6) 현재 세그먼트 파일 간격 : L_S - R_S = C_I (Current segment Interval)
 *
 * 1. T_S 가 0 보다 커야 한다.
 * 2. F_S 는 L_S 보다 작거나 같아야 한다.
 * 3. R_S 는 F_S 보다 크거나 같아야 하고 L_S 보다 작거나 같아야 한다.
 * 4. C_I 와 S_I 는 T_S 보다 크면 안된다.
 * 5. C_I 가 S_I 보다 작거나 같으면 삭제하지 않는다.
 * 6. C_I 가 S_I 보다 클 때,
 * 	6.1. T_S/2 보다 크면 삭제하지 않는다.
 * 	6.2. T_S/2 보다 작거나 같으면 F_S 를 삭제한다.
 *
 * - 예)
 * F_S : 50
 * L_S : 100
 * I_S : 75
 * T_S : 50
 * S_I : 20
 * C_I : 25
 * > F_S 삭제한다.
 */
public class MediaSegmentInfo {

    private final MediaType mediaType;

    private final AtomicLong lastSegmentNumber = new AtomicLong(1);
    private final AtomicLong firstSegmentNumber = new AtomicLong(0);
    private final AtomicLong requestedSegmentNumber = new AtomicLong(0);
    private final AtomicLong totalSegmentCount = new AtomicLong(0);
    private final AtomicLong currentSegmentFrontInterval = new AtomicLong(0);
    private final AtomicLong currentSegmentRearInterval = new AtomicLong(0);
    private final int segmentInterval;

    public MediaSegmentInfo(MediaType mediaType) {
        this.mediaType = mediaType;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        segmentInterval = configManager.getChunkFileDeletionWindowSize();
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setLastSegmentNumber(long value) {
        lastSegmentNumber.set(value);
    }

    public long getLastSegmentNumber() {
        return lastSegmentNumber.get();
    }

    public long incAndGetFirstSegmentNumber() {
        return firstSegmentNumber.incrementAndGet();
    }

    public void setFirstSegmentNumber(long firstSegmentNumber) {
        this.firstSegmentNumber.set(firstSegmentNumber);
    }

    public long getFirstSegmentNumber() {
        return firstSegmentNumber.get();
    }

    public void setRequestedSegmentNumber(long value) {
        requestedSegmentNumber.set(value);
    }

    public long getRequestedSegmentNumber() {
        return requestedSegmentNumber.get();
    }

    public long getTotalSegmentCount() {
        totalSegmentCount.set(getLastSegmentNumber() - getFirstSegmentNumber());
        return totalSegmentCount.get();
    }

    public long getCurrentSegmentRearInterval() {
        long firstSegmentNumber = getFirstSegmentNumber();
        long requestedSegmentNumber = getRequestedSegmentNumber();
        if (requestedSegmentNumber <= 0) {
            requestedSegmentNumber = firstSegmentNumber;
        }

        currentSegmentRearInterval.set(getLastSegmentNumber() - requestedSegmentNumber);
        return currentSegmentRearInterval.get();
    }

    public long getCurrentSegmentFrontInterval() {
        long lastSegmentNumber = getLastSegmentNumber();
        long requestedSegmentNumber = getRequestedSegmentNumber();
        if (requestedSegmentNumber <= 0) {
            requestedSegmentNumber = lastSegmentNumber;
        }

        currentSegmentFrontInterval.set(requestedSegmentNumber - getFirstSegmentNumber());
        return currentSegmentFrontInterval.get();
    }

    public long getSegmentInterval() {
        return segmentInterval;
    }
}
