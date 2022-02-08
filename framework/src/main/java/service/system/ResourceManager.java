package service.system;

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
    private final int targetPortMin;
    private final int targetPortMax;
    private final int portGap = 2;

    ////////////////////////////////////////////////////////////////////////////////

    public ResourceManager(int targetPortMin, int targetPortMax) {
        this.targetPortMin = targetPortMin;
        this.targetPortMax = targetPortMax;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void initResource() {
        for (int idx = targetPortMin; idx <= targetPortMax; idx += portGap) {
            try {
                channelQueues.add(idx);
            } catch (Exception e) {
                logger.error("Exception to port resource in Queue", e);
                return;
            }
        }

        logger.info("Ready to port resource in Queue. (port range: {} - {}, gap={})",
                targetPortMin, targetPortMax, portGap
        );
    }

    public void releaseResource () {
        channelQueues.clear();
        logger.info("Release port resource in Queue. (port range: {} - {}, gap={})",
                targetPortMin, targetPortMax, portGap
        );
    }

    public int takePort () {
        if (channelQueues.isEmpty()) {
            logger.warn("port resource in Queue is empty.");
            return -1;
        }

        int port = -1;
        try {
            Integer value = channelQueues.poll();
            if (value != null) {
                port = value;
            }
        } catch (Exception e) {
            logger.warn("Exception to get port resource in Queue.", e);
        }

        logger.debug("Success to get port(={}) resource in Queue.", port);
        return port;
    }

    public void restorePort (int port) {
        if (!channelQueues.contains(port)) {
            try {
                channelQueues.offer(port);
            } catch (Exception e) {
                logger.warn("Exception to restore port(={}) resource in Queue.", port, e);
            }
        }
    }

    public void removePort (int port) {
        try {
            channelQueues.remove(port);
        } catch (Exception e) {
            logger.warn("Exception to remove to port(={}) resource in Queue.", port, e);
        }
    }

}
