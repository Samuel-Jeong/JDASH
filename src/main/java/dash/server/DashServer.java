package dash.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import dash.mpd.MpdManager;
import dash.mpd.parser.mpd.MPD;
import dash.server.dynamic.DynamicMediaManager;
import dash.server.dynamic.message.StreamingStartRequest;
import dash.server.dynamic.message.StreamingStopRequest;
import dash.server.dynamic.message.base.MessageHeader;
import dash.server.dynamic.message.base.MessageType;
import dash.server.handler.DashMessageHandler;
import dash.server.handler.HttpMessageManager;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import media.MediaInfo;
import media.MediaManager;
import network.definition.DestinationRecord;
import network.socket.GroupSocket;
import network.socket.SocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ObjectSupplier;
import service.ServiceManager;
import service.scheduler.schedule.ScheduleManager;
import service.system.ResourceManager;
import stream.LocalStreamService;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DashServer {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashServer.class);

    public static final String DASH_SCHEDULE_JOB = "DASH";

    private final BaseEnvironment baseEnvironment;
    private final SocketManager socketManager;
    private final HttpMessageManager httpMessageManager;
    private final MediaManager mediaManager;
    private final DynamicMediaManager dynamicMediaManager;
    private LocalStreamService localStreamService = null;

    private final MpdManager mpdManager;

    private final HashMap<String, DashUnit> dashUnitMap = new HashMap<>();
    private final ReentrantLock dashUnitMapLock = new ReentrantLock();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashServer() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        ///////////////////////////
        // 인스턴스 생성
        ObjectSupplier<BaseEnvironment> baseEnvObjectSupplier = ObjectSupplier.of(
                () -> new BaseEnvironment(
                        new ScheduleManager(),
                        new ResourceManager(
                                configManager.getHttpListenPort() + configManager.getHttpListenPortBeginOffset(),
                                configManager.getHttpListenPort() + configManager.getHttpListenPortEndOffset()
                        ),
                        DebugLevel.DEBUG
                )
        );

        baseEnvironment = baseEnvObjectSupplier.get(); // lazy initialization
        ///////////////////////////

        ///////////////////////////
        // SocketManager 생성
        socketManager = new SocketManager(
                baseEnvironment,
                false, true,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        ); // eager initialization
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

        ///////////////////////////
        // MpdManager 생성
        String dashUnitId = configManager.getHttpListenIp()
                + ":" + configManager.getCameraPath();
        DashUnit localDashUnit = addDashUnit(
                StreamType.DYNAMIC,
                dashUnitId,
                null,
                0
        );

        FileManager fileManager = new FileManager();
        localDashUnit.setInputFilePath(
                fileManager.concatFilePath(configManager.getMediaBasePath(), configManager.getCameraPath())
        );

        String uri = fileManager.getFileNameFromUri(configManager.getCameraPath());
        uri = fileManager.concatFilePath(configManager.getCameraPath(), uri + StreamConfigManager.DASH_POSTFIX);
        localDashUnit.setOutputFilePath(
                fileManager.concatFilePath(configManager.getMediaBasePath(), uri)
        );

        this.mpdManager = new MpdManager(localDashUnit.getId(), localDashUnit.getOutputFilePath());
        ///////////////////////////

        ///////////////////////////
        // DynamicMediaManager 생성
        dynamicMediaManager = new DynamicMediaManager(socketManager);
        ///////////////////////////
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean start() {
        boolean result = true;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        baseEnvironment.start();

        ///////////////////////////
        // LOAD MEDIA URI
        loadMediaUriList();
        ///////////////////////////

        httpMessageManager.start();
        dynamicMediaManager.start();

        if (baseEnvironment.getScheduleManager().initJob(DASH_SCHEDULE_JOB, 5, 5 * 2)) {
            if (configManager.isEnableClient()) {
                localStreamService = new LocalStreamService(
                        baseEnvironment.getScheduleManager(),
                        LocalStreamService.class.getSimpleName(),
                        0, 0, TimeUnit.MILLISECONDS,
                        1, 1, false
                );
                result = localStreamService.start();
                baseEnvironment.getScheduleManager().startJob(DASH_SCHEDULE_JOB, localStreamService);
            }
        }

        return result;
    }

    public void stop() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();

        if (localStreamService != null) {
            localStreamService.stop();
        }

        //////////////////////////////////////
        if (configManager.isEnableClient()) {
            DashServer dashServer = ServiceManager.getInstance().getDashServer();
            DynamicMediaManager dynamicMediaManager = dashServer.getDynamicMediaManager();
            GroupSocket listenSocket = dynamicMediaManager.getLocalGroupSocket();
            if (listenSocket != null) {
                DestinationRecord target = listenSocket.getDestination(dynamicMediaManager.getSessionId());
                if (target != null) {
                    StreamingStopRequest streamingStopRequest = new StreamingStopRequest(
                            new MessageHeader(
                                    DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.STREAMING_STOP_REQ,
                                    dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    StreamingStartRequest.MIN_SIZE + configManager.getCameraPath().length()
                            ),
                            configManager.getPreprocessListenIp().length(),
                            configManager.getPreprocessListenIp(),
                            configManager.getCameraPath().length(),
                            configManager.getCameraPath()
                    );
                    byte[] requestByteData = streamingStopRequest.getByteData();
                    target.getNettyChannel().sendData(requestByteData, requestByteData.length);
                    logger.debug("[CameraService] SEND StreamingStopRequest={}", streamingStopRequest);
                }
            }
        }
        //////////////////////////////////////

        dynamicMediaManager.stop();
        httpMessageManager.stop();
        baseEnvironment.stop();
    }

    public void loadMediaUriList() {
        try {
            if (mediaManager.loadUriList()) {
                httpMessageManager.clear();
                for (MediaInfo mediaInfo : mediaManager.getMediaInfoList()) {
                    httpMessageManager.get(
                            mediaInfo.getUri(),
                            new DashMessageHandler(mediaInfo.getUri())
                    );
                }

                logger.debug("[MediaManager] Success to load the uri list. \n{}", gson.toJson(httpMessageManager.getAllUris()));
            } else {
                logger.warn("[MediaManager] Fail to load the uri list.");
            }
        } catch (Exception e) {
            logger.warn("[MediaManager] loadMediaUriList.Exception. Fail to load the uri list.", e);
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

    public DynamicMediaManager getDynamicMediaManager() {
        return dynamicMediaManager;
    }

    public LocalStreamService getLocalStreamService() {
        return localStreamService;
    }

    public MpdManager getMpdManager() {
        return mpdManager;
    }

    public String getServiceName() {
        return httpMessageManager.getServiceName();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit addDashUnit(StreamType type, String dashUnitId, MPD mpd, long expires) {
        if (getDashUnitById(dashUnitId) != null) { return null; }

        try {
            dashUnitMapLock.lock();

            DashUnit dashUnit = new DashUnit(type, dashUnitId, mpd, expires);
            dashUnitMap.putIfAbsent(dashUnitId, dashUnit);
            logger.debug("[DashServer] [(+)CREATED] \n{}", dashUnit);
            return dashUnit;
        } catch (Exception e) {
            logger.warn("Fail to open the dash unit. (id={})", dashUnitId, e);
            return null;
        } finally {
            dashUnitMapLock.unlock();
        }
    }

    public void deleteDashUnit(String dashUnitId) {
        DashUnit dashUnit = getDashUnitById(dashUnitId);
        if (dashUnit == null) { return; }

        try {
            dashUnitMapLock.lock();

            dashUnit.finishLiveStreaming();
            logger.debug("[DashServer] [(-)DELETED] \n{}", dashUnit);

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

    public void deleteDashUnitsByType(StreamType type) {
        try {
            dashUnitMapLock.lock();
            dashUnitMap.entrySet().removeIf(entry -> entry.getValue().getType() == type);
        } catch (Exception e) {
            logger.warn("Fail to close all dash units.", e);
        } finally {
            dashUnitMapLock.unlock();
        }
    }

    public DashUnit getDashUnitById(String dashUnitId) { // dashUnitId > MEDIA URI
        return dashUnitMap.get(dashUnitId);
    }

    public int getDashUnitMapSize() {
        return dashUnitMap.size();
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

        logger.debug("[DashServer] RESPONSE: {}", response);
    }

    public void send100Continue(final ChannelHandlerContext ctx) {
        ctx.write(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.CONTINUE));
    }
    ////////////////////////////////////////////////////////////

}
