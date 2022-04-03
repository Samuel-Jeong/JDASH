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

        // GET URI
        final FullHttpRequest httpRequest = (FullHttpRequest) o;
        if (HttpHeaderUtil.is100ContinueExpected(httpRequest)) {
            dashServer.send100Continue(channelHandlerContext);
        }

        /**
         * [live/test]
         * [vod/test_chunk_1_00001.m4s]
         * [vod/test.mp4]
         * [vod/test.mpd]
         */
        String uri = httpRequest.uri();
        if (uri == null) {
            logger.warn("[DashHttpMessageFilter] URI is not defined.");
            return;
        }
        uri = uri.trim();
        String originUri = uri;
        if (originUri.contains("bad-request")) { return; }
        logger.trace("[DashHttpMessageFilter] [OriginUri={}] REQUEST: \n{}", originUri, httpRequest);

        // GET DASH UNIT
        boolean isRegistered = false;
        DashUnit dashUnit = null;
        String uriFileName = fileManager.getFileNameFromUri(originUri); // [Seoul] or [Seoul_chunk_1_00001]
        if (uriFileName == null) {
            logger.warn("[DashHttpMessageFilter] URI is wrong. (uri={})", originUri);
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
            logger.warn("[DashHttpMessageFilter] NOT FOUND URI (Dash unit is not registered. Must use dash client.) : {}", originUri);
            return;
        }

        String localUri = makeLocalUri(originUri, uriFileName);
        httpRequest.setUri(localUri);

        // ROUTING IF NOT REGISTERED
        HttpMessageRoute httpMessageRoute = null;
        if (!isRegistered) {
            httpMessageRoute = uriRouteTable.findUriRoute(httpRequest.method(), localUri);
            if (httpMessageRoute == null) {
                logger.warn("[DashHttpMessageFilter] NOT FOUND URI (from the route table) : {}", localUri);
                dashServer.writeNotFound(channelHandlerContext, httpRequest);
                return;
            }
        }

        try {
            if (!isRegistered) { // GET MPD URI 수신 시 (not segment uri)
                processMpdRequest(channelHandlerContext, httpRequest, dashUnit, httpMessageRoute, originUri, uriFileName);
            } else { // GET SEGMENT URI 수신 시 (not mpd uri)
                processSegmentRequest(channelHandlerContext, httpRequest, dashUnit, localUri);
            }
        } catch (final Exception e) {
            logger.warn("DashHttpMessageFilter.messageReceived.Exception", e);
            dashServer.writeInternalServerError(channelHandlerContext, httpRequest);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    private String makeLocalUri(String remoteUri, String uriFileName) {
        String uriFileNameWithExtension = fileManager.getFileNameWithExtensionFromUri(remoteUri);
        if (uriFileNameWithExtension != null && uriFileNameWithExtension.contains(".")) {
            String parentPathOfUri = fileManager.getParentPathFromUri(remoteUri); // aws/20210209
            if (parentPathOfUri != null && !parentPathOfUri.isEmpty()) {
                parentPathOfUri = fileManager.concatFilePath(parentPathOfUri, uriFileName); // [aws/20210209/Seoul]
                remoteUri = fileManager.concatFilePath(parentPathOfUri, uriFileNameWithExtension); // [aws/20210209/Seoul/Seoul.mp4] or [aws/20210209/Seoul/Seoul_chunk_1_00001.m4s]
            } else {
                remoteUri = fileManager.concatFilePath(uriFileName, remoteUri); // [Seoul/Seoul.mp4] or [Seoul/Seoul_chunk_1_00001.m4s]
            }
        }
        return fileManager.concatFilePath(basePath, remoteUri); // [/Users/.../Seoul/Seoul.mp4] or [/Users/.../Seoul/Seoul_chunk_1_00001.m4s]
    }

    private void processMpdRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest,
                                   DashUnit dashUnit, HttpMessageRoute httpMessageRoute,
                                   String originUri, String uriFileName) throws Exception {
        final HttpRequest requestWrapper = new HttpRequest(httpRequest);
        final Object obj = httpMessageRoute.getHandler().handle(
                requestWrapper, null,
                originUri, uriFileName,
                channelHandlerContext, dashUnit
        );

        if (obj == null) {
            dashServer.writeNotFound(channelHandlerContext, httpRequest);
        } else {
            String content = obj.toString();
            dashServer.writeResponse(channelHandlerContext, httpRequest, HttpResponseStatus.OK, HttpMessageManager.TYPE_DASH_XML, content);
        }
    }

    private void processSegmentRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest,
                                       DashUnit dashUnit, String localUri) {
        byte[] segmentBytes = dashUnit.getSegmentByteData(localUri);
        if (segmentBytes != null) {
            logger.debug("[DashHttpMessageFilter] SEGMENT [{}] [len={}]", localUri, segmentBytes.length);
            dashServer.writeResponse(channelHandlerContext, httpRequest, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, segmentBytes);
        } else {
            logger.warn("[DashHttpMessageFilter] The segment file is not exist. (uri={})", localUri);
            dashServer.writeNotFound(channelHandlerContext, httpRequest);
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

}
