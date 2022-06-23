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
import dash.server.network.DashLocalAddressManager;
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
import service.scheduler.job.Job;
import service.scheduler.job.JobBuilder;
import service.scheduler.schedule.ScheduleManager;
import service.system.ResourceManager;
import stream.LocalStreamService;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DashServer {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashServer.class);

    public static final String DASH_SCHEDULE_JOB = "DASH_SCHEDULE_JOB";

    private final BaseEnvironment baseEnvironment;
    private final SocketManager httpSocketManager;
    private final SocketManager udpSocketManager;
    private final HttpMessageManager httpMessageManager;
    private final MediaManager mediaManager;
    private final DynamicMediaManager dynamicMediaManager;
    private final FileManager fileManager = new FileManager();
    private LocalStreamService localStreamService = null;

    private final MpdManager mpdManager;
    private final DashLocalAddressManager dashLocalAddressManager;

    private final HashMap<String, DashUnit> dashUnitMap = new HashMap<>();
    private final ReentrantLock dashUnitMapLock = new ReentrantLock();
    private final int maxDashUnitLimit;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashServer() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        maxDashUnitLimit = configManager.getMaxDashUnitLimit();

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
        dashLocalAddressManager = new DashLocalAddressManager(baseEnvironment, false); // ssl 아직 미지원
        ///////////////////////////

        ///////////////////////////
        // HTTP 용 SocketManager 생성
        httpSocketManager = new SocketManager(
                baseEnvironment,
                true, true,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        ); // eager initialization
        // UDP 용 SocketManager 생성
        udpSocketManager = new SocketManager(
                baseEnvironment,
                false, false,
                configManager.getThreadCount(),
                configManager.getSendBufSize(),
                configManager.getRecvBufSize()
        ); // eager initialization
        ///////////////////////////

        ///////////////////////////
        // HttpMessageManager 생성
        httpMessageManager = new HttpMessageManager(
                baseEnvironment.getScheduleManager(),
                httpSocketManager
        );
        ///////////////////////////

        ///////////////////////////
        // MediaManager 생성
        mediaManager = new MediaManager(configManager.getMediaListPath());
        ///////////////////////////

        ///////////////////////////
        // MpdManager 생성
        String dashUnitId = configManager.getHttpListenIp() + ":" + configManager.getCameraPath();
        DashUnit localDashUnit = addDashUnit(
                StreamType.NONE, dashUnitId,
                null, 0, false
        );

        if (localDashUnit == null) {
            logger.error("[DashServer] Fail to add a local dash unit. Fail to initiate service. Please check config.");
            System.exit(1);
        }

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
        dynamicMediaManager = new DynamicMediaManager(udpSocketManager);
        ///////////////////////////
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public boolean start() {
        boolean result = true;

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        baseEnvironment.start();

        dashLocalAddressManager.start();

        ///////////////////////////
        // LOAD MEDIA URI
        loadMediaUriList();
        ///////////////////////////

        httpMessageManager.start();
        dynamicMediaManager.start();

        if (configManager.isEnableClient()) {
            if (baseEnvironment.getScheduleManager().initJob(DASH_SCHEDULE_JOB, 5, 5 * 2)) {
                Job localStreamServiceJob = new JobBuilder()
                        .setScheduleManager(baseEnvironment.getScheduleManager())
                        .setName(LocalStreamService.class.getSimpleName())
                        .setInitialDelay(0)
                        .setInterval(0)
                        .setTimeUnit(TimeUnit.MILLISECONDS)
                        .setPriority(1)
                        .setTotalRunCount(1)
                        .setIsLasted(false)
                        .build();
                localStreamService = new LocalStreamService(localStreamServiceJob);
                if (localStreamService.init()) {
                    localStreamService.start();
                    result = baseEnvironment.getScheduleManager().startJob(DASH_SCHEDULE_JOB, localStreamService.getJob());
                } else {
                    result = false;
                }
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

        dashLocalAddressManager.stop();
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

    public SocketManager getHttpSocketManager() {
        return httpSocketManager;
    }

    public SocketManager getUdpSocketManager() {
        return udpSocketManager;
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

    public DashLocalAddressManager getDashLocalAddressManager() {
        return dashLocalAddressManager;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashUnit addDashUnit(StreamType type, String dashUnitId, MPD mpd, long expires, boolean isDynamic) {
        DashUnit dashUnit = getDashUnitById(dashUnitId);
        if (dashUnit != null) { return dashUnit; }
        if (dashUnitMap.size() == maxDashUnitLimit) {
            logger.warn("[DashServer] Fail to add a dash unit. List is full. (id={})", dashUnitId);
            return null;
        }

        try {
            dashUnitMapLock.lock();

            dashUnit = new DashUnit(type, dashUnitId, mpd, expires, isDynamic);
            dashUnitMap.putIfAbsent(dashUnitId, dashUnit);
            logger.debug("[DashServer] [(+)CREATED] \n{}", dashUnit);
            return dashUnit;
        } catch (Exception e) {
            logger.warn("[DashServer] Fail to open the dash unit. (id={})", dashUnitId, e);
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
            dashUnit.stop();
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

    public List<String> getDynamicStreamPathList() {
        List<String> streamKeys = new ArrayList<>();

        try {
            dashUnitMapLock.lock();
            for (Map.Entry<String, DashUnit> entry : dashUnitMap.entrySet()) {
                if (entry == null) { continue; }

                DashUnit dashUnit = entry.getValue();
                if (dashUnit == null) { continue; }
                if (!dashUnit.getType().equals(StreamType.DYNAMIC)) { continue; }

                String path = fileManager.getParentPathFromUri(dashUnit.getOutputFilePath());
                if (path != null && !path.isEmpty()) {
                    streamKeys.add(path);
                }
            }
        } catch (Exception e) {
            logger.warn("[DashServer] Fail to get the stream keys.", e);
        } finally {
            dashUnitMapLock.unlock();
        }

        return streamKeys;
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

    public long getDashUnitMapSizeWithStreamType(StreamType streamType) {
        return dashUnitMap.values().stream().filter(
                dashUnit -> (dashUnit != null) && (dashUnit.getType() == streamType)
                        && (!dashUnit.getId().equals(AppInstance.getInstance().getConfigManager().getId()))
        ).count();
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

    public void writeBadRequestError(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request) {

        writeErrorResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
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
