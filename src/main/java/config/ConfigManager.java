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
    public static final String SECTION_MEDIA = "MEDIA"; // MEDIA Section 이름
    public static final String SECTION_LIVE = "LIVE"; // LIVE Section 이름
    public static final String SECTION_NETWORK = "NETWORK"; // NETWORK Section 이름
    public static final String SECTION_RTMP = "RTMP"; // RTMP Section 이름
    public static final String SECTION_SCRIPT = "SCRIPT"; // SCRIPT Section 이름
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // Field String
    // COMMON
    public static final String FIELD_ENABLE_CLIENT = "ENABLE_CLIENT";
    public static final String FIELD_SERVICE_NAME = "SERVICE_NAME";
    public static final String FIELD_LONG_SESSION_LIMIT_TIME = "LONG_SESSION_LIMIT_TIME";

    // MEDIA
    public static final String FIELD_MEDIA_BASE_PATH = "MEDIA_BASE_PATH";
    public static final String FIELD_MEDIA_LIST_PATH = "MEDIA_LIST_PATH";
    public static final String FIELD_CAMERA_PATH = "CAMERA_PATH";
    public static final String FIELD_VALIDATION_XSD_PATH = "VALIDATION_XSD_PATH";

    // LIVE
    public static final String FIELD_FFMPEG_PATH = "FFMPEG_PATH";
    public static final String FIELD_FFPROBE_PATH = "FFPROBE_PATH";
    public static final String FIELD_PREPROCESS_LISTEN_IP = "PREPROCESS_LISTEN_IP";
    public static final String FIELD_PREPROCESS_LISTEN_PORT = "PREPROCESS_LISTEN_PORT";
    public static final String FIELD_PREPROCESS_TARGET_IP = "PREPROCESS_TARGET_IP";
    public static final String FIELD_PREPROCESS_TARGET_PORT = "PREPROCESS_TARGET_PORT";

    // NETWORK
    public static final String FIELD_THREAD_COUNT = "THREAD_COUNT";
    public static final String FIELD_SEND_BUF_SIZE = "SEND_BUF_SIZE";
    public static final String FIELD_RECV_BUF_SIZE = "RECV_BUF_SIZE";
    public static final String FIELD_HTTP_LISTEN_IP = "HTTP_LISTEN_IP";
    public static final String FIELD_HTTP_LISTEN_PORT = "HTTP_LISTEN_PORT";

    // RTMP
    public static final String FIELD_RTMP_PUBLISH_IP = "RTMP_PUBLISH_IP";
    public static final String FIELD_RTMP_PUBLISH_PORT = "RTMP_PUBLISH_PORT";

    // SCRIPT
    public static final String FIELD_SCRIPT_PATH = "PATH";
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // VARIABLES
    // COMMON
    private boolean enableClient = false;
    private String serviceName = null;
    private long localSessionLimitTime = 0; // ms

    // MEDIA
    private String mediaBasePath = null;
    private String mediaListPath = null;
    private String cameraPath = null;
    private String validationXsdPath = null;

    // LIVE
    private String ffmpegPath = null;
    private String ffprobePath = null;
    private String preprocessListenIp = null;
    private int preprocessListenPort = 0;
    private String preprocessTargetIp = null;
    private int preprocessTargetPort = 0;

    // NETWORK
    private int threadCount = 0;
    private int sendBufSize = 0;
    private int recvBufSize = 0;
    private String httpListenIp = null;
    private int httpListenPort = 0;

    // RTMP
    private String rtmpPublishIp = null;
    private int rtmpPublishPort = 0;

    // SCRIPT
    private String scriptPath = null;
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
            loadMediaConfig();
            loadLiveConfig();
            loadNetworkConfig();
            loadRtmpConfig();
            loadScriptConfig();

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
        String enableClientString = getIniValue(SECTION_COMMON, FIELD_ENABLE_CLIENT);
        if (enableClientString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_ENABLE_CLIENT);
            System.exit(1);
        } else {
            this.enableClient = Boolean.parseBoolean(enableClientString);
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

        logger.debug("Load [{}] config...(OK)", SECTION_COMMON);
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

        this.cameraPath = getIniValue(SECTION_MEDIA, FIELD_CAMERA_PATH);
        if (cameraPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_CAMERA_PATH);
            System.exit(1);
        }

        this.validationXsdPath = getIniValue(SECTION_MEDIA, FIELD_VALIDATION_XSD_PATH);
        if (validationXsdPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_MEDIA, FIELD_VALIDATION_XSD_PATH);
            System.exit(1);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_MEDIA);
    }

    /**
     * @fn private void loadLiveConfig()
     * @brief LIVE Section 을 로드하는 함수
     */
    private void loadLiveConfig() {
        this.ffmpegPath = getIniValue(SECTION_LIVE, FIELD_FFMPEG_PATH);
        if (this.ffmpegPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_FFMPEG_PATH);
            System.exit(1);
        }

        this.ffprobePath = getIniValue(SECTION_LIVE, FIELD_FFPROBE_PATH);
        if (this.ffprobePath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_FFPROBE_PATH);
            System.exit(1);
        }

        this.preprocessListenIp = getIniValue(SECTION_LIVE, FIELD_PREPROCESS_LISTEN_IP);
        if (this.preprocessListenIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_LISTEN_IP);
            System.exit(1);
        }

        String preprocessListenPort = getIniValue(SECTION_LIVE, FIELD_PREPROCESS_LISTEN_PORT);
        if (preprocessListenPort == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_LISTEN_PORT);
            System.exit(1);
        } else {
            this.preprocessListenPort = Integer.parseInt(preprocessListenPort);
            if (this.preprocessListenPort <= 0 || this.preprocessListenPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_TARGET_PORT);
                System.exit(1);
            }
        }

        this.preprocessTargetIp = getIniValue(SECTION_LIVE, FIELD_PREPROCESS_TARGET_IP);
        if (this.preprocessTargetIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_TARGET_IP);
            System.exit(1);
        }

        String preprocessTargetPort = getIniValue(SECTION_LIVE, FIELD_PREPROCESS_TARGET_PORT);
        if (preprocessTargetPort == null) {
            logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_TARGET_PORT);
            System.exit(1);
        } else {
            this.preprocessTargetPort = Integer.parseInt(preprocessTargetPort);
            if (this.preprocessTargetPort <= 0 || this.preprocessTargetPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_LIVE, FIELD_PREPROCESS_TARGET_PORT);
                System.exit(1);
            }
        }

        logger.debug("Load [{}] config...(OK)", SECTION_LIVE);
    }

    /**
     * @fn private void loadNetworkConfig()
     * @brief NETWORK Section 을 로드하는 함수
     */
    private void loadNetworkConfig() {
        this.threadCount = Integer.parseInt(getIniValue(SECTION_NETWORK, FIELD_THREAD_COUNT));
        if (this.threadCount <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_NETWORK, FIELD_THREAD_COUNT, threadCount);
            System.exit(1);
        }

        this.sendBufSize = Integer.parseInt(getIniValue(SECTION_NETWORK, FIELD_SEND_BUF_SIZE));
        if (this.sendBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_NETWORK, FIELD_SEND_BUF_SIZE, sendBufSize);
            System.exit(1);
        }

        this.recvBufSize = Integer.parseInt(getIniValue(SECTION_NETWORK, FIELD_RECV_BUF_SIZE));
        if (this.recvBufSize <= 0) {
            logger.error("Fail to load [{}-{}]. ({})", SECTION_NETWORK, FIELD_RECV_BUF_SIZE, recvBufSize);
            System.exit(1);
        }

        this.httpListenIp = getIniValue(SECTION_NETWORK, FIELD_HTTP_LISTEN_IP);
        if (this.httpListenIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_NETWORK, FIELD_HTTP_LISTEN_IP);
            System.exit(1);
        }

        String httpListenPortString = getIniValue(SECTION_NETWORK, FIELD_HTTP_LISTEN_PORT);
        if (httpListenPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_NETWORK, FIELD_HTTP_LISTEN_PORT);
            System.exit(1);
        } else {
            this.httpListenPort = Integer.parseInt(httpListenPortString);
            if (this.httpListenPort <= 0 || this.httpListenPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_NETWORK, FIELD_HTTP_LISTEN_PORT);
                System.exit(1);
            }
        }

        logger.debug("Load [{}] config...(OK)", SECTION_NETWORK);
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

    /**
     * @fn private void loadScriptConfig()
     * @brief SCRIPT Section 을 로드하는 함수
     */
    private void loadScriptConfig() {
        this.scriptPath = getIniValue(SECTION_SCRIPT, FIELD_SCRIPT_PATH);
        if (this.scriptPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_SCRIPT, FIELD_SCRIPT_PATH);
            System.exit(1);
        }

        logger.debug("Load [{}] config...(OK)", SECTION_SCRIPT);
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
    public boolean isEnableClient() {
        return enableClient;
    }

    public String getServiceName() {
        return serviceName;
    }

    public long getLocalSessionLimitTime() {
        return localSessionLimitTime;
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

    public String getMediaBasePath() {
        return mediaBasePath;
    }

    public String getMediaListPath() {
        return mediaListPath;
    }

    public String getHttpListenIp() {
        return httpListenIp;
    }

    public int getHttpListenPort() {
        return httpListenPort;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public String getCameraPath() {
        return cameraPath;
    }

    public String getRtmpPublishIp() {
        return rtmpPublishIp;
    }

    public int getRtmpPublishPort() {
        return rtmpPublishPort;
    }

    public String getValidationXsdPath() {
        return validationXsdPath;
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

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }
}
