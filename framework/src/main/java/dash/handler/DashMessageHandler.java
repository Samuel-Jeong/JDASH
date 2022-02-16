package dash.handler;

import config.ConfigManager;
import dash.DashManager;
import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpRequest;
import dash.handler.definition.HttpResponse;
import dash.unit.DashUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class DashMessageHandler implements HttpMessageHandler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    private final String uri;
    private final String scriptPath;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashMessageHandler(String uri) {
        this.uri = uri;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        scriptPath = configManager.getScriptPath();
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public Object handle(HttpRequest request, HttpResponse response, String uriFileName) {
        if (request == null || uriFileName == null) { return null; }

        ///////////////////////////
        // CHECK URI
        String result = null;
        String uri = request.getRequest().uri();
        logger.debug("[DashMessageHandler(uri={})] URI: [{}]", this.uri, uri);

        if (!this.uri.equals(uri)) {
            logger.warn("[DashMessageHandler(uri={})] URI is not equal with handler's uri. (uri={})", this.uri, uri);
            return null;
        }

        String uriFileNameWithExtension = FileManager.getFileNameWithExtensionOnlyFromUri(uri);
        if (uriFileNameWithExtension.contains(".")) {
            File uriFile = new File(uri);
            if (!uriFile.exists() || uriFile.isDirectory()) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. (uri={})", this.uri, uri);
                return null;
            }
        }
        ///////////////////////////

        ///////////////////////////
        // GENERATE MPD FROM MP4 BY GPAC
        String mpdPath = null; // Absolute path
        try {
            ///////////////////////////
            // GET COMMAND & RUN SCRIPT
            if (uriFileNameWithExtension.contains(".")) {
                if (uri.endsWith(".mp4")) {
                    mpdPath = uri.replace(".mp4", ".mpd");
                    File mpdFile = new File(mpdPath);
                    if (!mpdFile.exists()) {
                        String run = "python3 " + scriptPath;

                        ///////////////////////////
                        // FOR dash_encoder.py
                        //String mpdDirectoryPath = mpdPath.substring(0, mpdPath.lastIndexOf("/")); // Absolute path
                        //run = run + " --dash_folder " + mpdDirectoryPath + " --video " + uri; // python3 dash_encoder.py --dash_folder /home/uangel/udash/media/Seoul --video /home/uangel/udash/media/Seoul/Seoul.mp4
                        ///////////////////////////

                        ///////////////////////////
                        // FOR mp4_to_dash.py
                        run = run + " " + uri + " " + mpdPath;  // python3 mp4_to_dash.py /home/uangel/udash/media/Seoul/Seoul.mp4 /home/uangel/udash/media/Seoul/Seoul.mpd
                        ///////////////////////////

                        runProcess(run, mpdPath);

                        mpdFile = new File(mpdPath);
                        if (!mpdFile.exists()) {
                            logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                            return null;
                        }
                    } else {
                        logger.debug("[DashMessageHandler(uri={})] The mpd file is already exist. (mpdPath={})", this.uri, mpdPath);
                    }
                } else if (uri.endsWith(".mpd")) {
                    mpdPath = uri;
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Wrong file extension. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    return null;
                }
            } else {
                ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
                String curRtmpUri = FileManager.concatFilePath(networkPath, configManager.getCameraPath());
                mpdPath = FileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath());
                File mpdPathFile = new File(mpdPath);
                if (!mpdPathFile.exists()) {
                    if (mpdPathFile.mkdirs()) {
                        logger.debug("[DashMessageHandler(uri={})] [LIVE] Parent mpd path is created. (parentMpdPath={}, rtmpUri={})", this.uri, mpdPath, curRtmpUri);
                    }
                }

                mpdPath = FileManager.concatFilePath(mpdPath, uriFileName + ".mpd");
                logger.debug("[DashMessageHandler(uri={})] [LIVE] Final mpd path: {} (rtmpUri={})", this.uri, mpdPath, curRtmpUri);
                String run = "python3 " + scriptPath;

                ///////////////////////////
                // FOR mp4_to_dash.py
                run = run + " " + curRtmpUri + " " + mpdPath;  // python3 mp4_to_dash.py rtmp://airtc.uangel.com:1940/live/livestream /home/uangel/udash/media/live/livestream/livestream.mpd
                ///////////////////////////

                runProcess(run, mpdPath);

                File mpdFile = new File(mpdPath);
                if (!mpdFile.exists()) {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (rtmpUri={}, mpdPath={})", this.uri, curRtmpUri, mpdPath);
                    return null;
                }
            }

            ///////////////////////////
            // GET MPD
            DashManager dashManager = ServiceManager.getInstance().getDashManager();
            MPD mpd = dashManager.parseMpd(mpdPath);
            if (mpd == null) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Fail to parse the mpd. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
            }

            // VALIDATE MPD
            if (dashManager.validate(mpd)) {
                logger.debug("[DashMessageHandler(uri={})] Success to validate the mpd.", this.uri);
            } else {
                logger.warn("[DashMessageHandler(uri={})] Fail to validate the mpd.", this.uri);
                return null;
            }

            result = dashManager.getMpdParser().writeAsString(mpd);
            ///////////////////////////

            ///////////////////////////
            // SAVE META DATA OF MEDIA
            dashManager.addDashUnit(uriFileName, mpd);
            DashUnit dashUnit = dashManager.getDashUnit(uriFileName);
            dashUnit.setInputFilePath(uri);
            dashUnit.setOutputFilePath(mpdPath);
            dashUnit.setMinBufferTime(mpd.getMinBufferTime());
            dashUnit.setDuration(mpd.getMediaPresentationDuration());
            logger.debug("[DashMessageHandler(uri={})] CREATED DashUnit: \n{}", this.uri, dashUnit);
            ///////////////////////////
        } catch (Exception e) {
            logger.warn("DashMessageHandler(uri={}).handle.Exception (uri={}, mpdPath={})\n", this.uri, uri, mpdPath, e);
        }
        ///////////////////////////

        return result;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public String getUri() {
        return uri;
    }

    public void runProcess(String command, String mpdPath) {
        BufferedReader stdOut = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);

            String str;
            stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((str = stdOut.readLine()) != null) {
                logger.debug(str);
            }

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("[DashMessageHandler(uri=" + this.uri + ")] exit code is not 0 [" + exitValue + "]");
            }

            logger.debug("[DashMessageHandler(uri={})] Success to convert. (fileName={})", this.uri, mpdPath);
        } catch (Exception e) {
            logger.warn("DashMessageHandler.runProcess.Exception", e);
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (stdOut != null) {
                try {
                    stdOut.close();
                } catch (IOException e) {
                    logger.warn("[DashMessageHandler(uri={})] Fail to close the BufferReader.", this.uri, e);
                }
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////

}
