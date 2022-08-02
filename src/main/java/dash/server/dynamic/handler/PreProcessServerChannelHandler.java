package dash.server.dynamic.handler;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.dynamic.DynamicMediaManager;
import dash.server.dynamic.message.StreamingStartRequest;
import dash.server.dynamic.message.StreamingStartResponse;
import dash.server.dynamic.message.StreamingStopRequest;
import dash.server.dynamic.message.StreamingStopResponse;
import dash.server.dynamic.message.base.MessageHeader;
import dash.server.dynamic.message.base.MessageType;
import dash.server.dynamic.message.base.ResponseType;
import dash.server.handler.DashMessageHandler;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import network.definition.DestinationRecord;
import network.socket.GroupSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PreProcessServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(PreProcessServerChannelHandler.class);

    private final ConfigManager configManager;
    private final FileManager fileManager = new FileManager();

    private final List<String> validatedStreamNames = new ArrayList<>();
    ////////////////////////////////////////////////////////////////////////////////
    public PreProcessServerChannelHandler() {
        List<String> mediaListFileLines = fileManager.readAllLines(AppInstance.getInstance().getConfigManager().getMediaListPath());
        mediaListFileLines.stream()
                .filter(mediaListFileLine -> mediaListFileLine != null && !mediaListFileLine.isEmpty())
                .map(String::trim)
                .filter(mediaListFileLine -> !mediaListFileLine.startsWith("#"))
                .map(mediaListFileLine -> mediaListFileLine.split(","))
                .filter(elements -> elements.length == 2)
                .map(elements -> elements[1].trim())
                .forEach(validatedStreamNames::add);

        configManager = AppInstance.getInstance().getConfigManager();
    }
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) {
        try {
            DashServer dashServer = ServiceManager.getInstance().getDashServer();
            DynamicMediaManager dynamicMediaManager = dashServer.getDynamicMediaManager();

            GroupSocket groupSocket = dynamicMediaManager.getLocalGroupSocket();
            if (groupSocket == null) {
                logger.warn("[PreProcessServerChannelHandler]Listen socket is not found... Fail to process the request.");
                return;
            }

            DestinationRecord destinationRecord = groupSocket.getDestination(dynamicMediaManager.getSessionId());
            if (destinationRecord == null) {
                logger.warn("[PreProcessServerChannelHandler] DestinationRecord is not found... Fail to process the request.");
                return;
            }

            ByteBuf buf = datagramPacket.content();
            if (buf == null) {
                logger.warn("[PreProcessServerChannelHandler] DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[PreProcessServerChannelHandler] Message is null.");
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);
            if (data.length <= MessageHeader.SIZE) { return; }

            byte[] headerData = new byte[MessageHeader.SIZE];
            System.arraycopy(data, 0, headerData, 0, MessageHeader.SIZE);
            MessageHeader messageHeader = new MessageHeader(headerData);

            byte[] responseByteData;
            if (messageHeader.getMessageType() == MessageType.STREAMING_START_REQ) {
                StreamingStartRequest streamingStartRequest = new StreamingStartRequest(data);
                StreamingStartResponse streamingStartResponse;
                String sourceIp = streamingStartRequest.getSourceIp();
                String streamUri = streamingStartRequest.getUri();
                long expires = streamingStartRequest.getExpires();
                logger.debug("[PreProcessServerChannelHandler] RECV StreamingStartRequest(sourceIp={}, uri={}, expires={})", sourceIp, streamUri, expires);

                // CHECK URI is validated
                if (!checkUriIsValidated(streamUri)) {
                    streamingStartResponse = makeResponseForStreamingStart(dashServer, ResponseType.NOT_FOUND, ResponseType.REASON_NOT_FOUND);
                } else {
                    String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(streamUri);
                    logger.debug("[PreProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                    DashUnit dashUnit = dashServer.addDashUnit(
                            StreamType.DYNAMIC, dashUnitId,
                            null, expires, true
                    );

                    if (dashUnit != null) {
                        String networkPath = makeNetworkPath();
                        if (networkPath != null) {
                            if (startStreaming(networkPath, streamUri, dashUnit)) {
                                streamingStartResponse = makeResponseForStreamingStart(dashServer, ResponseType.SUCCESS, ResponseType.REASON_SUCCESS);
                            } else {
                                dashServer.deleteDashUnit(dashUnitId);
                                streamingStartResponse = makeResponseForStreamingStart(dashServer, ResponseType.FORBIDDEN, ResponseType.REASON_FAIL_TO_ACCESS);
                            }
                        } else {
                            dashServer.deleteDashUnit(dashUnitId);
                            streamingStartResponse = makeResponseForStreamingStart(dashServer, ResponseType.NOT_FOUND, ResponseType.REASON_NOT_FOUND);
                        }
                    } else {
                        streamingStartResponse = makeResponseForStreamingStart(dashServer, ResponseType.FORBIDDEN, ResponseType.REASON_RESOURCE_FULL);
                    }
                }

                responseByteData = streamingStartResponse.getByteData();
                logger.debug("[PreProcessServerChannelHandler] SEND StreamingStartResponse(sourceIp={}, uri={}, streamingStartResponse=\n{})", sourceIp, streamUri, streamingStartResponse);
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_STOP_REQ) {
                StreamingStopRequest streamingStopRequest = new StreamingStopRequest(data);
                String sourceIp = streamingStopRequest.getSourceIp();
                String uri = streamingStopRequest.getUri();
                logger.debug("[PreProcessServerChannelHandler] RECV StreamingStopRequest(sourceIp={}, uri={})", sourceIp, uri);

                String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(uri);
                logger.debug("[PreProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                DashUnit dashUnit = dashServer.getDashUnitById(dashUnitId);

                StreamingStopResponse streamingStopResponse;
                if (dashUnit == null) {
                    logger.warn("[PreProcessServerChannelHandler] DashUnit is not exist! (id={})", dashUnitId);
                    streamingStopResponse = makeResponseForStreamingStop(dashServer, ResponseType.NOT_FOUND, ResponseType.REASON_NOT_FOUND);
                } else {
                    dashServer.deleteDashUnit(dashUnitId);

                    logger.debug("[PreProcessServerChannelHandler] DashUnit[{}]'s pre live media process is finished. (request={}", dashUnitId, streamingStopRequest);
                    streamingStopResponse = makeResponseForStreamingStop(dashServer, ResponseType.SUCCESS, ResponseType.REASON_SUCCESS);
                }
                responseByteData = streamingStopResponse.getByteData();
                logger.debug("[PreProcessServerChannelHandler] SEND StreamingStopResponse(sourceIp={}, uri={}, streamingStopResponse=\n{})", sourceIp, uri, streamingStopResponse);
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_START_RES) {
                StreamingStartResponse streamingStartResponse = new StreamingStartResponse(data);
                int statusCode = streamingStartResponse.getStatusCode();
                String reason = streamingStartResponse.getReason();
                logger.debug("[PreProcessClientChannelHandler] RECV StreamingStartResponse(statusCode={}, reason={})", statusCode, reason);

                if (statusCode == ResponseType.NOT_FOUND) {
                    logger.debug("[PreProcessClientChannelHandler] RECV StreamingStartResponse [404 NOT FOUND], Fail to start service.");
                    System.exit(1);
                }
                responseByteData = null;
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_STOP_RES) {
                StreamingStopResponse streamingStopResponse = new StreamingStopResponse(data);
                int statusCode = streamingStopResponse.getStatusCode();
                String reason = streamingStopResponse.getReason();
                logger.debug("[PreProcessClientChannelHandler] RECV StreamingStopResponse(statusCode={}, reason={})", statusCode, reason);
                responseByteData = null;
            } else {
                logger.debug("[PreProcessClientChannelHandler] RECV UnknownResponse (header={})", messageHeader);
                responseByteData = null;
            }

            if (responseByteData != null) {
                destinationRecord.getNettyChannel().sendData(responseByteData, responseByteData.length);
            }
        } catch (Exception e) {
            logger.warn("PreProcessServerChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("PreProcessServerChannelHandler is inactive.");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("PreProcessServerChannelHandler.Exception", cause);
        ctx.close();
    }

    ////////////////////////////////////////////////////////////////////////////////

    private StreamingStartResponse makeResponseForStreamingStart(DashServer dashServer, int responseTypeCode, String responseTypeString) {
        return new StreamingStartResponse(
                new MessageHeader(
                        DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                        MessageType.STREAMING_START_RES,
                        dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                        System.currentTimeMillis(),
                        StreamingStartResponse.MIN_SIZE + responseTypeString.length()
                ),
                responseTypeCode,
                responseTypeString.length(),
                responseTypeString
        );
    }

    private StreamingStopResponse makeResponseForStreamingStop(DashServer dashServer, int responseTypeCode, String responseTypeString) {
        return new StreamingStopResponse(
                new MessageHeader(
                        DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                        MessageType.STREAMING_STOP_RES,
                        dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                        System.currentTimeMillis(),
                        StreamingStartResponse.MIN_SIZE + responseTypeString.length()
                ),
                responseTypeCode,
                responseTypeString.length(),
                responseTypeString
        );
    }

    private boolean checkUriIsValidated(String uri) {
        if (uri == null || uri.isEmpty()) { return false; }

        if (uri.startsWith("/")) { uri = uri.substring(1); }
        for (String validatedStreamName : validatedStreamNames) {
            if (validatedStreamName == null || validatedStreamName.isEmpty()) { continue; }

            if (validatedStreamName.startsWith("/")) { validatedStreamName = validatedStreamName.substring(1); }
            if (validatedStreamName.equals(uri)) {
                return true;
            }
        }

        return false;
    }

    private String makeNetworkPath() {
        if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
            return StreamConfigManager.RTMP_PREFIX + configManager.getRtmpServerIp() + ":" + configManager.getRtmpServerPort();
        } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
            return StreamConfigManager.HTTP_PREFIX + configManager.getHttpTargetIp() + ":" + configManager.getHttpTargetPort();
        }
        return null;
    }

    private boolean startStreaming(String networkPath, String streamUri, DashUnit dashUnit) {
        String sourceUri = fileManager.concatFilePath(networkPath, streamUri);
        String mpdPath = fileManager.concatFilePath(configManager.getMediaBasePath(), streamUri);
        String mpdParentPath = mpdPath;

        File mpdPathFile = new File(mpdParentPath);
        if (!mpdPathFile.exists()) {
            if (mpdPathFile.mkdirs()) {
                logger.debug("[PreProcessServerChannelHandler] Parent mpd path is created. (parentMpdPath={}, streamUri={}, sourceUri={})", mpdPath, streamUri, sourceUri);
            }
        }

        String uriFileName = fileManager.getFileNameFromUri(streamUri);
        mpdPath = fileManager.concatFilePath(mpdPath, uriFileName + StreamConfigManager.DASH_POSTFIX);
        dashUnit.setInputFilePath(sourceUri);
        dashUnit.setOutputFilePath(mpdPath);
        dashUnit.setMpdParentPath(mpdParentPath);

        if (dashUnit.runLiveStreaming(uriFileName, sourceUri, mpdPath, dashUnit.getDashClient().getMpdManager())) {
            logger.debug("[PreProcessServerChannelHandler] Success to run the streaming. (id={}, localMpdPath={}, streamUri={}, sourceUri={})",
                    dashUnit.getId(), mpdPath, streamUri, sourceUri
            );
            ServiceManager.getInstance().getDashServer().getHttpMessageManager().get(dashUnit.getMpdParentPath(), new DashMessageHandler(dashUnit.getMpdParentPath()));
            ServiceManager.getInstance().getDashServer().getHttpMessageManager().get(dashUnit.getOutputFilePath(), new DashMessageHandler(dashUnit.getOutputFilePath()));
            return true;
        } else {
            logger.warn("[PreProcessServerChannelHandler] Fail to run the streaming. (id={}, localMpdPath={}, streamUri={}, sourceUri={})",
                    dashUnit.getId(), mpdPath, streamUri, sourceUri
            );
            return false;
        }
    }

}
