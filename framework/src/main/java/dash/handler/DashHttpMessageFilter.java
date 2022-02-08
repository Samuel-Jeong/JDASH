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

public class DashHttpMessageFilter extends SimpleChannelInboundHandler<Object> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageFilter.class);

    private final String basePath;
    private final HttpMessageRouteTable routeTable;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageFilter(HttpMessageRouteTable routeTable) {
        this.routeTable = routeTable;
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
        String uri = request.uri(); // /Seoul.mp4 or /Seoul_chunk_1_00001.m4s
        if (uri == null) { return; }
        ///////////////////////////

        ///////////////////////////
        // GET DASH UNIT
        String uriFileName;
        boolean isRegistered = false;
        DashUnit dashUnit = DashManager.getInstance().getDashUnit(uri);
        if (dashUnit != null) {
            isRegistered = true;
            uriFileName = dashUnit.getUriFileName(); // Seoul
        } else {
            uriFileName = getFileNameFromUri(uri); // Seoul
        }

        uri = FileManager.concatFilePath(uriFileName, uri); // Seoul/Seoul.mp4 or Seoul/Seoul_chunk_1_00001.m4s
        uri = FileManager.concatFilePath(basePath, uri); // /Users/.../Seoul/Seoul.mp4 or /Users/.../Seoul/Seoul_chunk_1_00001.m4s
        request.setUri(uri);
        ///////////////////////////

        ///////////////////////////
        // ROUTING IF NOT REGISTERED
        HttpMessageRoute route = null;
        if (!isRegistered) {
            route = routeTable.findRoute(method, uri);
            if (route == null) {
                logger.warn("[DashHttpMessageFilter] NOT FOUND URI: {}", uri);
                writeNotFound(ctx, request);
                return;
            }
        }
        ///////////////////////////

        try {
            ///////////////////////////
            // PROCESS URI
            String content;
            if (!isRegistered) { // URI GET 최초 수신 시
                final HttpRequest requestWrapper = new HttpRequest(request);
                final Object obj = route.getHandler().handle(requestWrapper, null);
                content = obj == null ? "" : obj.toString();

                dashUnit = DashManager.getInstance().getDashUnit(uri);
                if (dashUnit != null) {
                    dashUnit.setUriFileName(uriFileName);
                }
            } else { // 클라이언트한테 MPD 데이터 전달 후
                // TODO : SEND SEGMENT DATA
                /**
                 * NOT FOUND URI: /Users/.../Seoul_init_0.m4s
                 * NOT FOUND URI: /Users/.../Seoul_init_1.m4s
                 * NOT FOUND URI: /Users/.../Seoul_chunk_0_00001.m4s
                 * NOT FOUND URI: /Users/.../Seoul_chunk_1_00001.m4s
                 * NOT FOUND URI: /Users/.../Seoul_chunk_0_00002.m4s
                 * NOT FOUND URI: /Users/.../Seoul_chunk_1_00002.m4s
                 */
                content = new String(dashUnit.getSegmentByteData(uri), StandardCharsets.UTF_8);
                logger.debug("SEGMENT [{}] [len={}]", uri, content.length());
            }
            ///////////////////////////

            ///////////////////////////
            // RESPONSE
            writeResponse(ctx, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, content);
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
                false);

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
    }

    private static void send100Continue(final ChannelHandlerContext ctx) {
        ctx.write(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.CONTINUE));
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private String getFileNameFromUri(String uri) {
        if (!uri.contains(".")) { return null; }
        if (uri.contains("/")) {
            int lastSlashIndex = uri.lastIndexOf("/");
            if (lastSlashIndex == (uri.length() - 1)) { return null; }
            uri = uri.substring(lastSlashIndex + 1).trim();
        }
        uri = uri.substring(0, uri.lastIndexOf(".")).trim();
        return uri;
    }
    ////////////////////////////////////////////////////////////

}
