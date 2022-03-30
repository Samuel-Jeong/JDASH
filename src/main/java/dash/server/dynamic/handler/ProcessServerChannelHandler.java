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

public class ProcessServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ProcessServerChannelHandler.class);

    private final ConfigManager configManager;
    private final FileManager fileManager = new FileManager();

    private final List<String> validatedStreamNames = new ArrayList<>();
    ////////////////////////////////////////////////////////////////////////////////
    public ProcessServerChannelHandler() {
        List<String> mediaListFileLines = fileManager.readAllLines(AppInstance.getInstance().getConfigManager().getMediaListPath());
        for (String mediaListFileLine : mediaListFileLines) {
            if (mediaListFileLine == null || mediaListFileLine.isEmpty()) { continue; }

            mediaListFileLine = mediaListFileLine.trim();
            if (mediaListFileLine.startsWith("#")) { continue; }

            String[] elements = mediaListFileLine.split(",");
            if (elements.length != 2) { continue; }

            validatedStreamNames.add(elements[1].trim());
        }

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
                logger.warn("[ProcessServerChannelHandler]Listen socket is not found... Fail to process the request.");
                return;
            }

            DestinationRecord destinationRecord = groupSocket.getDestination(dynamicMediaManager.getSessionId());
            if (destinationRecord == null) {
                logger.warn("[ProcessServerChannelHandler] DestinationRecord is not found... Fail to process the request.");
                return;
            }

            ByteBuf buf = datagramPacket.content();
            if (buf == null) {
                logger.warn("[ProcessServerChannelHandler] DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[ProcessServerChannelHandler] Message is null.");
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
                String uri = streamingStartRequest.getUri();
                long expires = streamingStartRequest.getExpires();
                logger.debug("[ProcessServerChannelHandler] RECV StreamingStartRequest(sourceIp={}, uri={}, expires={})", sourceIp, uri, expires);

                // CHECK URI is validated
                if (!checkUriIsValidated(uri)) {
                    streamingStartResponse = new StreamingStartResponse(
                            new MessageHeader(
                                    DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.STREAMING_START_RES,
                                    dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    StreamingStartResponse.MIN_SIZE + ResponseType.REASON_NOT_FOUND.length()
                            ),
                            ResponseType.NOT_FOUND,
                            ResponseType.REASON_NOT_FOUND.length(),
                            ResponseType.REASON_NOT_FOUND
                    );
                } else {
                    String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(uri);
                    logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                    DashUnit dashUnit = dashServer.addDashUnit(
                            StreamType.DYNAMIC, dashUnitId,
                            null, expires, true
                    );

                    if (dashUnit != null) {
                        String networkPath = "";
                        if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_RTMP)) {
                            networkPath = StreamConfigManager.RTMP_PREFIX + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
                        } else if (configManager.getStreaming().equals(StreamConfigManager.STREAMING_WITH_DASH)) {
                            networkPath = StreamConfigManager.HTTP_PREFIX + configManager.getHttpTargetIp() + ":" + configManager.getHttpTargetPort();
                        }

                        String sourceUri = fileManager.concatFilePath(networkPath, uri);
                        String mpdPath = fileManager.concatFilePath(configManager.getMediaBasePath(), uri);
                        File mpdPathFile = new File(mpdPath);
                        if (!mpdPathFile.exists()) {
                            if (mpdPathFile.mkdirs()) {
                                logger.debug("[ProcessServerChannelHandler] Parent mpd path is created. (parentMpdPath={}, uri={}, sourceUri={})", mpdPath, uri, sourceUri);
                            }
                        }

                        String uriFileName = fileManager.getFileNameFromUri(uri);
                        mpdPath = fileManager.concatFilePath(mpdPath, uriFileName + StreamConfigManager.DASH_POSTFIX);
                        logger.debug("[ProcessServerChannelHandler] Final mpd path: {} (uri={}, sourceUri={})", mpdPath, uri, sourceUri);

                        dashUnit.setInputFilePath(sourceUri);
                        dashUnit.setOutputFilePath(mpdPath);

                        ///////////////////////////
                        dashUnit.runLiveStreaming(uriFileName, sourceUri, mpdPath);
                        ///////////////////////////

                        logger.debug("[ProcessServerChannelHandler] DashUnit is created successfully. (id={}, request={})", dashUnitId, streamingStartRequest);

                        streamingStartResponse = new StreamingStartResponse(
                                new MessageHeader(
                                        DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                        MessageType.STREAMING_START_RES,
                                        dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                        System.currentTimeMillis(),
                                        StreamingStartResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                                ),
                                ResponseType.SUCCESS,
                                ResponseType.REASON_SUCCESS.length(),
                                ResponseType.REASON_SUCCESS
                        );
                    } else {
                        streamingStartResponse = new StreamingStartResponse(
                                new MessageHeader(
                                        DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                        MessageType.STREAMING_START_RES,
                                        dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                        System.currentTimeMillis(),
                                        StreamingStartResponse.MIN_SIZE + ResponseType.REASON_RESOURCE_FULL.length()
                                ),
                                ResponseType.FORBIDDEN,
                                ResponseType.REASON_RESOURCE_FULL.length(),
                                ResponseType.REASON_RESOURCE_FULL
                        );
                    }
                }
                responseByteData = streamingStartResponse.getByteData();
                logger.debug("[ProcessServerChannelHandler] SEND StreamingStartResponse(sourceIp={}, uri={}, streamingStartResponse=\n{})", sourceIp, uri, streamingStartResponse);
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_STOP_REQ) {
                StreamingStopRequest streamingStopRequest = new StreamingStopRequest(data);
                String sourceIp = streamingStopRequest.getSourceIp();
                String uri = streamingStopRequest.getUri();
                logger.debug("[ProcessServerChannelHandler] RECV StreamingStopRequest(sourceIp={}, uri={})", sourceIp, uri);

                String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(uri);
                logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                DashUnit dashUnit = dashServer.getDashUnitById(dashUnitId);

                StreamingStopResponse streamingStopResponse;
                if (dashUnit == null) {
                    logger.warn("[ProcessServerChannelHandler] DashUnit is not exist! (id={})", dashUnitId);
                    
                    streamingStopResponse = new StreamingStopResponse(
                            new MessageHeader(
                                    DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.STREAMING_STOP_RES,
                                    dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    StreamingStartResponse.MIN_SIZE + ResponseType.REASON_NOT_FOUND.length()
                            ),
                            ResponseType.NOT_FOUND,
                            ResponseType.REASON_NOT_FOUND.length(),
                            ResponseType.REASON_NOT_FOUND
                    );
                } else {
                    ///////////////////////////
                    dashServer.deleteDashUnit(dashUnitId);
                    ///////////////////////////

                    logger.debug("[ProcessServerChannelHandler] DashUnit[{}]'s pre live media process is finished. (request={}", dashUnitId, streamingStopRequest);

                    streamingStopResponse = new StreamingStopResponse(
                            new MessageHeader(
                                    DynamicMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.STREAMING_STOP_RES,
                                    dashServer.getDynamicMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    StreamingStartResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                            ),
                            ResponseType.SUCCESS,
                            ResponseType.REASON_SUCCESS.length(),
                            ResponseType.REASON_SUCCESS
                    );
                }
                responseByteData = streamingStopResponse.getByteData();
                logger.debug("[ProcessServerChannelHandler] SEND StreamingStopResponse(sourceIp={}, uri={}, streamingStopResponse=\n{})", sourceIp, uri, streamingStopResponse);
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_START_RES) {
                StreamingStartResponse streamingStartResponse = new StreamingStartResponse(data);
                int statusCode = streamingStartResponse.getStatusCode();
                String reason = streamingStartResponse.getReason();
                logger.debug("[ProcessClientChannelHandler] RECV StreamingStartResponse(statusCode={}, reason={})", statusCode, reason);

                if (statusCode == ResponseType.NOT_FOUND) {
                    logger.debug("[ProcessClientChannelHandler] RECV StreamingStartResponse [404 NOT FOUND], Fail to start service.");
                    //ServiceManager.getInstance().stop();
                    System.exit(1);
                }
                responseByteData = null;
            } else if (messageHeader.getMessageType() == MessageType.STREAMING_STOP_RES) {
                StreamingStopResponse streamingStopResponse = new StreamingStopResponse(data);
                int statusCode = streamingStopResponse.getStatusCode();
                String reason = streamingStopResponse.getReason();
                logger.debug("[ProcessClientChannelHandler] RECV StreamingStopResponse(statusCode={}, reason={})", statusCode, reason);
                responseByteData = null;
            } else {
                logger.debug("[ProcessClientChannelHandler] RECV UnknownResponse (header={})", messageHeader);
                responseByteData = null;
            }

            if (responseByteData != null) {
                destinationRecord.getNettyChannel().sendData(responseByteData, responseByteData.length);
            }
        } catch (Exception e) {
            logger.warn("ProcessServerChannelHandler.channelRead0.Exception", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.warn("ProcessServerChannelHandler is inactive.");
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("ProcessServerChannelHandler.Exception", cause);
        ctx.close();
    }

    ////////////////////////////////////////////////////////////////////////////////

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

}
