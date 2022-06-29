package config;

import lombok.Data;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.AvUtilLogLevelChecker;
import stream.StreamConfigManager;

import java.io.File;
import java.io.IOException;

/**
 * @class public class UserConfig
 * @brief UserConfig Class
 */
@Data
public class ConfigManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private Ini ini = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTANT
    private static final String CONSTANT_PRINT_SUCCESS_LOG_FORMAT = "Load [{}] config...(OK)";
    private static final String CONSTANT_PRINT_FAIL_LOG_FORMAT_1 = "Fail to load [{}-{}].";
    private static final String CONSTANT_PRINT_FAIL_LOG_FORMAT_2 = "Fail to load [{}-{}]. ({})";
    public static final String CONSTANT_SERVICE_DASH = "UDASH"; // UDASH Service 이름
    public static final String CONSTANT_SERVICE_CDN = "UCDN"; // UCDN Service 이름
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // Section String
    public static final String SECTION_COMMON = "COMMON"; // COMMON Section 이름
    public static final String SECTION_SERVER = "SERVER"; // SERVER Section 이름
    public static final String SECTION_CLIENT = "CLIENT"; // CLIENT Section 이름
    public static final String SECTION_MEDIA = "MEDIA"; // MEDIA Section 이름
    public static final String SECTION_MPD = "MPD"; // MPD Section 이름
    public static final String SECTION_RTMP = "RTMP"; // RTMP Section 이름
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // Field String
    // COMMON
    public static final String FIELD_ID = "ID";
    public static final String FIELD_REMOTE_ID = "REMOTE_ID";
    public static final String FIELD_SERVICE_NAME = "SERVICE_NAME";
    public static final String FIELD_ENABLE_CLIENT = "ENABLE_CLIENT";
    public static final String FIELD_THREAD_COUNT = "THREAD_COUNT";
    public static final String FIELD_SEND_BUF_SIZE = "SEND_BUF_SIZE";
    public static final String FIELD_RECV_BUF_SIZE = "RECV_BUF_SIZE";

    // SERVER
    public static final String FIELD_STREAMING = "STREAMING";
    public static final String FIELD_ENABLE_PRELOAD_WITH_DASH = "ENABLE_PRELOAD_WITH_DASH";
    public static final String FIELD_HTTP_LISTEN_IP = "HTTP_LISTEN_IP";
    public static final String FIELD_HTTP_LISTEN_PORT = "HTTP_LISTEN_PORT";
    public static final String FIELD_HTTP_LISTEN_PORT_BEGIN_OFFSET = "HTTP_LISTEN_PORT_BEGIN_OFFSET";
    public static final String FIELD_HTTP_LISTEN_PORT_END_OFFSET = "HTTP_LISTEN_PORT_END_OFFSET";
    public static final String FIELD_PREPROCESS_LISTEN_IP = "PREPROCESS_LISTEN_IP";
    public static final String FIELD_PREPROCESS_LISTEN_PORT = "PREPROCESS_LISTEN_PORT";
    public static final String FIELD_MAX_DASH_UNIT_LIMIT = "MAX_DASH_UNIT_LIMIT";
    public static final String FIELD_ENABLE_AUTO_DELETE_USELESS_SESSION = "ENABLE_AUTO_DELETE_USELESS_SESSION";
    public static final String FIELD_AUTO_DELETE_SESSION_LIMIT_TIME = "AUTO_DELETE_SESSION_LIMIT_TIME";
    public static final String FIELD_ENABLE_AUTO_DELETE_USELESS_DIR = "ENABLE_AUTO_DELETE_USELESS_DIR";
    public static final String FIELD_AUTO_DELETE_DIR_LIMIT_TIME = "AUTO_DELETE_DIR_LIMIT_TIME";
    public static final String FIELD_LOCAL_MPD_LISTEN_SOCKET_SIZE = "LOCAL_MPD_LISTEN_SOCKET_SIZE";
    public static final String FIELD_LOCAL_AUDIO_LISTEN_SOCKET_SIZE= "LOCAL_AUDIO_LISTEN_SOCKET_SIZE";
    public static final String FIELD_LOCAL_VIDEO_LISTEN_SOCKET_SIZE= "LOCAL_VIDEO_LISTEN_SOCKET_SIZE";

    // CLIENT
    public static final String FIELD_ENABLE_GUI = "ENABLE_GUI";
    public static final String FIELD_CAMERA_PATH = "CAMERA_PATH";
    public static final String FIELD_HTTP_TARGET_IP = "HTTP_TARGET_IP";
    public static final String FIELD_HTTP_TARGET_PORT = "HTTP_TARGET_PORT";
    public static final String FIELD_PREPROCESS_INIT_IDLE_TIME = "PREPROCESS_INIT_IDLE_TIME";
    public static final String FIELD_PREPROCESS_TARGET_IP = "PREPROCESS_TARGET_IP";
    public static final String FIELD_PREPROCESS_TARGET_PORT = "PREPROCESS_TARGET_PORT";
    public static final String FIELD_DOWNLOAD_CHUNK_RETRY_COUNT = "DOWNLOAD_CHUNK_RETRY_COUNT";

    // MEDIA
    public static final String FIELD_MEDIA_BASE_PATH = "MEDIA_BASE_PATH";
    public static final String FIELD_MEDIA_LIST_PATH = "MEDIA_LIST_PATH";
    public static final String FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED = "CLEAR_DASH_DATA_IF_SESSION_CLOSED";
    public static final String FIELD_CHUNK_FILE_DELETION_WINDOW_SIZE = "CHUNK_FILE_DELETION_WINDOW_SIZE";
    public static final String FIELD_AUDIO_ONLY = "AUDIO_ONLY";
    public static final String FIELD_LOCAL_AUDIO_CODEC = "LOCAL_AUDIO_CODEC";
    public static final String FIELD_LOCAL_AUDIO_SAMPLERATE = "LOCAL_AUDIO_SAMPLERATE";
    public static final String FIELD_LOCAL_VIDEO_CODEC = "LOCAL_VIDEO_CODEC";
    public static final String FIELD_LOCAL_VIDEO_PIXEL_FORMAT = "LOCAL_VIDEO_PIXEL_FORMAT";
    public static final String FIELD_MEDIA_LOCAL_VIDEO_WIDTH = "LOCAL_VIDEO_WIDTH";
    public static final String FIELD_MEDIA_LOCAL_VIDEO_HEIGHT = "LOCAL_VIDEO_HEIGHT";
    public static final String FIELD_REMOTE_AUDIO_CODEC = "REMOTE_AUDIO_CODEC";
    public static final String FIELD_REMOTE_AUDIO_SAMPLERATE = "REMOTE_AUDIO_SAMPLERATE";
    public static final String FIELD_REMOTE_VIDEO_CODEC = "REMOTE_VIDEO_CODEC";
    public static final String FIELD_REMOTE_VIDEO_PIXEL_FORMAT = "REMOTE_VIDEO_PIXEL_FORMAT";
    public static final String FIELD_MEDIA_REMOTE_VIDEO_WIDTH = "REMOTE_VIDEO_WIDTH";
    public static final String FIELD_MEDIA_REMOTE_VIDEO_HEIGHT = "REMOTE_VIDEO_HEIGHT";

    // MPD
    public static final String FIELD_ENABLE_VALIDATION = "ENABLE_VALIDATION";
    public static final String FIELD_REPRESENTATION_ID_FORMAT = "REPRESENTATION_ID_FORMAT";
    public static final String FIELD_CHUNK_NUMBER_FORMAT = "CHUNK_NUMBER_FORMAT";
    public static final String FIELD_SEGMENT_NUMBER_FORMAT = "SEGMENT_NUMBER_FORMAT";
    public static final String FIELD_VALIDATION_XSD_PATH = "VALIDATION_XSD_PATH";
    public static final String FIELD_SEGMENT_DURATION = "SEGMENT_DURATION";
    public static final String FIELD_WINDOW_SIZE = "WINDOW_SIZE";
    public static final String FIELD_LOCAL_TIME_OFFSET = "LOCAL_TIME_OFFSET";
    public static final String FIELD_REMOTE_TIME_OFFSET = "REMOTE_TIME_OFFSET";

    // RTMP
    public static final String FIELD_RTMP_PUBLISH_IP = "RTMP_PUBLISH_IP";
    public static final String FIELD_RTMP_PUBLISH_PORT = "RTMP_PUBLISH_PORT";
    public static final String FIELD_RTMP_LOG_LEVEL = "LOG_LEVEL";
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // VARIABLES
    // COMMON
    private String id = null;
    private String remoteId = null;
    private String serviceName = null;
    private boolean enableClient = false;
    private int threadCount = 0;
    private int sendBufSize = 0;
    private int recvBufSize = 0;

    // SERVER
    private String streaming = null;
    private boolean enablePreloadWithDash = false;
    private String httpListenIp = null;
    private int httpListenPort = 0;
    private int httpListenPortBeginOffset = 0;
    private int httpListenPortEndOffset = 0;
    private String preprocessListenIp = null;
    private int preprocessListenPort = 0;
    private int maxDashUnitLimit = 0;
    private boolean enableAutoDeleteUselessSession = false;
    private long autoDeleteSessionLimitTime = 0; // ms
    private boolean enableAutoDeleteUselessDir = false;
    private long autoDeleteDirLimitTime = 0; // ms
    private int localMpdListenSocketSize = 0; // 1 ~ 10000
    private int localAudioListenSocketSize = 0; // 1 ~ 10000
    private int localVideoListenSocketSize = 0; // 1 ~ 10000

    // CLIENT
    private boolean enableGui = false;
    private String cameraPath = null;
    private String httpTargetIp = null;
    private int httpTargetPort = 0;
    private long preprocessInitIdleTime = 0; // ms
    private String preprocessTargetIp = null;
    private int preprocessTargetPort = 0;
    private int downloadChunkRetryCount = 0;

    // MEDIA
    private String mediaBasePath = null;
    private String mediaListPath = null;
    private boolean clearDashDataIfSessionClosed = true;
    private int chunkFileDeletionWindowSize = 0;
    private boolean audioOnly = false;
    private int localAudioCodec = 0;
    private int localAudioSampleRate = 0;
    private int localVideoCodec = 0;
    private int localVideoPixelFormat = 0;
    private int localVideoWidth = 0;
    private int localVideoHeight = 0;
    private int remoteAudioCodec = 0;
    private int remoteAudioSampleRate = 0;
    private int remoteVideoCodec = 0;
    private int remoteVideoPixelFormat = 0;
    private int remoteVideoWidth = 0;
    private int remoteVideoHeight = 0;

    // MPD
    private boolean enableValidation = false;
    private String representationIdFormat = null;
    private String chunkNumberFormat = null;
    private String segmentNumberFormat = null;
    private String validationXsdPath = null;
    private double segmentDuration = 0.0d;
    private double localTimeOffset = 0.0d;
    private double remoteTimeOffset = 0.0d;
    private int windowSize = 0;

    // RTMP
    private String rtmpServerIp = null;
    private int rtmpServerPort = 0;

    /**
     * AV_LOG_QUIET = -8; AV_LOG_PANIC = 0; AV_LOG_FATAL = 8;
     * AV_LOG_ERROR = 16; AV_LOG_WARNING = 24; AV_LOG_INFO = 32;
     * AV_LOG_VERBOSE = 40; AV_LOG_DEBUG = 48; AV_LOG_TRACE = 56;
     * AV_LOG_MAX_OFFSET = 64;
     */
    private int rtmpLogLevel = 16; // default : 16 (AV_LOG_ERROR)
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn public AuditConfig(String configPath)
     * @brief AuditConfig 생성자 함수
     * @param configPath Config 파일 경로 이름
     */
    public ConfigManager(String configPath) {
        File iniFile = new File(configPath);
        if (!iniFile.isFile() || !iniFile.exists()) {
            logger.warn("Not found the config path. (path={})", configPath);
            System.exit(1);
        }

        try {
            this.ini = new Ini(iniFile);

            loadCommonConfig();
            loadServerConfig();
            loadClientConfig();
            loadMediaConfig();
            loadMpdConfig();
            loadRtmpConfig();

            logger.info("Load config [{}]", configPath);
        } catch (IOException e) {
            logger.error("ConfigManager.IOException", e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn private void loadCommonConfig()
     * @brief COMMON Section 을 로드하는 함수
     */
    private void loadCommonConfig() {
        this.id = getIniValue(SECTION_COMMON, FIELD_ID);
        if (id == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_COMMON, FIELD_ID);
            System.exit(1);
        }

        this.remoteId = getIniValue(SECTION_COMMON, FIELD_REMOTE_ID);
        if (remoteId == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_COMMON, FIELD_REMOTE_ID);
            System.exit(1);
        }

        this.serviceName = getIniValue(SECTION_COMMON, FIELD_SERVICE_NAME);
        if (serviceName == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_COMMON, FIELD_SERVICE_NAME);
            System.exit(1);
        } else if (!serviceName.equals(CONSTANT_SERVICE_DASH) && !serviceName.equals(CONSTANT_SERVICE_CDN)) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_COMMON, FIELD_SERVICE_NAME);
            logger.error("MUST CHECK SERVICE NAME (given: {}, expected: {} or {})",
                    serviceName, CONSTANT_SERVICE_DASH, CONSTANT_SERVICE_CDN
            );
            System.exit(1);
        }

        String enableClientString = getIniValue(SECTION_COMMON, FIELD_ENABLE_CLIENT);
        if (enableClientString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_COMMON, FIELD_ENABLE_CLIENT);
            System.exit(1);
        } else {
            this.enableClient = Boolean.parseBoolean(enableClientString);
        }

        this.threadCount = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_THREAD_COUNT));
        if (this.threadCount <= 0) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_2, SECTION_COMMON, FIELD_THREAD_COUNT, threadCount);
            System.exit(1);
        }

        this.sendBufSize = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_SEND_BUF_SIZE));
        if (this.sendBufSize <= 0) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_2, SECTION_COMMON, FIELD_SEND_BUF_SIZE, sendBufSize);
            System.exit(1);
        }

        this.recvBufSize = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_RECV_BUF_SIZE));
        if (this.recvBufSize <= 0) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_2, SECTION_COMMON, FIELD_RECV_BUF_SIZE, recvBufSize);
            System.exit(1);
        }

        logger.debug(CONSTANT_PRINT_SUCCESS_LOG_FORMAT, SECTION_COMMON);
    }

    /**
     * @fn private void loadServerConfig()
     * @brief SERVER Section 을 로드하는 함수
     */
    private void loadServerConfig() {
        this.streaming = getIniValue(SECTION_SERVER, FIELD_STREAMING);
        if (streaming == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_STREAMING);
            System.exit(1);
        } else {
            if (!streaming.equals(StreamConfigManager.STREAMING_WITH_DASH)
                    && !streaming.equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_STREAMING);
                System.exit(1);
            }
        }

        String enablePreloadWithDashString = getIniValue(SECTION_SERVER, FIELD_ENABLE_PRELOAD_WITH_DASH);
        if (enablePreloadWithDashString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_ENABLE_PRELOAD_WITH_DASH);
            System.exit(1);
        } else {
            this.enablePreloadWithDash = Boolean.parseBoolean(enablePreloadWithDashString);
        }

        this.httpListenIp = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_IP);
        if (this.httpListenIp == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_IP);
            System.exit(1);
        }

        String httpListenPortString = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
        if (httpListenPortString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
            System.exit(1);
        } else {
            this.httpListenPort = Integer.parseInt(httpListenPortString);
            if (this.httpListenPort <= 0 || this.httpListenPort > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
                System.exit(1);
            }
        }

        String httpListenPortBeginOffsetString = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_BEGIN_OFFSET);
        if (httpListenPortBeginOffsetString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_BEGIN_OFFSET);
            System.exit(1);
        } else {
            this.httpListenPortBeginOffset = Integer.parseInt(httpListenPortBeginOffsetString);
            if (this.httpListenPortBeginOffset <= 0 || this.httpListenPort + this.httpListenPortBeginOffset > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_BEGIN_OFFSET);
                System.exit(1);
            }
        }

        String httpListenPortEndOffsetString = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_END_OFFSET);
        if (httpListenPortEndOffsetString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_END_OFFSET);
            System.exit(1);
        } else {
            this.httpListenPortEndOffset = Integer.parseInt(httpListenPortEndOffsetString);
            if (this.httpListenPortEndOffset <= 0 || this.httpListenPort + this.httpListenPortEndOffset > 65535
                    || this.httpListenPortBeginOffset >= this.httpListenPortEndOffset) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_HTTP_LISTEN_PORT_END_OFFSET);
                System.exit(1);
            }
        }

        this.preprocessListenIp = getIniValue(SECTION_SERVER, FIELD_PREPROCESS_LISTEN_IP);
        if (this.preprocessListenIp == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_PREPROCESS_LISTEN_IP);
            System.exit(1);
        }

        String preprocessListenPortString = getIniValue(SECTION_SERVER, FIELD_PREPROCESS_LISTEN_PORT);
        if (preprocessListenPortString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_PREPROCESS_LISTEN_PORT);
            System.exit(1);
        } else {
            this.preprocessListenPort = Integer.parseInt(preprocessListenPortString);
            if (this.preprocessListenPort <= 0 || this.preprocessListenPort > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_PREPROCESS_TARGET_PORT);
                System.exit(1);
            }
        }

        String maxDashUnitLimitString = getIniValue(SECTION_SERVER, FIELD_MAX_DASH_UNIT_LIMIT);
        if (maxDashUnitLimitString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_MAX_DASH_UNIT_LIMIT);
            System.exit(1);
        } else {
            this.maxDashUnitLimit = Integer.parseInt(maxDashUnitLimitString);
            if (this.maxDashUnitLimit <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_MAX_DASH_UNIT_LIMIT);
                System.exit(1);
            }
        }

        String enableAutoDeleteUselessSessionString = getIniValue(SECTION_SERVER, FIELD_ENABLE_AUTO_DELETE_USELESS_SESSION);
        if (enableAutoDeleteUselessSessionString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_ENABLE_AUTO_DELETE_USELESS_SESSION);
            System.exit(1);
        } else {
            this.enableAutoDeleteUselessSession = Boolean.parseBoolean(enableAutoDeleteUselessSessionString);
        }

        String autoDeleteSessionLimitTimeString = getIniValue(SECTION_SERVER, FIELD_AUTO_DELETE_SESSION_LIMIT_TIME);
        if (autoDeleteSessionLimitTimeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_AUTO_DELETE_SESSION_LIMIT_TIME);
            System.exit(1);
        } else {
            this.autoDeleteSessionLimitTime = Long.parseLong(autoDeleteSessionLimitTimeString);
            if (this.autoDeleteSessionLimitTime < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_AUTO_DELETE_SESSION_LIMIT_TIME);
                System.exit(1);
            }
        }

        String enableAutoDeleteUselessDirString = getIniValue(SECTION_SERVER, FIELD_ENABLE_AUTO_DELETE_USELESS_DIR);
        if (enableAutoDeleteUselessDirString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_ENABLE_AUTO_DELETE_USELESS_DIR);
            System.exit(1);
        } else {
            this.enableAutoDeleteUselessDir = Boolean.parseBoolean(enableAutoDeleteUselessDirString);
        }

        String autoDeleteDirLimitTimeString = getIniValue(SECTION_SERVER, FIELD_AUTO_DELETE_DIR_LIMIT_TIME);
        if (autoDeleteDirLimitTimeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_AUTO_DELETE_DIR_LIMIT_TIME);
            System.exit(1);
        } else {
            this.autoDeleteDirLimitTime = Long.parseLong(autoDeleteDirLimitTimeString);
            if (this.autoDeleteDirLimitTime < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_AUTO_DELETE_DIR_LIMIT_TIME);
                System.exit(1);
            }
        }

        String localMpdListenSocketSizeString = getIniValue(SECTION_SERVER, FIELD_LOCAL_MPD_LISTEN_SOCKET_SIZE);
        if (localMpdListenSocketSizeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_MPD_LISTEN_SOCKET_SIZE);
            System.exit(1);
        } else {
            this.localMpdListenSocketSize = Integer.parseInt(localMpdListenSocketSizeString);
            if (this.localMpdListenSocketSize <= 0 || this.localMpdListenSocketSize > 10000) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_MPD_LISTEN_SOCKET_SIZE);
                System.exit(1);
            }
        }

        String localAudioListenSocketSizeString = getIniValue(SECTION_SERVER, FIELD_LOCAL_AUDIO_LISTEN_SOCKET_SIZE);
        if (localAudioListenSocketSizeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_AUDIO_LISTEN_SOCKET_SIZE);
            System.exit(1);
        } else {
            this.localAudioListenSocketSize = Integer.parseInt(localAudioListenSocketSizeString);
            if (this.localAudioListenSocketSize <= 0 || this.localAudioListenSocketSize > 10000) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_AUDIO_LISTEN_SOCKET_SIZE);
                System.exit(1);
            }
        }

        String localVideoListenSocketSizeString = getIniValue(SECTION_SERVER, FIELD_LOCAL_VIDEO_LISTEN_SOCKET_SIZE);
        if (localVideoListenSocketSizeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_VIDEO_LISTEN_SOCKET_SIZE);
            System.exit(1);
        } else {
            this.localVideoListenSocketSize = Integer.parseInt(localVideoListenSocketSizeString);
            if (this.localVideoListenSocketSize <= 0 || this.localVideoListenSocketSize > 10000) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_SERVER, FIELD_LOCAL_VIDEO_LISTEN_SOCKET_SIZE);
                System.exit(1);
            }
        }
    }

    /**
     * @fn private void loadClientConfig()
     * @brief CLIENT Section 을 로드하는 함수
     */
    private void loadClientConfig() {
        this.cameraPath = getIniValue(SECTION_CLIENT, FIELD_CAMERA_PATH);
        if (cameraPath == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_CAMERA_PATH);
            System.exit(1);
        }

        String enableGuiString = getIniValue(SECTION_CLIENT, FIELD_ENABLE_GUI);
        if (enableGuiString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_ENABLE_GUI);
            System.exit(1);
        } else {
            this.enableGui = Boolean.parseBoolean(enableGuiString);
        }

        this.httpTargetIp = getIniValue(SECTION_CLIENT, FIELD_HTTP_TARGET_IP);
        if (this.httpTargetIp == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_HTTP_TARGET_IP);
            System.exit(1);
        }

        String httpTargetPortString = getIniValue(SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
        if (httpTargetPortString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
            System.exit(1);
        } else {
            this.httpTargetPort = Integer.parseInt(httpTargetPortString);
            if (this.httpTargetPort <= 0 || this.httpTargetPort > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
                System.exit(1);
            }
        }

        String preprocessInitIdleTimeSring = getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_INIT_IDLE_TIME);
        if (preprocessInitIdleTimeSring == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_PREPROCESS_INIT_IDLE_TIME);
            System.exit(1);
        } else {
            this.preprocessInitIdleTime = Long.parseLong(preprocessInitIdleTimeSring);
            if (this.preprocessInitIdleTime < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_PREPROCESS_INIT_IDLE_TIME);
                System.exit(1);
            }
        }

        this.preprocessTargetIp = getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_TARGET_IP);
        if (this.preprocessTargetIp == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_PREPROCESS_TARGET_IP);
            System.exit(1);
        }

        String preprocessTargetPortString = getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
        if (preprocessTargetPortString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
            System.exit(1);
        } else {
            this.preprocessTargetPort = Integer.parseInt(preprocessTargetPortString);
            if (this.preprocessTargetPort <= 0 || this.preprocessTargetPort > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
                System.exit(1);
            }
        }

        String downloadChunkRetryCountString = getIniValue(SECTION_CLIENT, FIELD_DOWNLOAD_CHUNK_RETRY_COUNT);
        if (downloadChunkRetryCountString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_DOWNLOAD_CHUNK_RETRY_COUNT);
            System.exit(1);
        } else {
            this.downloadChunkRetryCount = Integer.parseInt(downloadChunkRetryCountString);
            if (this.downloadChunkRetryCount < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_CLIENT, FIELD_DOWNLOAD_CHUNK_RETRY_COUNT);
                System.exit(1);
            }
        }
    }

    /**
     * @fn private void loadMediaConfig()
     * @brief MEDIA Section 을 로드하는 함수
     */
    private void loadMediaConfig() {
        this.mediaBasePath = getIniValue(SECTION_MEDIA, FIELD_MEDIA_BASE_PATH);
        if (mediaBasePath == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_BASE_PATH);
            System.exit(1);
        }

        this.mediaListPath = getIniValue(SECTION_MEDIA, FIELD_MEDIA_LIST_PATH);
        if (mediaListPath == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_LIST_PATH);
            System.exit(1);
        }

        String clearDashDataIfSessionClosedString = getIniValue(SECTION_MEDIA, FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED);
        if (clearDashDataIfSessionClosedString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED);
            System.exit(1);
        } else {
            this.clearDashDataIfSessionClosed = Boolean.parseBoolean(clearDashDataIfSessionClosedString);
        }

        String chunkFileDeletionWindowSizeString = getIniValue(SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_WINDOW_SIZE);
        if (chunkFileDeletionWindowSizeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_WINDOW_SIZE);
            System.exit(1);
        } else {
            this.chunkFileDeletionWindowSize = Integer.parseInt(chunkFileDeletionWindowSizeString);
            if (this.chunkFileDeletionWindowSize <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_WINDOW_SIZE);
                System.exit(1);
            }
        }

        String audioOnlyString = getIniValue(SECTION_MEDIA, FIELD_AUDIO_ONLY);
        if (audioOnlyString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_AUDIO_ONLY);
            System.exit(1);
        } else {
            this.audioOnly = Boolean.parseBoolean(audioOnlyString);
        }

        String localAudioCodecString = getIniValue(SECTION_MEDIA, FIELD_LOCAL_AUDIO_CODEC);
        if (localAudioCodecString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_AUDIO_CODEC);
            System.exit(1);
        } else {
            this.localAudioCodec = Integer.parseInt(localAudioCodecString);
            if (this.localAudioCodec <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_AUDIO_CODEC);
                System.exit(1);
            }
        }

        String localAudioSampleRateString = getIniValue(SECTION_MEDIA, FIELD_LOCAL_AUDIO_SAMPLERATE);
        if (localAudioSampleRateString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_AUDIO_SAMPLERATE);
            System.exit(1);
        } else {
            this.localAudioSampleRate = Integer.parseInt(localAudioSampleRateString);
            if (this.localAudioSampleRate <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_AUDIO_SAMPLERATE);
                System.exit(1);
            }
        }

        String localVideoCodecString = getIniValue(SECTION_MEDIA, FIELD_LOCAL_VIDEO_CODEC);
        if (localVideoCodecString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_VIDEO_CODEC);
            System.exit(1);
        } else {
            this.localVideoCodec = Integer.parseInt(localVideoCodecString);
            if (this.localVideoCodec <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_VIDEO_CODEC);
                System.exit(1);
            }
        }

        String localVideoPixelFormatString = getIniValue(SECTION_MEDIA, FIELD_LOCAL_VIDEO_PIXEL_FORMAT);
        if (localVideoPixelFormatString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_VIDEO_PIXEL_FORMAT);
            System.exit(1);
        } else {
            this.localVideoPixelFormat = Integer.parseInt(localVideoPixelFormatString);
            if (this.localVideoPixelFormat < 0) { // 0 : YUV420P
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_LOCAL_VIDEO_PIXEL_FORMAT);
                System.exit(1);
            }
        }

        String localVideoWidthString = getIniValue(SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_WIDTH);
        if (localVideoWidthString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_WIDTH);
            System.exit(1);
        } else {
            this.localVideoWidth = Integer.parseInt(localVideoWidthString);
            if (this.localVideoWidth < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_WIDTH);
                System.exit(1);
            }
        }

        String localVideoHeightString = getIniValue(SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_HEIGHT);
        if (localVideoHeightString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_HEIGHT);
            System.exit(1);
        } else {
            this.localVideoHeight = Integer.parseInt(localVideoHeightString);
            if (this.localVideoHeight < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_LOCAL_VIDEO_HEIGHT);
                System.exit(1);
            }
        }

        String remoteAudioCodecString = getIniValue(SECTION_MEDIA, FIELD_REMOTE_AUDIO_CODEC);
        if (remoteAudioCodecString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_AUDIO_CODEC);
            System.exit(1);
        } else {
            this.remoteAudioCodec = Integer.parseInt(remoteAudioCodecString);
            if (this.remoteAudioCodec <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_AUDIO_CODEC);
                System.exit(1);
            }
        }

        String remoteAudioSampleRateString = getIniValue(SECTION_MEDIA, FIELD_REMOTE_AUDIO_SAMPLERATE);
        if (remoteAudioSampleRateString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_AUDIO_SAMPLERATE);
            System.exit(1);
        } else {
            this.remoteAudioSampleRate = Integer.parseInt(remoteAudioSampleRateString);
            if (this.remoteAudioSampleRate <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_AUDIO_SAMPLERATE);
                System.exit(1);
            }
        }

        String remoteVideoCodecString = getIniValue(SECTION_MEDIA, FIELD_REMOTE_VIDEO_CODEC);
        if (remoteVideoCodecString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_VIDEO_CODEC);
            System.exit(1);
        } else {
            this.remoteVideoCodec = Integer.parseInt(remoteVideoCodecString);
            if (this.remoteVideoCodec <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_VIDEO_CODEC);
                System.exit(1);
            }
        }

        String remoteVideoPixelFormatString = getIniValue(SECTION_MEDIA, FIELD_REMOTE_VIDEO_PIXEL_FORMAT);
        if (remoteVideoPixelFormatString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_VIDEO_PIXEL_FORMAT);
            System.exit(1);
        } else {
            this.remoteVideoPixelFormat = Integer.parseInt(remoteVideoPixelFormatString);
            if (this.remoteVideoPixelFormat < 0) { // 0 : YUV420P
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_REMOTE_VIDEO_PIXEL_FORMAT);
                System.exit(1);
            }
        }

        String remoteVideoWidthString = getIniValue(SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_WIDTH);
        if (remoteVideoWidthString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_WIDTH);
            System.exit(1);
        } else {
            this.remoteVideoWidth = Integer.parseInt(remoteVideoWidthString);
            if (this.remoteVideoWidth < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_WIDTH);
                System.exit(1);
            }
        }

        String remoteVideoHeightString = getIniValue(SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_HEIGHT);
        if (remoteVideoHeightString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_HEIGHT);
            System.exit(1);
        } else {
            this.remoteVideoHeight = Integer.parseInt(remoteVideoHeightString);
            if (this.remoteVideoHeight < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MEDIA, FIELD_MEDIA_REMOTE_VIDEO_HEIGHT);
                System.exit(1);
            }
        }

        logger.debug(CONSTANT_PRINT_SUCCESS_LOG_FORMAT, SECTION_MEDIA);
    }

    /**
     * @fn private void loadMpdConfig()
     * @brief MPD Section 을 로드하는 함수
     */
    private void loadMpdConfig() {
        String enableValidationString = getIniValue(SECTION_MPD, FIELD_ENABLE_VALIDATION);
        if (enableValidationString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_ENABLE_VALIDATION);
            System.exit(1);
        } else {
            this.enableValidation = Boolean.parseBoolean(enableValidationString);
        }

        this.representationIdFormat = getIniValue(SECTION_MPD, FIELD_REPRESENTATION_ID_FORMAT);
        if (representationIdFormat == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_REPRESENTATION_ID_FORMAT);
            System.exit(1);
        }

        this.chunkNumberFormat = getIniValue(SECTION_MPD, FIELD_CHUNK_NUMBER_FORMAT);
        if (chunkNumberFormat == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_CHUNK_NUMBER_FORMAT);
            System.exit(1);
        }

        this.segmentNumberFormat = getIniValue(SECTION_MPD, FIELD_SEGMENT_NUMBER_FORMAT);
        if (segmentNumberFormat == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_SEGMENT_NUMBER_FORMAT);
            System.exit(1);
        }

        this.validationXsdPath = getIniValue(SECTION_MPD, FIELD_VALIDATION_XSD_PATH);
        if (validationXsdPath == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_VALIDATION_XSD_PATH);
            System.exit(1);
        }

        String segmentDurationString = getIniValue(SECTION_MPD, FIELD_SEGMENT_DURATION);
        if (segmentDurationString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_SEGMENT_DURATION);
            System.exit(1);
        } else {
            this.segmentDuration = Double.parseDouble(segmentDurationString);
            if (this.segmentDuration <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_SEGMENT_DURATION);
                System.exit(1);
            }
        }

        String localSegmentDurationOffsetString = getIniValue(SECTION_MPD, FIELD_LOCAL_TIME_OFFSET);
        if (localSegmentDurationOffsetString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_LOCAL_TIME_OFFSET);
            System.exit(1);
        } else {
            this.localTimeOffset = Double.parseDouble(localSegmentDurationOffsetString);
            if (this.localTimeOffset < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_LOCAL_TIME_OFFSET);
                System.exit(1);
            }
        }

        String remoteSegmentDurationOffsetString = getIniValue(SECTION_MPD, FIELD_REMOTE_TIME_OFFSET);
        if (remoteSegmentDurationOffsetString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_REMOTE_TIME_OFFSET);
            System.exit(1);
        } else {
            this.remoteTimeOffset = Double.parseDouble(remoteSegmentDurationOffsetString);
            if (this.remoteTimeOffset < 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_REMOTE_TIME_OFFSET);
                System.exit(1);
            }
        }


        String windowSizeString = getIniValue(SECTION_MPD, FIELD_WINDOW_SIZE);
        if (windowSizeString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_WINDOW_SIZE);
            System.exit(1);
        } else {
            this.windowSize = Integer.parseInt(windowSizeString);
            if (this.windowSize <= 0) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_MPD, FIELD_WINDOW_SIZE);
                System.exit(1);
            }
        }
    }

    /**
     * @fn private void loadRtmpConfig()
     * @brief RTMP Section 을 로드하는 함수
     */
    private void loadRtmpConfig() {
        this.rtmpServerIp = getIniValue(SECTION_RTMP, FIELD_RTMP_PUBLISH_IP);
        if (this.rtmpServerIp == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_PUBLISH_IP);
            System.exit(1);
        }

        String rtmpServerPortString = getIniValue(SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
        if (rtmpServerPortString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
            System.exit(1);
        } else {
            this.rtmpServerPort = Integer.parseInt(rtmpServerPortString);
            if (this.rtmpServerPort <= 0 || this.rtmpServerPort > 65535) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
                System.exit(1);
            }
        }

        String logLevelString = getIniValue(SECTION_RTMP, FIELD_RTMP_LOG_LEVEL);
        if (logLevelString == null) {
            logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_LOG_LEVEL);
            System.exit(1);
        } else {
            this.rtmpLogLevel = Integer.parseInt(logLevelString);
            if (this.rtmpLogLevel < -8 || this.rtmpLogLevel > 64) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_LOG_LEVEL);
                System.exit(1);
            }

            if (!AvUtilLogLevelChecker.checkLogLevel(rtmpLogLevel)) {
                logger.error(CONSTANT_PRINT_FAIL_LOG_FORMAT_1, SECTION_RTMP, FIELD_RTMP_LOG_LEVEL);
                System.exit(1);
            }
        }

        logger.debug(CONSTANT_PRINT_SUCCESS_LOG_FORMAT, SECTION_RTMP);
    }

    ////////////////////////////////////////////////////////////////////////////////

    /**
     * @fn private String getIniValue(String section, String key)
     * @brief INI 파일에서 지정한 section 과 key 에 해당하는 value 를 가져오는 함수
     * @param section Section
     * @param key Key
     * @return 성공 시 value, 실패 시 null 반환
     */
    private String getIniValue(String section, String key) {
        String value = ini.get(section,key);
        if (value == null) {
            logger.warn("[ {} ] \" {} \" is null.", section, key);
            System.exit(1);
            return null;
        }

        value = value.trim();
        logger.debug("\tGet Config [{}] > [{}] : [{}]", section, key, value);
        return value;
    }

    /**
     * @fn public void setIniValue(String section, String key, String value)
     * @brief INI 파일에 새로운 value 를 저장하는 함수
     * @param section Section
     * @param key Key
     * @param value Value
     */
    public void setIniValue(String section, String key, String value) {
        try {
            ini.put(section, key, value);
            ini.store();

            logger.debug("\tSet Config [{}] > [{}] : [{}]", section, key, value);
        } catch (IOException e) {
            logger.warn("Fail to set the config. (section={}, field={}, value={})", section, key, value);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

}
