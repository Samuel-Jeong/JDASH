package dash;

import cam.CameraManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.handler.DashMessageHandler;
import dash.handler.HttpMessageManager;
import dash.unit.DashUnit;
import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import media.MediaManager;
import network.socket.SocketManager;
import network.user.UserInfo;
import network.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.scheduler.schedule.ScheduleManager;
import tool.parser.MPDParser;
import tool.parser.mpd.MPD;
import tool.validator.MPDValidator;
import tool.validator.ManifestValidationException;
import util.module.NonceGenerator;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DashManager {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashManager.class);

    public static final String DASH_SCHEDULE_JOB = "DASH";

    private final UserInfo myUserInfo = new UserInfo(NonceGenerator.createRandomNonce());

    private final BaseEnvironment baseEnvironment;
    private final SocketManager socketManager;
    private final HttpMessageManager httpMessageManager;
    private final MediaManager mediaManager;
    private final UserManager userManager;
    private CameraManager cameraManager = null;

    private final MPDParser mpdParser = new MPDParser();
    private MPDValidator mpdValidator = null;

    private final HashMap<String, DashUnit> dashUnitMap = new HashMap<>();
    private final ReentrantLock dashUnitMapLock = new ReentrantLock();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashManager() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        userManager = new UserManager();

        ///////////////////////////
        // 인스턴스 생성
        baseEnvironment = new BaseEnvironment(
                new ScheduleManager(),
                null,
                DebugLevel.DEBUG
        );
        ///////////////////////////

        ///////////////////////////
        // SocketManager 생성
        socketManager = new SocketManager(
                baseEnvironment,
                false, true,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        );
        ///////////////////////////

        ///////////////////////////
        // HttpMessageManager 생성
        httpMessageManager = new HttpMessageManager(
                baseEnvironment.getScheduleManager(),
                socketManager
        );
        ///////////////////////////

        ///////////////////////////
        // MediaManager 생성
        mediaManager = new MediaManager(configManager.getMediaListPath());
        ///////////////////////////
    }
    ////////////////////////////////////////////////////////////

    public UserInfo getMyUserInfo() {
        return myUserInfo;
    }

    ////////////////////////////////////////////////////////////
    public void start() {
        baseEnvironment.start();

        ///////////////////////////
        // LOAD MEDIA URI
        loadMediaUriList();
        ///////////////////////////

        httpMessageManager.start();

        if (baseEnvironment.getScheduleManager().initJob(DASH_SCHEDULE_JOB, 5, 5 * 2)) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            if (configManager.isEnableClient()) {
                cameraManager = new CameraManager(
                        baseEnvironment.getScheduleManager(),
                        CameraManager.class.getSimpleName(),
                        0, 0, TimeUnit.MILLISECONDS,
                        1, 1, false
                );
                baseEnvironment.getScheduleManager().startJob(DASH_SCHEDULE_JOB, cameraManager);
            }
        }

        ///////////////////////////
        // MPDValidator 생성
        try {
            mpdValidator = new MPDValidator(mpdParser);
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to make a mpd validator.");
        }
        ///////////////////////////
    }

    public void stop() {
        httpMessageManager.stop();
        baseEnvironment.stop();
    }

    public void loadMediaUriList() {
        if (mediaManager.loadUriList()) {
            httpMessageManager.clear();
            for (String uri : mediaManager.getUriList()) {
                httpMessageManager.get(
                        uri,
                        new DashMessageHandler(uri, getBaseEnvironment().getScheduleManager())
                );
            }

            logger.debug("[MediaManager] Success to load the uri list. \n{}", gson.toJson(httpMessageManager.getAllUris()));
        } else {
            logger.warn("[MediaManager] Fail to load the uri list.");
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public BaseEnvironment getBaseEnvironment() {
        return baseEnvironment;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public HttpMessageManager getHttpMessageManager() {
        return httpMessageManager;
    }

    public MediaManager getMediaManager() {
        return mediaManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public boolean validate(MPD mpd) {
        if (mpd == null || mpdValidator == null) { return false; }

        try {
            mpdValidator.validate(mpd);
        } catch (ManifestValidationException e) {
            logger.warn("DashManager.validate.ManifestValidationException", e);
            logger.warn("{}", e.getViolations());
            return false;
        } catch (Exception e) {
            logger.warn("DashManager.validate.Exception", e);
            return false;
        }

        return true;
    }

    public String getServiceName() {
        return httpMessageManager.getServiceName();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit addDashUnit(String dashUnitId, MPD mpd) {
        if (getDashUnit(dashUnitId) != null) { return null; }

        try {
            dashUnitMapLock.lock();

            DashUnit dashUnit = new DashUnit(dashUnitId, mpd);
            dashUnitMap.putIfAbsent(dashUnitId, dashUnit);
            logger.debug("[DashHttpMessageFilter] [(+)CREATED] \n{}", dashUnit);
            return dashUnit;
        } catch (Exception e) {
            logger.warn("Fail to open the dash unit. (id={})", dashUnitId, e);
            return null;
        } finally {
            dashUnitMapLock.unlock();
        }
    }

    public void deleteDashUnit(String dashUnitId) {
        try {
            dashUnitMapLock.lock();

            DashUnit dashUnit = getDashUnit(dashUnitId);
            dashUnit.finishLiveMpdProcess();

            logger.debug("[DashHttpMessageFilter] [(-)DELETED] \n{}", dashUnit);
            dashUnitMap.remove(dashUnitId);
        } catch (Exception e) {
            logger.warn("Fail to close the dash unit. (id={})", dashUnitId, e);
        } finally {
            dashUnitMapLock.unlock();
        }
    }

    public HashMap<String, DashUnit> getCloneDashMap( ) {
        HashMap<String, DashUnit> cloneMap;

        try {
            dashUnitMapLock.lock();

            cloneMap = (HashMap<String, DashUnit>) dashUnitMap.clone();
        } catch (Exception e) {
            logger.warn("Fail to clone the dash unit map.", e);
            cloneMap = dashUnitMap;
        } finally {
            dashUnitMapLock.unlock();
        }

        return cloneMap;
    }

    public void deleteAllDashUnits() {
        try {
            dashUnitMapLock.lock();
            dashUnitMap.entrySet().removeIf(Objects::nonNull);
        } catch (Exception e) {
            logger.warn("Fail to close all dash units.", e);
        } finally {
            dashUnitMapLock.unlock();
        }
    }

    public DashUnit getDashUnit(String dashUnitId) { // dashUnitId > MEDIA URI
        return dashUnitMap.get(dashUnitId);
    }

    public int getDashUnitMapSize() {
        return dashUnitMap.size();
    }
    /////////////////////////////////////////////1///////////////

    ////////////////////////////////////////////////////////////
    /**
     * Durations define the amount of intervening time in a time interval and
     *      are represented by the format P[n]Y[n]M[n]DT[n]H[n]M[n]S or P[n]W as shown on the aside.
     *
     * In these representations, the [n] is replaced by the value for each of the date and time elements that follow the [n].
     *      Leading zeros are not required,
     *              but the maximum number of digits for each element should be agreed to by the communicating parties.
     *      The capital letters P, Y, M, W, D, T, H, M, and S are designators
     *              for each of the date and time elements and are not replaced.
     *
     *      P is the duration designator (for period) placed at the start of the duration representation.
     *      Y is the year designator that follows the value for the number of years.
     *      M is the month designator that follows the value for the number of months.
     *       W is the week designator that follows the value for the number of weeks.
     *      D is the day designator that follows the value for the number of days.
     *      T is the time designator that precedes the time components of the representation.
     *      H is the hour designator that follows the value for the number of hours.
     *      M is the minute designator that follows the value for the number of minutes.
     *      S is the second designator that follows the value for the number of seconds.
     *
     * For example,
     *      "P3Y6M4DT12H30M5S" represents a duration of
     *              "three years, six months, four days, twelve hours, thirty minutes, and five seconds".
     *
     * Date and time elements including their designator may be omitted if their value is zero,
     *      and lower-order elements may also be omitted for reduced precision.
     *
     *      For example, "P23DT23H" and "P4Y" are both acceptable duration representations.
     *      However, at least one element must be present,
     *              thus "P" is not a valid representation for a duration of 0 seconds.
     *      "PT0S" or "P0D", however, are both valid and represent the same duration.
     *
     * To resolve ambiguity,
     *      "P1M" is a one-month duration and "PT1M" is a one-minute duration
     *              (note the time designator, T, that precedes the time value).
     *      The smallest value used may also have a decimal fraction, as in "P0.5Y" to indicate half a year.
     *      This decimal fraction may be specified with either a comma or a full stop, as in "P0,5Y" or "P0.5Y".
     *      The standard does not prohibit date and time values in a duration representation
     *      from exceeding their "carry over points" except as noted below.
     *
     *      Thus, "PT36H" could be used as well as "P1DT12H" for representing the same duration.
     *      But keep in mind that "PT36H" is not the same as "P1DT12H" when switching from or to Daylight saving time.
     *
     * Alternatively,
     *      a format for duration based on combined date and time representations
     *      may be used by agreement between the communicating parties either
     *      in the basic format PYYYYMMDDThhmmss or in the extended format P[YYYY]-[MM]-[DD]T[hh]:[mm]:[ss].
     *
     *      For example, the first duration shown above would be "P0003-06-04T12:30:05".
     *      However, individual date and time values cannot exceed their moduli
     *              (e.g. a value of 13 for the month or 25 for the hour would not be permissible).
     *
     * @param timeString duration
     * @return real time
     */
    private double parseTime(String timeString) {
        double time = 0;
        int begin = timeString.indexOf("PT") + 2;
        int end;
        end = timeString.indexOf("Y");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 365 * 24 * 60 * 60;
            begin = end + 1;
        }
        if (timeString.indexOf("M") != timeString.lastIndexOf("M")) {
            end = timeString.indexOf("M");
            if (end != -1) {
                time += Double.parseDouble(timeString.substring(begin, end)) * 30 * 24 * 60 * 60;
                begin = end + 1;
            }
        }
        end = timeString.indexOf("DT");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 24 * 60 * 60;
            begin = end + 2;
        }
        end = timeString.indexOf("H");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 60 * 60;
            begin = end + 1;
        }
        end = timeString.lastIndexOf("M");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end)) * 60;
            begin = end + 1;
        }
        end = timeString.indexOf("S");
        if (end != -1) {
            time += Double.parseDouble(timeString.substring(begin, end));
        }
        return time;
    }

    /**
     * @fn public MPD parseMpd(String filePath)
     * @brief MPD Parsing Function
     * @param filePath File absolute path
     * @return Media Presentation Description object
     */
    public MPD parseMpd(String filePath) {
        if (filePath == null) { return null; }

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(filePath);
        } catch (Exception e) {
            logger.warn("[DashManager] Fail to get the input stream. (filePath={})", filePath, e);
            return null;
        }

        //////////////////////////////
        // Media Presentation Description (MPD)
        MPD mpd = null;

        try {
            mpd = mpdParser.parse(inputStream);

            if (logger.isTraceEnabled()) {
                logger.trace(mpdParser.writeAsString(mpd));
            }
        } catch (Exception e) {
            logger.warn("DashManager.parseMpd.Exception", e);
        }

        // MPD END
        //////////////////////////////

        return mpd;
    }

    public MPDParser getMpdParser() {
        return mpdParser;
    }

    public void makeMpdFileFromMp4() {
        // ffmpeg -i file.mp4 -vcodec copy -acodec copy output.mpd
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void writeNotFound(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request) {

        writeErrorResponse(ctx, request, HttpResponseStatus.NOT_FOUND);
    }
    public void writeInternalServerError(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request) {

        writeErrorResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private void writeErrorResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status) {

        writeResponse(ctx, request, status, HttpMessageManager.TYPE_PLAIN, status.reasonPhrase().toString());
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void writeResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status,
            final CharSequence contentType,
            final String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final ByteBuf entity = Unpooled.wrappedBuffer(bytes);
        writeResponse(ctx, request, status, entity, contentType, bytes.length);
    }

    public void writeResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status,
            final CharSequence contentType,
            final byte[] bytes) {
        final ByteBuf entity = Unpooled.wrappedBuffer(bytes);
        writeResponse(ctx, request, status, entity, contentType, bytes.length);
    }

    private void writeResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status,
            final ByteBuf buf,
            final CharSequence contentType,
            final int contentLength) {
        // Decide whether to close the connection or not.
        final boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);

        // Build the response object.
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                buf,
                false
        );

        final ZonedDateTime dateTime = ZonedDateTime.now();
        final DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;

        final DefaultHttpHeaders headers = (DefaultHttpHeaders) response.headers();

        headers.set(HttpHeaderNames.SERVER, getServiceName());
        headers.set(HttpHeaderNames.DATE, dateTime.format(formatter));
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        headers.set(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(contentLength));

        // Close the non-keep-alive connection after the write operation is done.
        if (!keepAlive) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response, ctx.voidPromise());
        }

        logger.debug("[DashHttpMessageFilter] RESPONSE: {}", response);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void send100Continue(final ChannelHandlerContext ctx) {
        ctx.write(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.CONTINUE));
    }
    ////////////////////////////////////////////////////////////

}
