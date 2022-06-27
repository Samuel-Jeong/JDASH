package dash.server.handler;

import config.ConfigManager;
import dash.client.DashClient;
import dash.mpd.MpdManager;
import dash.server.DashServer;
import dash.server.handler.definition.HttpMessageRoute;
import dash.server.handler.definition.HttpMessageRouteTable;
import dash.server.handler.definition.HttpRequest;
import dash.unit.DashUnit;
import dash.unit.segment.MediaSegmentController;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.StreamConfigManager;
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

    private final ConfigManager configManager;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashHttpMessageFilter(HttpMessageRouteTable routeTable) {
        dashServer = ServiceManager.getInstance().getDashServer();
        this.uriRouteTable = routeTable;
        this.basePath = AppInstance.getInstance().getConfigManager().getMediaBasePath();
        this.configManager = AppInstance.getInstance().getConfigManager();
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
        if (segmentBytes != null && segmentBytes.length > 0) {
            if (!parseSegmentInfoForDash(channelHandlerContext, httpRequest, dashUnit, localUri)) { return; }

            logger.debug("[DashHttpMessageFilter] SEGMENT [{}] [len={}]", localUri, segmentBytes.length);
            dashServer.writeResponse(channelHandlerContext, httpRequest, HttpResponseStatus.OK, HttpMessageManager.TYPE_PLAIN, segmentBytes);
        } else {
            logger.warn("[DashHttpMessageFilter] The segment file is not exist. (uri={})", localUri);
            dashServer.writeNotFound(channelHandlerContext, httpRequest);
        }
    }

    private boolean parseSegmentInfoForDash(ChannelHandlerContext channelHandlerContext, FullHttpRequest httpRequest, DashUnit dashUnit, String localUri) {
        if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) { // Dash 스트리밍일 때만 수행, 아니면 통과
            int initStringIndex = localUri.indexOf("init");
            if (initStringIndex < 0) { // 초기화 세그먼트는 파싱하지 않고 통과
                // URI 에서 비디오인지 오디오인지 판별해서 요청된 세그먼트 번호를 저장해야 한다.
                // RequestedUri : cgTnoWWP_chunk0_00008.m4s > RepresentationID : 0, SegmentNumber : 8
                // UDashUri : cgTnoWWP_chunk$RepresentationID$_$Number%05d$.m4s
                // 1) Parse Representation ID
                int representationId;
                int segmentNumber;
                int chunkStringIndex = localUri.indexOf("chunk");
                if (chunkStringIndex >= 0) { // 미디어 세그먼트만 파싱
                    int chunkStringLength = "chunk".length();
                    int representationIdIndex = chunkStringIndex + chunkStringLength;
                    if (representationIdIndex + 1 >= localUri.length()) {
                        logger.warn("[DashHttpMessageFilter({})] Fail to get a representation id. (uri={})", dashUnit.getId(), localUri);
                        dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                        return false;
                    }

                    String representationIdString = localUri.substring(representationIdIndex, representationIdIndex + 1);
                    try {
                        // 2) Parse Segment Number
                        int segmentNumberIndex = localUri.lastIndexOf("_"); // _00008.m4s
                        if (segmentNumberIndex < 0) {
                            logger.warn("[DashHttpMessageFilter({})] Fail to find the segment number index. (uri={})", dashUnit.getId(), localUri);
                            dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                            return false;
                        }

                        int fileExtensionIndex = localUri.lastIndexOf("."); // .m4s
                        if (fileExtensionIndex < 0) {
                            logger.warn("[DashHttpMessageFilter({})] Fail to find the file extension index. (uri={})", dashUnit.getId(), localUri);
                            dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                            return false;
                        }

                        String segmentNumberString = localUri.substring(segmentNumberIndex + 1, fileExtensionIndex); // 00008
                        try {
                            segmentNumber = Integer.parseInt(segmentNumberString); // 8
                        } catch (NumberFormatException e) {
                            logger.warn("[DashHttpMessageFilter({})] Fail to parse integer. (SegmentNumber) (value={}, uri={})", dashUnit.getId(), segmentNumberString, localUri);
                            dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                            return false;
                        }

                        DashClient dashClient = dashUnit.getDashClient();
                        if (dashClient != null) { // DashClient 는 Dash 스트리밍인 경우에만 활성화된다.
                            MpdManager mpdManager = dashClient.getMpdManager();

                            int curAudioIndex = mpdManager.getCurAudioIndex();
                            int curVideoIndex = mpdManager.getCurVideoIndex();
                            representationId = Integer.parseInt(representationIdString);

                            if (representationId == curAudioIndex) { // 요청된 오디오 세그먼트 번호 등록 > 통과
                                MediaSegmentController audioSegmentController = dashClient.getAudioSegmentController();
                                if (audioSegmentController != null) {
                                    audioSegmentController.getMediaSegmentInfo().setRequestedSegmentNumber(segmentNumber);
                                }
                            } else if (representationId == curVideoIndex) { // 요청된 비디오 세그먼트 번호 등록 > 통과
                                MediaSegmentController videoSegmentController = dashClient.getVideoSegmentController();
                                if (videoSegmentController != null) {
                                    videoSegmentController.getMediaSegmentInfo().setRequestedSegmentNumber(segmentNumber);
                                }
                            } else { // 전달받은 MPD 정보와 일치하지 않음 (RepresentationID for audio, video)
                                logger.warn("[DashHttpMessageFilter({})] Fail to match with the mpd manager. (value={}, audioIndex={}, videoIndex={}, segmentNumber={})",
                                        dashUnit.getId(), representationId, curAudioIndex, curVideoIndex, segmentNumber
                                );
                                dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                                return false;
                            }
                        } else {
                            logger.warn("[DashHttpMessageFilter({})] Fail to get the dash client. (segmentNumber={})", dashUnit.getId(), segmentNumber);
                            dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                            return false;
                        }
                    } catch(NumberFormatException e) {
                        logger.warn("[DashHttpMessageFilter({})] Fail to parse integer. (RepresentationID) (value={}, uri={})", dashUnit.getId(), representationIdString, localUri);
                        dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                        return false;
                    }
                } else {
                    logger.warn("[DashHttpMessageFilter({})] Uri is wrong. Fail to find the chunk info from the uri. (uri={})", dashUnit.getId(), localUri);
                    dashServer.writeBadRequestError(channelHandlerContext, httpRequest);
                    return false;
                }
            } else {
                return true;
            }
        }

        return true;
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
