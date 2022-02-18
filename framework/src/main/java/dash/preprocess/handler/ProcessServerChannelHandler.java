package dash.preprocess.handler;

import config.ConfigManager;
import dash.DashManager;
import dash.preprocess.PreProcessMediaManager;
import dash.preprocess.message.EndLiveMediaProcessRequest;
import dash.preprocess.message.EndLiveMediaProcessResponse;
import dash.preprocess.message.PreLiveMediaProcessRequest;
import dash.preprocess.message.PreLiveMediaProcessResponse;
import dash.preprocess.message.base.MessageHeader;
import dash.preprocess.message.base.MessageType;
import dash.preprocess.message.base.ResponseType;
import dash.unit.DashUnit;
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
import util.module.FileManager;

import java.io.File;

public class ProcessServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(ProcessServerChannelHandler.class);

    ////////////////////////////////////////////////////////////////////////////////
    public ProcessServerChannelHandler() {}
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) {
        try {
            DashManager dashManager = ServiceManager.getInstance().getDashManager();
            PreProcessMediaManager preProcessMediaManager = dashManager.getPreProcessMediaManager();

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
                String sourceIp = preProcessRequest.getSourceIp();
                String uri = preProcessRequest.getUri();
                long expires = preProcessRequest.getExpires();
                logger.debug("[ProcessServerChannelHandler] RECV PreProcessRequest(sourceIp={}, uri={}, expires={})", sourceIp, uri, expires);

                String dashUnitId = sourceIp + ":" + FileManager.getFilePathWithoutExtensionFromUri(uri);
                logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                DashUnit dashUnit = dashManager.addDashUnit(dashUnitId, null);

                PreLiveMediaProcessResponse preProcessResponse;
                if (dashUnit == null) {
                    logger.warn("[ProcessServerChannelHandler] DashUnit is already exist! (id={}, request={})", dashUnitId, preProcessRequest);

                    preProcessResponse = new PreLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.PREPROCESS_RES,
                                    dashManager.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_ALREADY_EXIST.length()
                            ),
                            ResponseType.FORBIDDEN,
                            ResponseType.REASON_ALREADY_EXIST.length(),
                            ResponseType.REASON_ALREADY_EXIST
                    );
                } else {
                    ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                    String networkPath = "rtmp://" + configManager.getRtmpPublishIp() + ":" + configManager.getRtmpPublishPort();
                    String curRtmpUri = FileManager.concatFilePath(networkPath, uri);
                    String mpdPath = FileManager.concatFilePath(configManager.getMediaBasePath(), uri);
                    File mpdPathFile = new File(mpdPath);
                    if (!mpdPathFile.exists()) {
                        if (mpdPathFile.mkdirs()) {
                            logger.debug("[DashMessageHandler] Parent mpd path is created. (parentMpdPath={}, uri={}, rtmpUri={})", mpdPath, uri, curRtmpUri);
                        }
                    }

                    String uriFileName = FileManager.getFileNameFromUri(uri);
                    mpdPath = FileManager.concatFilePath(mpdPath, uriFileName + ".mpd");
                    logger.debug("[ProcessServerChannelHandler] Final mpd path: {} (uri={}, rtmpUri={})", mpdPath, uri, curRtmpUri);

                    dashUnit.setInputFilePath(curRtmpUri);
                    dashUnit.setOutputFilePath(mpdPath);
                    dashUnit.setLiveStreaming(true);

                    ///////////////////////////
                    dashUnit.runRtmpStreaming(uriFileName, curRtmpUri, mpdPath);
                    ///////////////////////////

                    logger.debug("[ProcessServerChannelHandler] DashUnit is created successfully. (id={}, request={})", dashUnitId, preProcessRequest);

                    preProcessResponse = new PreLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.PREPROCESS_RES,
                                    dashManager.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                            ),
                            ResponseType.SUCCESS,
                            ResponseType.REASON_SUCCESS.length(),
                            ResponseType.REASON_SUCCESS
                    );
                }
                responseByteData = preProcessResponse.getByteData();
            } else if (messageHeader.getMessageType() == MessageType.ENDPROCESS_REQ) {
                EndLiveMediaProcessRequest endLiveMediaProcessRequest = new EndLiveMediaProcessRequest(data);
                String sourceIp = endLiveMediaProcessRequest.getSourceIp();
                String uri = endLiveMediaProcessRequest.getUri();
                logger.debug("[ProcessServerChannelHandler] RECV EndLiveMediaProcessRequest(sourceIp={}, uri={})", sourceIp, uri);

                String dashUnitId = sourceIp + ":" + FileManager.getFilePathWithoutExtensionFromUri(uri);
                logger.debug("[ProcessServerChannelHandler] DashUnitId: [{}]", dashUnitId);
                DashUnit dashUnit = dashManager.getDashUnit(dashUnitId);

                EndLiveMediaProcessResponse endLiveMediaProcessResponse;
                if (dashUnit == null) {
                    logger.warn("[ProcessServerChannelHandler] DashUnit is not exist! (id={}, request={})", dashUnitId, endLiveMediaProcessRequest);
                    
                    endLiveMediaProcessResponse = new EndLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.ENDPROCESS_RES,
                                    dashManager.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_NOT_FOUND.length()
                            ),
                            ResponseType.NOT_FOUND,
                            ResponseType.REASON_NOT_FOUND.length(),
                            ResponseType.REASON_NOT_FOUND
                    );
                    responseByteData = endLiveMediaProcessResponse.getByteData();
                } else {
                    ///////////////////////////
                    dashUnit.finishRtmpStreaming();
                    ///////////////////////////

                    logger.debug("[ProcessServerChannelHandler] DashUnit[{}]'s pre live media process is finished. (request={}", dashUnitId, endLiveMediaProcessRequest);

                    endLiveMediaProcessResponse = new EndLiveMediaProcessResponse(
                            new MessageHeader(
                                    PreProcessMediaManager.MESSAGE_MAGIC_COOKIE,
                                    MessageType.ENDPROCESS_RES,
                                    dashManager.getPreProcessMediaManager().getRequestSeqNumber().getAndIncrement(),
                                    System.currentTimeMillis(),
                                    PreLiveMediaProcessResponse.MIN_SIZE + ResponseType.REASON_SUCCESS.length()
                            ),
                            ResponseType.SUCCESS,
                            ResponseType.REASON_SUCCESS.length(),
                            ResponseType.REASON_SUCCESS
                    );
                    responseByteData = endLiveMediaProcessResponse.getByteData();
                }
            } else {
                responseByteData = null;
            }

            if (responseByteData != null) {
                destinationRecord.getNettyChannel().sendData(responseByteData, responseByteData.length);
            } else {
                logger.warn("[ProcessServerChannelHandler] Fail to response. (header={})", messageHeader);
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

}
