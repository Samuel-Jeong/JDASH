package stream;

import java.util.Arrays;

public class AvUtilLogLevelChecker {

    private enum AV_UTIL_LOG_LEVEL {

        AV_LOG_QUIET(-8),
        AV_LOG_PANIC(0),
        AV_LOG_FATAL(8),
        AV_LOG_ERROR(16),
        AV_LOG_WARNING(24),
        AV_LOG_INFO(32),
        AV_LOG_VERBOSE(40),
        AV_LOG_DEBUG(48),
        AV_LOG_TRACE(56),
        AV_LOG_MAX_OFFSET(64)
        ;

        final int value;

        AV_UTIL_LOG_LEVEL(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static boolean checkLogLevel(int level) {
        return Arrays.stream(AV_UTIL_LOG_LEVEL.values()).
                anyMatch(
                        logLevel -> logLevel.getValue() == level
                );
    }

}
