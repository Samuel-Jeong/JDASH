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

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private Ini ini = null;

    // Section String
    public static final String SECTION_COMMON = "COMMON"; // COMMON Section 이름
    public static final String SECTION_MEDIA = "MEDIA"; // MEDIA Section 이름
    public static final String SECTION_NETWORK = "NETWORK"; // NETWORK Section 이름
    public static final String SECTION_REGISTER = "REGISTER"; // REGISTER Section 이름
    public static final String SECTION_RTMP = "RTMP"; // RTMP Section 이름
    public static final String SECTION_SCRIPT = "SCRIPT"; // SCRIPT Section 이름

    // Field String
    public static final String FIELD_ENABLE_CLIENT = "ENABLE_CLIENT";
    public static final String FIELD_SERVICE_NAME = "SERVICE_NAME";
    public static final String FIELD_LONG_SESSION_LIMIT_TIME = "LONG_SESSION_LIMIT_TIME";
    public static final String FIELD_ICON_ROOT_PATH = "ICON_ROOT_PATH";
    public static final String FIELD_PLAYLIST_ROOT_PATH = "PLAYLIST_ROOT_PATH";
    public static final String FIELD_PLAYLIST_SIZE = "PLAYLIST_SIZE";
    public static final String FIELD_URI_LIMIT = "URI_LIMIT";

    public static final String FIELD_MEDIA_BASE_PATH = "MEDIA_BASE_PATH";
    public static final String FIELD_MEDIA_LIST_PATH = "MEDIA_LIST_PATH";
    public static final String FIELD_CAMERA_PATH = "CAMERA_PATH";

    public static final String FIELD_THREAD_COUNT = "THREAD_COUNT";
    public static final String FIELD_SEND_BUF_SIZE = "SEND_BUF_SIZE";
    public static final String FIELD_RECV_BUF_SIZE = "RECV_BUF_SIZE";
    public static final String FIELD_HTTP_LISTEN_IP = "HTTP_LISTEN_IP";
    public static final String FIELD_HTTP_LISTEN_PORT = "HTTP_LISTEN_PORT";

    public static final String FIELD_MAGIC_COOKIE = "MAGIC_COOKIE";
    public static final String FIELD_REGISTER_LISTEN_IP = "REGISTER_LISTEN_IP";
    public static final String FIELD_REGISTER_LISTEN_PORT = "REGISTER_LISTEN_PORT";
    public static final String FIELD_REGISTER_LOCAL_IP = "REGISTER_LOCAL_IP";
    public static final String FIELD_REGISTER_LOCAL_PORT = "REGISTER_LOCAL_PORT";
    public static final String FIELD_REGISTER_TARGET_IP = "REGISTER_TARGET_IP";
    public static final String FIELD_REGISTER_TARGET_PORT = "REGISTER_TARGET_PORT";

    public static final String FIELD_RTMP_IP = "RTMP_IP";
    public static final String FIELD_RTMP_PORT = "RTMP_PORT";

    public static final String FIELD_SCRIPT_PATH = "PATH";

    // COMMON
    private boolean enableClient = false;
    private String serviceName = null;
    private long localSessionLimitTime = 0; // ms
    private String iconRootPath = null;
    private String playlistRootPath = null;
    private int playlistSize = 0;
    private int uriLimit = 0;

    // MEDIA
    private String mediaBasePath = null;
    private String mediaListPath = null;
    private String cameraPath = null;

    // NETWORK
    private int threadCount = 0;
    private int sendBufSize = 0;
    private int recvBufSize = 0;
    private String httpListenIp = null;
    private int httpListenPort = 0;

    // REGISTER
    private String magicCookie = null;
    private String registerListenIp = null;
    private int registerListenPort = 0;
    private String registerLocalIp = null;
    private int registerLocalPort = 0;
    private String registerTargetIp = null;
    private int registerTargetPort = 0;

    // RTMP
    private String rtmpIp = null;
    private int rtmpPort = 0;

    // SCRIPT
    private String scriptPath = null;

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
            loadNetworkConfig();
            loadRegisterConfig();
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

        this.iconRootPath = getIniValue(SECTION_COMMON, FIELD_ICON_ROOT_PATH);
        if (iconRootPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_ICON_ROOT_PATH);
            System.exit(1);
        }

        this.playlistRootPath = getIniValue(SECTION_COMMON, FIELD_PLAYLIST_ROOT_PATH);
        if (playlistRootPath == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_PLAYLIST_ROOT_PATH);
            System.exit(1);
        }

        String playlistSizeString = getIniValue(SECTION_COMMON, FIELD_PLAYLIST_SIZE);
        if (playlistSizeString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_PLAYLIST_SIZE);
            System.exit(1);
        } else {
            this.playlistSize = Integer.parseInt(playlistSizeString);
            if (this.playlistSize <= 0) {
                this.playlistSize = 5; // default
            }
        }

        String uriLimitString = getIniValue(SECTION_COMMON, FIELD_URI_LIMIT);
        if (uriLimitString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_COMMON, FIELD_URI_LIMIT);
            System.exit(1);
        } else {
            this.uriLimit = Integer.parseInt(uriLimitString);
            if (this.uriLimit <= 0) {
                this.uriLimit = 300; // default
            }
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

        logger.debug("Load [{}] config...(OK)", SECTION_MEDIA);
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
     * @fn private void loadRegisterConfig()
     * @brief REGISTER Section 을 로드하는 함수
     */
    private void loadRegisterConfig() {
        this.magicCookie = getIniValue(SECTION_REGISTER, FIELD_MAGIC_COOKIE);
        if (this.magicCookie == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_MAGIC_COOKIE);
            System.exit(1);
        }

        this.registerListenIp = getIniValue(SECTION_REGISTER, FIELD_REGISTER_LISTEN_IP);
        if (this.registerListenIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LISTEN_IP);
            System.exit(1);
        }

        String registerListenPortString = getIniValue(SECTION_REGISTER, FIELD_REGISTER_LISTEN_PORT);
        if (registerListenPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LISTEN_PORT);
            System.exit(1);
        } else {
            this.registerListenPort = Integer.parseInt(registerListenPortString);
            if (this.registerListenPort <= 0 || this.registerListenPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LISTEN_PORT);
                System.exit(1);
            }
        }

        this.registerLocalIp = getIniValue(SECTION_REGISTER, FIELD_REGISTER_LOCAL_IP);
        if (this.registerLocalIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LOCAL_IP);
            System.exit(1);
        }

        String registerLocalPortString = getIniValue(SECTION_REGISTER, FIELD_REGISTER_LOCAL_PORT);
        if (registerLocalPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LOCAL_PORT);
            System.exit(1);
        } else {
            this.registerLocalPort = Integer.parseInt(registerLocalPortString);
            if (this.registerLocalPort <= 0 || this.registerLocalPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_LOCAL_PORT);
                System.exit(1);
            }
        }

        this.registerTargetIp = getIniValue(SECTION_REGISTER, FIELD_REGISTER_TARGET_IP);
        if (this.registerTargetIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_TARGET_IP);
            System.exit(1);
        }

        String registerTargetPortString = getIniValue(SECTION_REGISTER, FIELD_REGISTER_TARGET_PORT);
        if (registerTargetPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_TARGET_PORT);
            System.exit(1);
        } else {
            this.registerTargetPort = Integer.parseInt(registerTargetPortString);
            if (this.registerTargetPort <= 0 || this.registerTargetPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_REGISTER, FIELD_REGISTER_TARGET_PORT);
                System.exit(1);
            }
        }

        logger.debug("Load [{}] config...(OK)", SECTION_REGISTER);
    }

    /**
     * @fn private void loadRtmpConfig()
     * @brief RTMP Section 을 로드하는 함수
     */
    private void loadRtmpConfig() {
        this.rtmpIp = getIniValue(SECTION_RTMP, FIELD_RTMP_IP);
        if (this.rtmpIp == null) {
            logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_IP);
            System.exit(1);
        }

        String rtmpPortString = getIniValue(SECTION_RTMP, FIELD_RTMP_PORT);
        if (rtmpPortString == null) {
            logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_PORT);
            System.exit(1);
        } else {
            this.rtmpPort = Integer.parseInt(rtmpPortString);
            if (this.rtmpPort <= 0 || this.rtmpPort > 65535) {
                logger.error("Fail to load [{}-{}].", SECTION_RTMP, FIELD_RTMP_PORT);
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

    public String getRtmpIp() {
        return rtmpIp;
    }

    public int getRtmpPort() {
        return rtmpPort;
    }

    public String getMagicCookie() {
        return magicCookie;
    }

    public String getRegisterLocalIp() {
        return registerLocalIp;
    }

    public int getRegisterLocalPort() {
        return registerLocalPort;
    }

    public String getRegisterTargetIp() {
        return registerTargetIp;
    }

    public int getRegisterTargetPort() {
        return registerTargetPort;
    }

    public String getIconRootPath() {
        return iconRootPath;
    }

    public String getPlaylistRootPath() {
        return playlistRootPath;
    }

    public int getPlaylistSize() {
        return playlistSize;
    }

    public int getUriLimit() {
        return uriLimit;
    }

    public String getRegisterListenIp() {
        return registerListenIp;
    }

    public int getRegisterListenPort() {
        return registerListenPort;
    }
}
