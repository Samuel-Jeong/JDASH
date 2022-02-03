package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @class public class ResourceManager
 * @brief ResourceManager class
 */
public class ResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    private final ConcurrentLinkedQueue<Integer> channelQueues = new ConcurrentLinkedQueue<>();
    private final int targetRtpPortMin;
    private final int targetRtpPortMax;
    private final int portGap = 2;

    ////////////////////////////////////////////////////////////////////////////////

    public ResourceManager(int targetRtpPortMin, int targetRtpPortMax) {
        this.targetRtpPortMin = targetRtpPortMin;
        this.targetRtpPortMax = targetRtpPortMax;
        initResource();
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void initResource() {
        for (int idx = targetRtpPortMin; idx <= targetRtpPortMax; idx += portGap) {
            try {
                channelQueues.add(idx);
            } catch (Exception e) {
                logger.error("Exception to RTP port resource in Queue", e);
                return;
            }
        }

        logger.info("Ready to RTP port resource in Queue. (port range: {} - {}, gap={})",
                targetRtpPortMin, targetRtpPortMax, portGap
        );
    }

    public void releaseResource () {
        channelQueues.clear();
        logger.info("Release RTP port resource in Queue. (port range: {} - {}, gap={})",
                targetRtpPortMin, targetRtpPortMax, portGap
        );
    }

    public int takePort () {
        if (channelQueues.isEmpty()) {
            logger.warn("RTP port resource in Queue is empty.");
            return -1;
        }

        int port = -1;
        try {
            Integer value = channelQueues.poll();
            if (value != null) {
                port = value;
            }
        } catch (Exception e) {
            logger.warn("Exception to get RTP port resource in Queue.", e);
        }

        logger.debug("Success to get RTP port(={}) resource in Queue.", port);
        return port;
    }

    public void restorePort (int port) {
        if (!channelQueues.contains(port)) {
            try {
                channelQueues.offer(port);
            } catch (Exception e) {
                logger.warn("Exception to restore RTP port(={}) resource in Queue.", port, e);
            }
        }
    }

    public void removePort (int port) {
        try {
            channelQueues.remove(port);
        } catch (Exception e) {
            logger.warn("Exception to remove to RTP port(={}) resource in Queue.", port, e);
        }
    }

}
