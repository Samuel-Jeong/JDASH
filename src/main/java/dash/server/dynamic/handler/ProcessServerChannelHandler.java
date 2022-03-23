package dash.server.dynamic.handler;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.dynamic.PreProcessMediaManager;
import dash.server.dynamic.message.EndLiveMediaProcessRequest;
import dash.server.dynamic.message.EndLiveMediaProcessResponse;
import dash.server.dynamic.message.PreLiveMediaProcessRequest;
import dash.server.dynamic.message.PreLiveMediaProcessResponse;
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
            PreProcessMediaManager preProcessMediaManager = dashServer.getPreProcessMediaManager();

            GroupSocket groupSocket = preProcessMediaManager.getLocalGroupSocket();
            if (groupSocket == null) {
                logger.warn("[ProcessServerChannelHandler]Listen socket is not found... Fail to process the request.");
                return;
            }

            DestinationRecord destinationRecord = groupSocket.getDestination(preProcessMediaManager.getSessionId());
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
            if (messageHeader.getMessageType() == MessageType.PREPROCESS_REQ) {
                PreLiveMediaProcessRequest preProcessRequest = new PreLiveMediaProcessRequest(data);
                PreLiveMediaProcessResponse preProcessResponse;
                String sourceIp = preProcessRequest.getSourceIp();
                String uri = preProcessRequest.getUri();
                long expires = preProcessRequest.getExpires();
                logger.debug("[ProcessServerChannelHandler] RECV PreProcessRequest(sourceIp={}, uri={}, expires={})", sourceIp, uri, expires);

                // CHECK URI is validated
                if (!checkUriIsValidated(uri)) {
                    preProcessResponse = new PreLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.PREPROCESS_RES,
                                    dashServer.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_NOT_FOUND.length()
                            ),
                            ResponseType.NOT_FOUND,
                            ResponseType.REASON_NOT_FOUND.length(),
                            ResponseType.REASON_NOT_FOUND
                    );
                } else {
                    String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(uri);
                    logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                    DashUnit dashUnit = dashServer.addDashUnit(
                            StreamType.DYNAMIC,
                            dashUnitId,
                            null,
                            expires
                    );

                    if (dashUnit == null) {
                        dashUnit = dashServer.getDashUnitById(dashUnitId);
                    }

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
                            logger.debug("[DashMessageHandler] Parent mpd path is created. (parentMpdPath={}, uri={}, rtmpUri={})", mpdPath, uri, sourceUri);
                        }
                    }

                    String uriFileName = fileManager.getFileNameFromUri(uri);
                    mpdPath = fileManager.concatFilePath(mpdPath, uriFileName + StreamConfigManager.DASH_POSTFIX);
                    logger.debug("[ProcessServerChannelHandler] Final mpd path: {} (uri={}, rtmpUri={})", mpdPath, uri, sourceUri);

                    dashUnit.setInputFilePath(sourceUri);
                    dashUnit.setOutputFilePath(mpdPath);

                    ///////////////////////////
                    dashUnit.runLiveStreaming(uriFileName, sourceUri, mpdPath);
                    ///////////////////////////

                    logger.debug("[ProcessServerChannelHandler] DashUnit is created successfully. (id={}, request={})", dashUnitId, preProcessRequest);

                    preProcessResponse = new PreLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.PREPROCESS_RES,
                                    dashServer.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                            ),
                            ResponseType.SUCCESS,
                            ResponseType.REASON_SUCCESS.length(),
                            ResponseType.REASON_SUCCESS
                    );
                }
                responseByteData = preProcessResponse.getByteData();
                logger.debug("[ProcessServerChannelHandler] SEND PreLiveMediaProcessResponse(sourceIp={}, uri={}, preProcessResponse=\n{})", sourceIp, uri, preProcessResponse);
            } else if (messageHeader.getMessageType() == MessageType.ENDPROCESS_REQ) {
                EndLiveMediaProcessRequest endLiveMediaProcessRequest = new EndLiveMediaProcessRequest(data);
                String sourceIp = endLiveMediaProcessRequest.getSourceIp();
                String uri = endLiveMediaProcessRequest.getUri();
                logger.debug("[ProcessServerChannelHandler] RECV EndLiveMediaProcessRequest(sourceIp={}, uri={})", sourceIp, uri);

                String dashUnitId = sourceIp + ":" + fileManager.getFilePathWithoutExtensionFromUri(uri);
                logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                DashUnit dashUnit = dashServer.getDashUnitById(dashUnitId);

                EndLiveMediaProcessResponse endLiveMediaProcessResponse;
                if (dashUnit == null) {
                    logger.warn("[ProcessServerChannelHandler] DashUnit is not exist! (id={})", dashUnitId);
                    
                    endLiveMediaProcessResponse = new EndLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.ENDPROCESS_RES,
                                    dashServer.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_NOT_FOUND.length()
                            ),
                            ResponseType.NOT_FOUND,
                            ResponseType.REASON_NOT_FOUND.length(),
                            ResponseType.REASON_NOT_FOUND
                    );
                } else {
                    ///////////////////////////
                    dashServer.deleteDashUnit(dashUnitId);
                    ///////////////////////////

                    logger.debug("[ProcessServerChannelHandler] DashUnit[{}]'s pre live media process is finished. (request={}", dashUnitId, endLiveMediaProcessRequest);

                    endLiveMediaProcessResponse = new EndLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.ENDPROCESS_RES,
                                    dashServer.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                            ),
                            ResponseType.SUCCESS,
                            ResponseType.REASON_SUCCESS.length(),
                            ResponseType.REASON_SUCCESS
                    );
                }
                responseByteData = endLiveMediaProcessResponse.getByteData();
                logger.debug("[ProcessServerChannelHandler] SEND EndLiveMediaProcessResponse(sourceIp={}, uri={}, endLiveMediaProcessResponse=\n{})", sourceIp, uri, endLiveMediaProcessResponse);
            } else if (messageHeader.getMessageType() == MessageType.PREPROCESS_RES) {
                PreLiveMediaProcessResponse preProcessResponse = new PreLiveMediaProcessResponse(data);
                int statusCode = preProcessResponse.getStatusCode();
                String reason = preProcessResponse.getReason();
                logger.debug("[ProcessClientChannelHandler] RECV PreProcessResponse(statusCode={}, reason={})", statusCode, reason);

                if (statusCode == ResponseType.NOT_FOUND) {
                    logger.debug("[ProcessClientChannelHandler] RECV PreProcessResponse [404 NOT FOUND], Fail to start service.");
                    //ServiceManager.getInstance().stop();
                    System.exit(1);
                }
                responseByteData = null;
            } else if (messageHeader.getMessageType() == MessageType.ENDPROCESS_RES) {
                EndLiveMediaProcessResponse endLiveMediaProcessResponse = new EndLiveMediaProcessResponse(data);
                int statusCode = endLiveMediaProcessResponse.getStatusCode();
                String reason = endLiveMediaProcessResponse.getReason();
                logger.debug("[ProcessClientChannelHandler] RECV EndLiveMediaProcessResponse(statusCode={}, reason={})", statusCode, reason);
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
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("ProcessServerChannelHandler.Exception", cause);
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
