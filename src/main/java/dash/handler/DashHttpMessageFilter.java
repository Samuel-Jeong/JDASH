package dash.handler;

import dash.DashManager;
import dash.handler.definition.HttpMessageRoute;
import dash.handler.definition.HttpMessageRouteTable;
import dash.handler.definition.HttpRequest;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpMethod;
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

    //private final String serviceName;

    private final DashManager dashManager;
    private final String basePath;
    private final HttpMessageRouteTable uriRouteTable;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageFilter(HttpMessageRouteTable routeTable) {
        dashManager = ServiceManager.getInstance().getDashManager();
        //this.serviceName = dashManager.getServiceName();
        this.uriRouteTable = routeTable;
        this.basePath = AppInstance.getInstance().getConfigManager().getMediaBasePath();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        if (!(o instanceof FullHttpRequest)) {
            return;
        }

        ///////////////////////////
        // GET URI
        final FullHttpRequest request = (FullHttpRequest) o;
        if (HttpHeaderUtil.is100ContinueExpected(request)) {
            dashManager.send100Continue(channelHandlerContext);
        }

        final HttpMethod method = request.method();
        String uri = request.uri(); // [/Seoul.mp4] or [/Seoul_chunk_1_00001.m4s] or [aws/20210209/Seoul.mp4]
        if (uri == null) {
            logger.warn("[DashHttpMessageFilter] URI is not defined.");
            return;
        }
        uri = uri.trim();
        String originUri = uri;
        logger.debug("[DashHttpMessageFilter] [OriginUri={}] REQUEST: \n{}", originUri, request);
        ///////////////////////////

        ///////////////////////////
        // GET DASH UNIT
        boolean isRegistered = false;
        DashUnit dashUnit = null;
        String uriFileName = FileManager.getFileNameFromUri(uri); // [Seoul] or [Seoul_chunk_1_00001]
        if (uriFileName == null) {
            logger.warn("[DashHttpMessageFilter] URI is wrong. (uri={})", uri);
            return;
        }
        logger.debug("[DashHttpMessageFilter] uriFileName: {}", uriFileName);

        InetSocketAddress remoteAddress = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();
        String filePathWithoutExtensionFromUri = FileManager.getFilePathWithoutExtensionFromUri(originUri);
        String dashUnitKey = remoteAddress.getAddress().getHostAddress() + ":" + filePathWithoutExtensionFromUri;
        logger.debug("[DashHttpMessageFilter] dashUnitKey: [{}]", dashUnitKey);

        for (Map.Entry<String, DashUnit> entry : dashManager.getCloneDashMap().entrySet()) {
            if (entry == null) { continue; }

            DashUnit curDashUnit = entry.getValue();
            if (curDashUnit == null) { continue; }

            /*if (!curDashUnit.getId().contains(uriFileName)) { continue; }
            else {
                logger.debug("[DashHttpMessageFilter] curDashUnitKey: [{}]", dashUnitKey);
            }*/

            String curDashUnitUriFileName = FileManager.getFileNameFromUri(curDashUnit.getInputFilePath());
            if (curDashUnitUriFileName == null) { continue; }
            else {
                logger.debug("[DashHttpMessageFilter] requestedUriFileName:[{}], curDashUnitUriFileName: [{}]", uriFileName, curDashUnitUriFileName);
            }

            if (uriFileName.equals(curDashUnitUriFileName)) {
                dashUnit = curDashUnit;
                logger.debug("[DashHttpMessageFilter] RE-DEMANDED! [{}] <> [{}] (dashUnitId={})", uriFileName, curDashUnitUriFileName, dashUnit.getId());
                break;
            }

            if (uriFileName.contains(curDashUnitUriFileName)) {
                dashUnit = curDashUnit;
                isRegistered = true;
                logger.debug("[DashHttpMessageFilter] MATCHED! [{}] <> [{}] (dashUnitId={})", uriFileName, curDashUnitUriFileName, dashUnit.getId());
                uriFileName = curDashUnitUriFileName;
                break;
            }
        }

        if (dashUnit == null) {
            logger.warn("[DashHttpMessageFilter] NOT FOUND URI (Dash unit is not registered. Must use jdash.) : {}", uri);
            return;
        }

        String uriFileNameWithExtension = FileManager.getFileNameWithExtensionFromUri(uri);
        if (uriFileNameWithExtension != null && uriFileNameWithExtension.contains(".")) {
            String parentPathOfUri = FileManager.getParentPathFromUri(uri); // aws/20210209
            if (parentPathOfUri != null && !parentPathOfUri.isEmpty()) {
                parentPathOfUri = FileManager.concatFilePath(parentPathOfUri, uriFileName); // [aws/20210209/Seoul]
                uri = FileManager.concatFilePath(parentPathOfUri, uriFileNameWithExtension); // [aws/20210209/Seoul/Seoul.mp4] or [aws/20210209/Seoul/Seoul_chunk_1_00001.m4s]
            } else {
                uri = FileManager.concatFilePath(uriFileName, uri); // [Seoul/Seoul.mp4] or [Seoul/Seoul_chunk_1_00001.m4s]
            }
        }

        uri = FileManager.concatFilePath(basePath, uri); // [/Users/.../Seoul/Seoul.mp4] or [/Users/.../Seoul/Seoul_chunk_1_00001.m4s]
        request.setUri(uri);
        ///////////////////////////

        ///////////////////////////
        // ROUTING IF NOT REGISTERED
        HttpMessageRoute uriRoute = null;
        if (!isRegistered) {
            uriRoute = uriRouteTable.findUriRoute(method, uri);
            if (uriRoute == null) {
                logger.warn("[DashHttpMessageFilter] NOT FOUND URI (from the route table) : {}", uri);
                dashManager.writeNotFound(channelHandlerContext, request);
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

                dashManager.writeResponse(channelHandlerContext, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_DASH_XML, content);
            } else { // GET SEGMENT URI 수신 시 (not mpd uri)
                byte[] segmentBytes = dashUnit.getSegmentByteData(uri);
                logger.debug("[DashHttpMessageFilter] SEGMENT [{}] [len={}]", uri, segmentBytes.length);
                dashManager.writeResponse(channelHandlerContext, request, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, segmentBytes);
                //FileManager.deleteFile(uri);
            }
            ///////////////////////////
        } catch (final Exception e) {
            logger.warn("DashHttpHandler.messageReceived.Exception", e);
            dashManager.writeInternalServerError(channelHandlerContext, request);
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
