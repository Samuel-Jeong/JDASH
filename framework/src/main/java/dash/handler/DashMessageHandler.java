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

        File uriFile = new File(uri);
        if (!uriFile.exists() || uriFile.isDirectory()) {
            logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. URI is not exists or directory. (uri={})", this.uri, uri);
            return null;
        }
        ///////////////////////////

        ///////////////////////////
        // GENERATE MPD FROM MP4 BY GPAC
        String mpdPath = null; // Absolute path
        try {
            ///////////////////////////
            // GET COMMAND & RUN SCRIPT
            if (uri.endsWith(".mp4")) {
                mpdPath = uri.replace(".mp4", ".mpd");

                ///////////////////////////
                // FOR dash_encoder.py
                //String mpdDirectoryPath = mpdPath.substring(0, mpdPath.lastIndexOf("/")); // Absolute path
                //run = run + " --dash_folder " + mpdDirectoryPath + " " + uri; // + " " + mpdPath;
                ///////////////////////////

                ///////////////////////////
                // FOR mp4_to_dash.py
                String run = "python3 " + scriptPath;
                run = run + " " + uri + " " + mpdPath;
                ///////////////////////////

                runProcess(mpdPath, run);

                File mpdFile = new File(mpdPath);
                if (!mpdFile.exists()) {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    return null;
                }
                ///////////////////////////
            } else if (uri.endsWith(".mpd")) {
                mpdPath = uri;
            } else {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Wrong file extension. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
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

    public void runProcess(String mpdPath, String command) {
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
