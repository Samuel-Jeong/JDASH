package dash.handler;

import dash.DashManager;
import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpRequest;
import dash.handler.definition.HttpResponse;
import dash.unit.DashUnit;
import io.lindstrom.mpd.data.AdaptationSet;
import io.lindstrom.mpd.data.MPD;
import io.lindstrom.mpd.data.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.io.File;
import java.util.List;

public class DashMessageHandler implements HttpMessageHandler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    private final String uri;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashMessageHandler(String uri) {
        this.uri = uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public Object handle(HttpRequest request, HttpResponse response) {
        if (request == null) { return null; }

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
        String mpdPath = null;
        try {
            ///////////////////////////
            // GET COMMAND & RUN SCRIPT
            String run = "python3 " + AppInstance.getInstance().getConfigManager().getScriptPath();
            if (uri.endsWith(".mp4")) {
                mpdPath = uri.replace(".mp4", ".mpd");
            }

            if (mpdPath == null) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MP4 file is supported only. (uri={})", this.uri, uri);
                return null;
            }

            run = run + " " + uri + " " + mpdPath;
            Runtime.getRuntime().exec(run); // IF mpd path is exists, just return

            File mpdFile = new File(mpdPath);
            if (!mpdFile.exists()) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
            }
            ///////////////////////////

            ///////////////////////////
            // GET MPD
            DashManager dashManager = DashManager.getInstance();
            MPD mpd = dashManager.parseMpd(mpdPath);
            if (mpd == null) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Fail to parse the mpd. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
            }
            result = dashManager.getMpdParser().writeAsString(mpd);

            List<Period> periodList = mpd.getPeriods();
            for (Period period : periodList) {
                List<AdaptationSet> adaptationSetList = period.getAdaptationSets();
            }
            ///////////////////////////

            ///////////////////////////
            // SAVE META DATA OF MEDIA
            dashManager.addDashUnit(uri, mpd);
            DashUnit dashUnit = dashManager.getDashUnit(uri);
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
    ////////////////////////////////////////////////////////////////////////////////

}
