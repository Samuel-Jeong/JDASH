package config;

import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @class public class UserConfig
 * @brief UserConfig Class
 */
public class ConfigManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private Ini ini = null;
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
    public static final String FIELD_SERVICE_NAME = "SERVICE_NAME";
    public static final String FIELD_LONG_SESSION_LIMIT_TIME = "LONG_SESSION_LIMIT_TIME";
    public static final String FIELD_ENABLE_CLIENT = "ENABLE_CLIENT";
    public static final String FIELD_THREAD_COUNT = "THREAD_COUNT";
    public static final String FIELD_SEND_BUF_SIZE = "SEND_BUF_SIZE";
    public static final String FIELD_RECV_BUF_SIZE = "RECV_BUF_SIZE";

    // SERVER
    public static final String FIELD_STREAMING = "STREAMING";
    public static final String FIELD_ENABLE_PRELOAD_WITH_DASH = "ENABLE_PRELOAD_WITH_DASH";
    public static final String FIELD_HTTP_LISTEN_IP = "HTTP_LISTEN_IP";
    public static final String FIELD_HTTP_LISTEN_PORT = "HTTP_LISTEN_PORT";
    public static final String FIELD_PREPROCESS_LISTEN_IP = "PREPROCESS_LISTEN_IP";
    public static final String FIELD_PREPROCESS_LISTEN_PORT = "PREPROCESS_LISTEN_PORT";

    // CLIENT
    public static final String FIELD_ENABLE_GUI = "ENABLE_GUI";
    public static final String FIELD_CAMERA_PATH = "CAMERA_PATH";
    public static final String FIELD_HTTP_TARGET_IP = "HTTP_TARGET_IP";
    public static final String FIELD_HTTP_TARGET_PORT = "HTTP_TARGET_PORT";
    public static final String FIELD_PREPROCESS_INIT_IDLE_TIME = "PREPROCESS_INIT_IDLE_TIME";
    public static final String FIELD_PREPROCESS_TARGET_IP = "PREPROCESS_TARGET_IP";
    public static final String FIELD_PREPROCESS_TARGET_PORT = "PREPROCESS_TARGET_PORT";

    // MEDIA
    public static final String FIELD_MEDIA_BASE_PATH = "MEDIA_BASE_PATH";
    public static final String FIELD_MEDIA_LIST_PATH = "MEDIA_LIST_PATH";
    public static final String FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED = "CLEAR_DASH_DATA_IF_SESSION_CLOSED";
    public static final String FIELD_CHUNK_FILE_DELETION_INTERVAL_SEC = "CHUNK_FILE_DELETION_INTERVAL_SEC";
    public static final String FIELD_AUDIO_ONLY = "AUDIO_ONLY";

    // MPD
    public static final String FIELD_ENABLE_VALIDATION = "ENABLE_VALIDATION";
    public static final String FIELD_REPRESENTATION_ID_FORMAT = "REPRESENTATION_ID_FORMAT";
    public static final String FIELD_CHUNK_NUMBER_FORMAT = "CHUNK_NUMBER_FORMAT";
    public static final String FIELD_VALIDATION_XSD_PATH = "VALIDATION_XSD_PATH";
    public static final String FIELD_SEGMENT_DURATION = "SEGMENT_DURATION";
    public static final String FIELD_WINDOW_SIZE = "WINDOW_SIZE";

    // RTMP
    public static final String FIELD_RTMP_PUBLISH_IP = "RTMP_PUBLISH_IP";
    public static final String FIELD_RTMP_PUBLISH_PORT = "RTMP_PUBLISH_PORT";
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // VARIABLES
    // COMMON
    private String id = null;
    private String serviceName = null;
    private long localSessionLimitTime = 0; // ms
    private boolean enableClient = false;
    private int threadCount = 0;
    private int sendBufSize = 0;
    private int recvBufSize = 0;

    // SERVER
    private String streaming = null;
    private boolean enablePreloadWithDash = false;
    private String httpListenIp = null;
    private int httpListenPort = 0;
    private String preprocessListenIp = null;
    private int preprocessListenPort = 0;

    // CLIENT
    private boolean enableGui = false;
    private String cameraPath = null;
    private long preprocessInitIdleTime = 0; // ms
    private String preprocessTargetIp = null;
    private int preprocessTargetPort = 0;
    private String httpTargetIp = null;
    private int httpTargetPort = 0;

    // MEDIA
    private String mediaBasePath = null;
    private String mediaListPath = null;
    private boolean clearDashDataIfSessionClosed = true;
    private int chunkFileDeletionIntervalSeconds = 0;
    private boolean audioOnly = false;

    // MPD
    private boolean enableValidation = false;
    private String representationIdFormat = null;
    private String chunkNumberFormat = null;
    private String validationXsdPath = null;
    private double segmentDuration = 0.0d;
    private int windowSize = 0;

    // RTMP
    private String rtmpPublishIp = null;
    private int rtmpPublishPort = 0;
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
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_ID);
            System.exit(1);
        }

        this.serviceName = getIniValue(SECTION_COMMON, FIELD_SERVICE_NAME);
        if (serviceName == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_SERVICE_NAME);
            System.exit(1);
        }

        this.localSessionLimitTime = Long.parseLong(getIniValue(SECTION_COMMON, FIELD_LONG_SESSION_LIMIT_TIME));
        if (this.localSessionLimitTime < 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_COMMON, FIELD_LONG_SESSION_LIMIT_TIME, localSessionLimitTime);
            System.exit(1);
        }

        String enableClientString = getIniValue(SECTION_COMMON, FIELD_ENABLE_CLIENT);
        if (enableClientString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_ENABLE_CLIENT);
            System.exit(1);
        } else {
            this.enableClient = Boolean.parseBoolean(enableClientString);
        }

        this.threadCount = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_THREAD_COUNT));
        if (this.threadCount <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_COMMON, FIELD_THREAD_COUNT, threadCount);
            System.exit(1);
        }

        this.sendBufSize = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_SEND_BUF_SIZE));
        if (this.sendBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_COMMON, FIELD_SEND_BUF_SIZE, sendBufSize);
            System.exit(1);
        }

        this.recvBufSize = Integer.parseInt(getIniValue(SECTION_COMMON, FIELD_RECV_BUF_SIZE));
        if (this.recvBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_COMMON, FIELD_RECV_BUF_SIZE, recvBufSize);
            System.exit(1);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_COMMON);
    }

    /**
     * @fn private void loadServerConfig()
     * @brief SERVER Section 을 로드하는 함수
     */
    private void loadServerConfig() {
        this.streaming = getIniValue(SECTION_SERVER, FIELD_STREAMING);
        if (streaming == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_STREAMING);
            System.exit(1);
        }

        String enablePreloadWithDashString = getIniValue(SECTION_SERVER, FIELD_ENABLE_PRELOAD_WITH_DASH);
        if (enablePreloadWithDashString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_ENABLE_PRELOAD_WITH_DASH);
            System.exit(1);
        } else {
            this.enablePreloadWithDash = Boolean.parseBoolean(enablePreloadWithDashString);
        }

        this.httpListenIp = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_IP);
        if (this.httpListenIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_HTTP_LISTEN_IP);
            System.exit(1);
        }

        String httpListenPortString = getIniValue(SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
        if (httpListenPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
            System.exit(1);
        } else {
            this.httpListenPort = Integer.parseInt(httpListenPortString);
            if (this.httpListenPort <= 0 || this.httpListenPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_HTTP_LISTEN_PORT);
                System.exit(1);
            }
        }

        this.preprocessListenIp = getIniValue(SECTION_SERVER, FIELD_PREPROCESS_LISTEN_IP);
        if (this.preprocessListenIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_PREPROCESS_LISTEN_IP);
            System.exit(1);
        }

        String preprocessListenPort = getIniValue(SECTION_SERVER, FIELD_PREPROCESS_LISTEN_PORT);
        if (preprocessListenPort == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_PREPROCESS_LISTEN_PORT);
            System.exit(1);
        } else {
            this.preprocessListenPort = Integer.parseInt(preprocessListenPort);
            if (this.preprocessListenPort <= 0 || this.preprocessListenPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_SERVER, FIELD_PREPROCESS_TARGET_PORT);
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
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_CAMERA_PATH);
            System.exit(1);
        }

        String enableGuiString = getIniValue(SECTION_CLIENT, FIELD_ENABLE_GUI);
        if (enableGuiString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_ENABLE_GUI);
            System.exit(1);
        } else {
            this.enableGui = Boolean.parseBoolean(enableGuiString);
        }

        this.httpTargetIp = getIniValue(SECTION_CLIENT, FIELD_HTTP_TARGET_IP);
        if (this.httpTargetIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_HTTP_TARGET_IP);
            System.exit(1);
        }

        String httpTargetPortString = getIniValue(SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
        if (httpTargetPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
            System.exit(1);
        } else {
            this.httpTargetPort = Integer.parseInt(httpTargetPortString);
            if (this.httpTargetPort <= 0 || this.httpTargetPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_HTTP_TARGET_PORT);
                System.exit(1);
            }
        }

        this.preprocessInitIdleTime = Long.parseLong(getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_INIT_IDLE_TIME));
        if (this.preprocessInitIdleTime < 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_CLIENT, FIELD_PREPROCESS_INIT_IDLE_TIME, preprocessInitIdleTime);
            System.exit(1);
        }

        this.preprocessTargetIp = getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_TARGET_IP);
        if (this.preprocessTargetIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_PREPROCESS_TARGET_IP);
            System.exit(1);
        }

        String preprocessTargetPort = getIniValue(SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
        if (preprocessTargetPort == null) {
            logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
            System.exit(1);
        } else {
            this.preprocessTargetPort = Integer.parseInt(preprocessTargetPort);
            if (this.preprocessTargetPort <= 0 || this.preprocessTargetPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_CLIENT, FIELD_PREPROCESS_TARGET_PORT);
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
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_MEDIA_BASE_PATH);
            System.exit(1);
        }

        this.mediaListPath = getIniValue(SECTION_MEDIA, FIELD_MEDIA_LIST_PATH);
        if (mediaListPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_MEDIA_LIST_PATH);
            System.exit(1);
        }

        String clearDashDataIfSessionClosedString = getIniValue(SECTION_MEDIA, FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED);
        if (clearDashDataIfSessionClosedString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_CLEAR_DASH_DATA_IF_SESSION_CLOSED);
            System.exit(1);
        } else {
            this.clearDashDataIfSessionClosed = Boolean.parseBoolean(clearDashDataIfSessionClosedString);
        }

        String chunkFileDeletionIntervalSecondsString = getIniValue(SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_INTERVAL_SEC);
        if (chunkFileDeletionIntervalSecondsString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_INTERVAL_SEC);
            System.exit(1);
        } else {
            this.chunkFileDeletionIntervalSeconds = Integer.parseInt(chunkFileDeletionIntervalSecondsString);
            if (this.chunkFileDeletionIntervalSeconds <= 0) {
                logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_CHUNK_FILE_DELETION_INTERVAL_SEC);
                System.exit(1);
            }
        }

        String audioOnlyString = getIniValue(SECTION_MEDIA, FIELD_AUDIO_ONLY);
        if (audioOnlyString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_AUDIO_ONLY);
            System.exit(1);
        } else {
            this.audioOnly = Boolean.parseBoolean(audioOnlyString);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_MEDIA);
    }

    /**
     * @fn private void loadMpdConfig()
     * @brief MPD Section 을 로드하는 함수
     */
    private void loadMpdConfig() {
        String enableValidationString = getIniValue(SECTION_MPD, FIELD_ENABLE_VALIDATION);
        if (enableValidationString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_ENABLE_VALIDATION);
            System.exit(1);
        } else {
            this.enableValidation = Boolean.parseBoolean(enableValidationString);
        }

        this.representationIdFormat = getIniValue(SECTION_MPD, FIELD_REPRESENTATION_ID_FORMAT);
        if (representationIdFormat == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_REPRESENTATION_ID_FORMAT);
            System.exit(1);
        }

        this.chunkNumberFormat = getIniValue(SECTION_MPD, FIELD_CHUNK_NUMBER_FORMAT);
        if (chunkNumberFormat == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_CHUNK_NUMBER_FORMAT);
            System.exit(1);
        }

        this.validationXsdPath = getIniValue(SECTION_MPD, FIELD_VALIDATION_XSD_PATH);
        if (validationXsdPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_VALIDATION_XSD_PATH);
            System.exit(1);
        }

        String segmentDurationString = getIniValue(SECTION_MPD, FIELD_SEGMENT_DURATION);
        if (segmentDurationString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_SEGMENT_DURATION);
            System.exit(1);
        } else {
            this.segmentDuration = Double.parseDouble(segmentDurationString);
            if (this.segmentDuration <= 0) {
                logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_SEGMENT_DURATION);
                System.exit(1);
            }
        }

        String windowSizeSring = getIniValue(SECTION_MPD, FIELD_WINDOW_SIZE);
        if (windowSizeSring == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_WINDOW_SIZE);
            System.exit(1);
        } else {
            this.windowSize = Integer.parseInt(windowSizeSring);
            if (this.windowSize <= 0) {
                logger.error("Fail to load [{}-{}].", SECTION_MPD, FIELD_WINDOW_SIZE);
                System.exit(1);
            }
        }
    }

    /**
     * @fn private void loadRtmpConfig()
     * @brief RTMP Section 을 로드하는 함수
     */
    private void loadRtmpConfig() {
        this.rtmpPublishIp = getIniValue(SECTION_RTMP, FIELD_RTMP_PUBLISH_IP);
        if (this.rtmpPublishIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_PUBLISH_IP);
            System.exit(1);
        }

        String rtmpPublishPortString = getIniValue(SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
        if (rtmpPublishPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
            System.exit(1);
        } else {
            this.rtmpPublishPort = Integer.parseInt(rtmpPublishPortString);
            if (this.rtmpPublishPort <= 0 || this.rtmpPublishPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_PUBLISH_PORT);
                System.exit(1);
            }
        }

        logger.debug("Load [{}] config...(OK)", SECTION_RTMP);
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
    public String getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public long getLocalSessionLimitTime() {
        return localSessionLimitTime;
    }

    public boolean isEnableClient() {
        return enableClient;
    }

    public String getStreaming() {
        return streaming;
    }

    public String getMediaBasePath() {
        return mediaBasePath;
    }

    public String getMediaListPath() {
        return mediaListPath;
    }

    public String getCameraPath() {
        return cameraPath;
    }

    public String getValidationXsdPath() {
        return validationXsdPath;
    }

    public boolean isEnableGui() {
        return enableGui;
    }

    public boolean isClearDashDataIfSessionClosed() {
        return clearDashDataIfSessionClosed;
    }

    public double getSegmentDuration() {
        return segmentDuration;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public boolean isAudioOnly() {
        return audioOnly;
    }

    public String getPreprocessListenIp() {
        return preprocessListenIp;
    }

    public int getPreprocessListenPort() {
        return preprocessListenPort;
    }

    public String getPreprocessTargetIp() {
        return preprocessTargetIp;
    }

    public int getPreprocessTargetPort() {
        return preprocessTargetPort;
    }

    public long getPreprocessInitIdleTime() {
        return preprocessInitIdleTime;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getSendBufSize() {
        return sendBufSize;
    }

    public int getRecvBufSize() {
        return recvBufSize;
    }

    public String getHttpListenIp() {
        return httpListenIp;
    }

    public int getHttpListenPort() {
        return httpListenPort;
    }

    public String getHttpTargetIp() {
        return httpTargetIp;
    }

    public int getHttpTargetPort() {
        return httpTargetPort;
    }

    public String getRtmpPublishIp() {
        return rtmpPublishIp;
    }

    public int getRtmpPublishPort() {
        return rtmpPublishPort;
    }

    public boolean isEnablePreloadWithDash() {
        return enablePreloadWithDash;
    }

    public String getRepresentationIdFormat() {
        return representationIdFormat;
    }

    public String getChunkNumberFormat() {
        return chunkNumberFormat;
    }

    public boolean isEnableValidation() {
        return enableValidation;
    }

    public int getChunkFileDeletionIntervalSeconds() {
        return chunkFileDeletionIntervalSeconds;
    }
}
