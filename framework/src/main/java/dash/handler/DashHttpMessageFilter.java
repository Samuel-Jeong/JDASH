package dash.handler;

import dash.DashManager;
import dash.handler.definition.HttpMessageRoute;
import dash.handler.definition.HttpMessageRouteTable;
import dash.handler.definition.HttpRequest;
import dash.unit.DashUnit;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.module.FileManager;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DashHttpMessageFilter extends SimpleChannelInboundHandler<Object> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageFilter.class);

    private final String basePath;
    private final HttpMessageRouteTable uriRouteTable;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageFilter(HttpMessageRouteTable routeTable) {
        this.uriRouteTable = routeTable;
        this.basePath = AppInstance.getInstance().getConfigManager().getMediaBasePath();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            return;
        }

        ///////////////////////////
        // GET URI
        final FullHttpRequest request = (FullHttpRequest) msg;
        if (HttpHeaderUtil.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        final HttpMethod method = request.method();
        String uri = request.uri(); // [/Seoul.mp4] or [/Seoul_chunk_1_00001.m4s]
        if (uri == null) {
            logger.warn("[DashHttpMessageFilter] URI is not defined.");
            return;
        }
        logger.debug("[DashHttpMessageFilter] REQUEST: \n{}", request);
        ///////////////////////////

        ///////////////////////////
        // GET DASH UNIT
        boolean isRegistered = false;

        DashUnit dashUnit = null;
        String uriFileName = FileManager.getFileNameFromUri(uri); // [Seoul] or [Seoul_chunk_1_00001]
        if (uriFileName == null) {
            logger.warn("[DashHttpMessageFilter] Fail to get the URI file name.");
            return;
        }

        for (Map.Entry<String, DashUnit> entry : DashManager.getInstance().getCloneDashMap().entrySet()) {
            if (entry == null) { continue; }

            DashUnit curDashUnit = entry.getValue();
            if (curDashUnit == null) { continue; }

            if (uriFileName.contains(curDashUnit.getId())) {
                dashUnit = curDashUnit;
                isRegistered = true;
                logger.debug("[DashHttpMessageFilter] MATCHED! [{}] <> [{}]", uriFileName, dashUnit.getId());
                uriFileName = dashUnit.getId();
                break;
            }
        }

        if (dashUnit == null) {
            dashUnit = DashManager.getInstance().getDashUnit(uriFileName);
            if (dashUnit != null) {
                isRegistered = true;
            }
        }

        uri = FileManager.concatFilePath(uriFileName, uri); // [Seoul/Seoul.mp4] or [Seoul/Seoul_chunk_1_00001.m4s]
        uri = FileManager.concatFilePath(basePath, uri); // [/Users/.../Seoul/Seoul.mp4] or [/Users/.../Seoul/Seoul_chunk_1_00001.m4s]
        request.setUri(uri);
        ///////////////////////////

        ///////////////////////////
        // ROUTING IF NOT REGISTERED
        HttpMessageRoute uriRoute = null;
        if (!isRegistered) {
            uriRoute = uriRouteTable.findUriRoute(method, uri);
            if (uriRoute == null) {
                logger.warn("[DashHttpMessageFilter] NOT FOUND URI: {}", uri);
                writeNotFound(ctx, request);
                return;
            }
        }
        ///////////////////////////

        try {
            ///////////////////////////
            // PROCESS URI
            if (!isRegistered) { // GET MPD URI 수신 시 (not segment uri)
                final HttpRequest requestWrapper = new HttpRequest(request);
                final Object obj = uriRoute.getHandler().handle(requestWrapper, null, uriFileName);
                String content = obj == null ? "" : obj.toString();

                writeResponse(ctx, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_DASH_XML, content);
            } else { // GET SEGMENT URI 수신 시 (not mpd uri)
                byte[] segmentBytes = dashUnit.getSegmentByteData(uri);
                logger.debug("SEGMENT [{}] [len={}]", uri, segmentBytes.length);
                writeResponse(ctx, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, segmentBytes);
            }
            ///////////////////////////
        } catch (final Exception e) {
            logger.warn("DashHttpHandler.messageReceived.Exception", e);
            writeInternalServerError(ctx, request);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        ctx.close();
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.flush();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private static void writeNotFound(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request) {

        writeErrorResponse(ctx, request, HttpResponseStatus.NOT_FOUND);
    }
    private static void writeInternalServerError(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request) {

        writeErrorResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private static void writeErrorResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status) {

        writeResponse(ctx, request, status, HttpMessageManager.TYPE_PLAIN, status.reasonPhrase().toString());
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private static void writeResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status,
            final CharSequence contentType,
            final String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        final ByteBuf entity = Unpooled.wrappedBuffer(bytes);
        writeResponse(ctx, request, status, entity, contentType, bytes.length);
    }

    private static void writeResponse(
            final ChannelHandlerContext ctx,
            final FullHttpRequest request,
            final HttpResponseStatus status,
            final CharSequence contentType,
            final byte[] bytes) {
        final ByteBuf entity = Unpooled.wrappedBuffer(bytes);
        writeResponse(ctx, request, status, entity, contentType, bytes.length);
    }

    private static void writeResponse(
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
        headers.set(HttpHeaderNames.SERVER, HttpMessageManager.SERVER_NAME);
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
    private static void send100Continue(final ChannelHandlerContext ctx) {
        ctx.write(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.CONTINUE));
    }
    ////////////////////////////////////////////////////////////

}
