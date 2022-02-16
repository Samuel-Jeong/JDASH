package dash.handler;

import dash.DashManager;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;
import tool.parser.mpd.MPD;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class DashDynamicStreamHandler extends Job {

    private static final Logger logger = LoggerFactory.getLogger(DashDynamicStreamHandler.class);

    private final String uri;
    private final String mpdPath;
    private final ChannelHandlerContext ctx;
    private final FullHttpRequest fullHttpRequest;
    private final DashUnit dashUnit;

    public DashDynamicStreamHandler(ScheduleManager scheduleManager, String name,
                                    int initialDelay, int interval, TimeUnit timeUnit,
                                    int priority, int totalRunCount, boolean isLasted,
                                    String uri, String mpdPath,
                                    ChannelHandlerContext ctx, FullHttpRequest request, DashUnit dashUnit) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        this.uri = uri;
        this.mpdPath = mpdPath;
        this.ctx = ctx;
        this.fullHttpRequest = request;
        this.dashUnit = dashUnit;
    }

    @Override
    public void run() {
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        try {
            ///////////////////////////
            // GET MPD
            while (true) {
                File mpdFile = new File(mpdPath);
                if (mpdFile.exists()) { break; }
                timeUnit.sleep(1000);
            }

            DashManager dashManager = ServiceManager.getInstance().getDashManager();
            MPD mpd = dashManager.parseMpd(mpdPath);
            if (mpd == null) {
                logger.warn("[DashDynamicStreamHandler(mpdPath={})] Fail to generate the mpd file. Fail to parse the mpd.", this.mpdPath);
                return;
            }

            // VALIDATE MPD > ServiceDescription 파싱 오류 발생해서 잠시 기능 정지
            /*if (dashManager.validate(mpd)) {
                logger.debug("[DashDynamicStreamHandler(mpdPath={})] Success to validate the mpd.", this.mpdPath);
            } else {
                logger.warn("[DashDynamicStreamHandler(mpdPath={})] Fail to validate the mpd.", this.mpdPath);
                return;
            }*/

            String result = dashManager.getMpdParser().writeAsString(mpd);
            if (result == null) {
                logger.warn("[DashDynamicStreamHandler(mpdPath={})] Fail to generate the mpd file. Fail to get the mpd data.", this.mpdPath);
                return;
            }
            ///////////////////////////

            ///////////////////////////
            // SAVE META DATA OF MEDIA
            if (dashUnit != null) {
                dashUnit.setMpd(mpd);
                dashUnit.setInputFilePath(uri);
                dashUnit.setOutputFilePath(mpdPath);
                dashUnit.setMinBufferTime(mpd.getMinBufferTime());
                dashUnit.setDuration(mpd.getMediaPresentationDuration());
                dashUnit.setLiveStreaming(true);
                logger.debug("[DashDynamicStreamHandler(mpdPath={})] MODIFIED DashUnit[{}]: \n{}", this.mpdPath, dashUnit.getId(), dashUnit);
            }
            ///////////////////////////

            ///////////////////////////
            // RESPONSE WITH MPD
            dashManager.writeResponse(ctx, fullHttpRequest, HttpResponseStatus.OK, HttpMessageManager.TYPE_DASH_XML, result);
            ///////////////////////////
        } catch (Exception e) {
            logger.warn("[DashDynamicStreamHandler(mpdPath={})] run.Exception", mpdPath, e);
        }
    }
}
