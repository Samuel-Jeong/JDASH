package dash.handler;

import config.ConfigManager;
import dash.DashManager;
import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpRequest;
import dash.handler.definition.HttpResponse;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import process.ProcessManager;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.schedule.ScheduleManager;
import tool.parser.mpd.MPD;
import util.module.FileManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static dash.DashManager.DASH_SCHEDULE_JOB;

public class DashMessageHandler implements HttpMessageHandler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    public static final String LIVE_MESSAGE = "LIVE";

    private final String uri;
    private final String scriptPath;
    private final ScheduleManager scheduleManager;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashMessageHandler(String uri, ScheduleManager scheduleManager) {
        this.uri = uri;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        scriptPath = configManager.getScriptPath();
        this.scheduleManager = scheduleManager;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public Object handle(HttpRequest request, HttpResponse response, String originUri, String uriFileName, ChannelHandlerContext ctx, DashUnit dashUnit) {
        DashManager dashManager = ServiceManager.getInstance().getDashManager();
        if (request == null || uriFileName == null || ctx == null) {
            dashManager.deleteDashUnit(dashUnit.getId());
            return null;
        }

        ///////////////////////////
        // CHECK URI
        String result = null;
        String uri = request.getRequest().uri();
        logger.debug("[DashMessageHandler(uri={})] URI: [{}]", this.uri, uri);

        if (!this.uri.equals(uri)) {
            logger.warn("[DashMessageHandler(uri={})] URI is not equal with handler's uri. (uri={})", this.uri, uri);
            dashManager.deleteDashUnit(dashUnit.getId());
            return null;
        }

        String uriFileNameWithExtension = FileManager.getFileNameWithExtensionFromUri(uri);
        if (uriFileNameWithExtension.contains(".")) {
            File uriFile = new File(uri);
            if (!uriFile.exists() || uriFile.isDirectory()) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. (uri={})", this.uri, uri);
                dashManager.deleteDashUnit(dashUnit.getId());
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
                        ///////////////////////////
                        // sh rtmp_streaming.sh tigers /home/uangel/udash/media/animal/tigers.mp4 /home/uangel/udash/media/animal/tigers.mpd
                        String command = "sh " + scriptPath;
                        command = command + " " + uriFileName + " " + uri + " " + mpdPath;
                        ProcessManager.runProcessWait(command);
                        ///////////////////////////

                        mpdFile = new File(mpdPath);
                        if (!mpdFile.exists()) {
                            logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                            dashManager.deleteDashUnit(dashUnit.getId());
                            return null;
                        }
                    } else {
                        logger.debug("[DashMessageHandler(uri={})] The mpd file is already exist. (mpdPath={})", this.uri, mpdPath);
                    }
                } else if (uri.endsWith(".mpd")) {
                    mpdPath = uri;
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Wrong file extension. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    dashManager.deleteDashUnit(dashUnit.getId());
                    return null;
                }

                dashUnit.setInputFilePath(uri);
                dashUnit.setOutputFilePath(mpdPath);
                dashUnit.setLiveStreaming(false);

                ///////////////////////////
                // GET MPD
                MPD mpd = dashManager.parseMpd(mpdPath);
                if (mpd == null) {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Fail to parse the mpd. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    dashManager.deleteDashUnit(dashUnit.getId());
                    return null;
                }

                // VALIDATE MPD
                if (dashManager.validate(mpd)) {
                    logger.debug("[DashMessageHandler(uri={})] Success to validate the mpd.", this.uri);
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to validate the mpd.", this.uri);
                    dashManager.deleteDashUnit(dashUnit.getId());
                    return null;
                }

                result = dashManager.getMpdParser().writeAsString(mpd);
                ///////////////////////////

                ///////////////////////////
                // SAVE META DATA OF MEDIA
                dashUnit.setMpd(mpd);
                dashUnit.setMinBufferTime(mpd.getMinBufferTime());
                dashUnit.setDuration(mpd.getMediaPresentationDuration());
                logger.debug("[DashMessageHandler(uri={})] MODIFIED DashUnit[{}]: \n{}", this.uri, dashUnit.getId(), dashUnit);
                ///////////////////////////
            } else {
                DashDynamicStreamHandler dashDynamicStreamHandler = new DashDynamicStreamHandler(
                        scheduleManager,
                        DashDynamicStreamHandler.class.getSimpleName(),
                        0, 0, TimeUnit.MILLISECONDS,
                        1, 1, false,
                        uri, mpdPath, ctx, request.getRequest(), dashUnit
                ); // every time for mpd request by dash unit
                scheduleManager.startJob(DASH_SCHEDULE_JOB, dashDynamicStreamHandler);
                ///////////////////////////

                result = LIVE_MESSAGE;
            }
        } catch (Exception e) {
            logger.warn("DashMessageHandler(uri={}).handle.Exception (uri={}, mpdPath={})\n", this.uri, uri, mpdPath, e);
            dashManager.deleteDashUnit(dashUnit.getId());
        }
        ///////////////////////////

        return result;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public String getUri() {
        return uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
