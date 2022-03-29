package dash.server.handler;

import dash.server.DashServer;
import dash.server.handler.definition.HttpMessageRoute;
import dash.server.handler.definition.HttpMessageRouteTable;
import dash.server.handler.definition.HttpRequest;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import util.module.FileManager;

import java.net.InetSocketAddress;
import java.util.Map;

public class DashHttpMessageFilter extends SimpleChannelInboundHandler<Object> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashHttpMessageFilter.class);

    private final FileManager fileManager = new FileManager();

    private final DashServer dashServer;
    private final String basePath;
    private final HttpMessageRouteTable uriRouteTable;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageFilter(HttpMessageRouteTable routeTable) {
        dashServer = ServiceManager.getInstance().getDashServer();
        this.uriRouteTable = routeTable;
        this.basePath = AppInstance.getInstance().getConfigManager().getMediaBasePath();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, Object o) {
        if (!(o instanceof FullHttpRequest)) {
            return;
        }

        ///////////////////////////
        // GET URI
        final FullHttpRequest request = (FullHttpRequest) o;
        if (HttpHeaderUtil.is100ContinueExpected(request)) {
            dashServer.send100Continue(channelHandlerContext);
        }

        /**
         * [live/test]
         * [vod/test_chunk_1_00001.m4s]
         * [vod/test.mp4]
         * [vod/test.mpd]
         */
        String uri = request.uri();
        if (uri == null) {
            logger.warn("[DashHttpMessageFilter] URI is not defined.");
            return;
        }
        uri = uri.trim();
        String originUri = uri;
        if (originUri.contains("bad-request")) { return; }
        logger.trace("[DashHttpMessageFilter] [OriginUri={}] REQUEST: \n{}", originUri, request);
        ///////////////////////////

        ///////////////////////////
        // GET DASH UNIT
        boolean isRegistered = false;
        DashUnit dashUnit = null;
        String uriFileName = fileManager.getFileNameFromUri(uri); // [Seoul] or [Seoul_chunk_1_00001]
        if (uriFileName == null) {
            logger.warn("[DashHttpMessageFilter] URI is wrong. (uri={})", uri);
            return;
        }
        logger.trace("[DashHttpMessageFilter] uriFileName: {}", uriFileName);

        InetSocketAddress remoteAddress = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();
        String filePathWithoutExtensionFromUri = fileManager.getFilePathWithoutExtensionFromUri(originUri);
        String dashUnitKey = remoteAddress.getAddress().getHostAddress() + ":" + filePathWithoutExtensionFromUri;
        logger.trace("[DashHttpMessageFilter] dashUnitKey: [{}]", dashUnitKey);

        for (Map.Entry<String, DashUnit> entry : dashServer.getCloneDashMap().entrySet()) {
            if (entry == null) { continue; }

            DashUnit curDashUnit = entry.getValue();
            if (curDashUnit == null) { continue; }

            String curDashUnitUriFileName = fileManager.getFileNameFromUri(curDashUnit.getInputFilePath());
            if (curDashUnitUriFileName == null) { continue; }
            else {
                logger.trace("[DashHttpMessageFilter] requestedUriFileName:[{}], curDashUnitUriFileName: [{}]", uriFileName, curDashUnitUriFileName);
            }

            if (uriFileName.equals(curDashUnitUriFileName)) {
                dashUnit = curDashUnit;
                logger.trace("[DashHttpMessageFilter] RE-DEMANDED! [{}] <> [{}] (dashUnitId={})", uriFileName, curDashUnitUriFileName, dashUnit.getId());
                break;
            }

            if (uriFileName.contains(curDashUnitUriFileName)) {
                dashUnit = curDashUnit;
                isRegistered = true;
                logger.trace("[DashHttpMessageFilter] MATCHED! [{}] <> [{}] (dashUnitId={})", uriFileName, curDashUnitUriFileName, dashUnit.getId());
                uriFileName = curDashUnitUriFileName;
                break;
            }
        }

        if (dashUnit == null) {
            logger.warn("[DashHttpMessageFilter] NOT FOUND URI (Dash unit is not registered. Must use dash client.) : {}", uri);
            return;
        }

        String uriFileNameWithExtension = fileManager.getFileNameWithExtensionFromUri(uri);
        if (uriFileNameWithExtension != null && uriFileNameWithExtension.contains(".")) {
            String parentPathOfUri = fileManager.getParentPathFromUri(uri); // aws/20210209
            if (parentPathOfUri != null && !parentPathOfUri.isEmpty()) {
                parentPathOfUri = fileManager.concatFilePath(parentPathOfUri, uriFileName); // [aws/20210209/Seoul]
                uri = fileManager.concatFilePath(parentPathOfUri, uriFileNameWithExtension); // [aws/20210209/Seoul/Seoul.mp4] or [aws/20210209/Seoul/Seoul_chunk_1_00001.m4s]
            } else {
                uri = fileManager.concatFilePath(uriFileName, uri); // [Seoul/Seoul.mp4] or [Seoul/Seoul_chunk_1_00001.m4s]
            }
        }

        uri = fileManager.concatFilePath(basePath, uri); // [/Users/.../Seoul/Seoul.mp4] or [/Users/.../Seoul/Seoul_chunk_1_00001.m4s]
        request.setUri(uri);
        ///////////////////////////

        ///////////////////////////
        // ROUTING IF NOT REGISTERED
        HttpMessageRoute uriRoute = null;
        if (!isRegistered) {
            uriRoute = uriRouteTable.findUriRoute(request.method(), uri);
            if (uriRoute == null) {
                logger.warn("[DashHttpMessageFilter] NOT FOUND URI (from the route table) : {}", uri);
                dashServer.writeNotFound(channelHandlerContext, request);
                return;
            }
        }
        ///////////////////////////

        try {
            ///////////////////////////
            // PROCESS URI
            if (!isRegistered) { // GET MPD URI 수신 시 (not segment uri)
                final HttpRequest requestWrapper = new HttpRequest(request);
                final Object obj = uriRoute.getHandler().handle(requestWrapper, null, originUri, uriFileName, channelHandlerContext, dashUnit);
                String content = obj == null ? "" : obj.toString();

                dashServer.writeResponse(channelHandlerContext, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_DASH_XML, content);
            } else { // GET SEGMENT URI 수신 시 (not mpd uri)
                byte[] segmentBytes = dashUnit.getSegmentByteData(uri);
                if (segmentBytes != null) {
                    logger.debug("[DashHttpMessageFilter] SEGMENT [{}] [len={}]", uri, segmentBytes.length);
                    dashServer.writeResponse(channelHandlerContext, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, segmentBytes);
                    //FileManager.deleteFile(uri);
                } else {
                    logger.warn("[DashHttpMessageFilter] The segment file is not exist. (uri={})", uri);
                    dashServer.writeNotFound(channelHandlerContext, request);
                }
            }
            ///////////////////////////
        } catch (final Exception e) {
            logger.warn("DashHttpMessageFilter.messageReceived.Exception", e);
            dashServer.writeInternalServerError(channelHandlerContext, request);
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.close();
    }
    ////////////////////////////////////////////////////////////

}
