package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.base.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.StreamConfigManager;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashVideoHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashVideoHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final DashClient dashClient;
    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashVideoHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashVideoHttpClientHandler] DashClient is null. Fail to recv the message.");
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashVideoHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // VIDEO FSM
        DashClientFsmManager dashClientVideoFsmManager = dashClient.getDashClientVideoFsmManager();
        if (dashClientVideoFsmManager == null) {
            logger.warn("[DashAudioHttpClientHandler({})] Video Fsm manager is not defined.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            if (!response.status().equals(HttpResponseStatus.OK)) {
                // 재시도 로직
                int curVideoRetryCount = dashClient.incAndGetVideoRetryCount();
                if (curVideoRetryCount > StreamConfigManager.VIDEO_RETRY_LIMIT) {
                    logger.warn("[DashVideoHttpClientHandler({})] [-] [VIDEO] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
                    dashClient.stop();
                    channelHandlerContext.close();
                } else {
                    // SegmentDuration 의 절반 만큼(micro-sec) sleep
                    long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
                    if (segmentDuration > 0) {
                        try {
                            segmentDuration /= 2; // 500000
                            timeUnit.sleep(segmentDuration);
                            logger.trace("[DashVideoHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                        } catch (Exception e) {
                            //logger.warn("");
                        }
                    }

                    dashClient.sendHttpGetRequest(
                            fileManager.concatFilePath(
                                    dashClient.getSrcBasePath(),
                                    dashClient.getMpdManager().getVideoMediaSegmentName()
                            ),
                            MessageType.VIDEO
                    );

                    logger.warn("[DashVideoHttpClientHandler({})] [VIDEO] [count={}] Retrying... ({})",
                            dashClient.getDashUnitId(), curVideoRetryCount, dashClient.getMpdManager().getVideoMediaSegmentName()
                    );
                }
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[DashVideoHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), response.status());
                logger.trace("> VERSION: {}", response.protocolVersion());

                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            logger.trace("[DashVideoHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                        }
                    }
                }

                if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                    logger.trace("[DashVideoHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
                } else {
                    logger.trace("[DashVideoHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
                }
            }
        }

        // CONTENT
        if (httpObject instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            if (buf == null) {
                logger.warn("[DashVideoHttpClientHandler({})] DatagramPacket's content is null.", dashClient.getDashUnitId());
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[DashVideoHttpClientHandler({})] Message is null.", dashClient.getDashUnitId());
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            StateManager videoStateManager = dashClientVideoFsmManager.getStateManager();
            StateHandler videoStateHandler = dashClientVideoFsmManager.getStateManager().getStateHandler(DashClientState.NAME);
            StateUnit videoStateUnit = videoStateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curVideoState = videoStateUnit.getCurState();
            switch (curVideoState) {
                case DashClientState.MPD_DONE:
                    dashClient.getMpdManager().makeInitSegment(fileManager, dashClient.getTargetVideoInitSegPath(), data);
                    break;
                case DashClientState.VIDEO_INIT_SEG_DONE:
                    String targetVideoMediaSegPath = fileManager.concatFilePath(
                            dashClient.getTargetBasePath(),
                            dashClient.getMpdManager().getVideoMediaSegmentName()
                    );
                    dashClient.getMpdManager().makeMediaSegment(fileManager, targetVideoMediaSegPath, data);
                    break;
            }

            logger.trace("[DashVideoHttpClientHandler({})] [VIDEO] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                switch (curVideoState) {
                    case DashClientState.MPD_DONE:
                        videoStateHandler.fire(DashClientEvent.GET_VIDEO_INIT_SEG, videoStateUnit);
                        break;
                    case DashClientState.VIDEO_INIT_SEG_DONE:
                        String prevMediaSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
                        if (prevMediaSegmentName == null) {
                            logger.debug("[DashVideoHttpClientHandler({})] [+] [VIDEO] Previous MediaSegment name is not defined. (videoSeqNum={})",
                                    dashClient.getDashUnitId(), dashClient.getMpdManager().getVideoSegmentSeqNum()
                            );
                            return;
                        }

                        long curSeqNum = dashClient.getMpdManager().incAndGetVideoSegmentSeqNum();
                        String curVideoSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
                        if (curVideoSegmentName == null) {
                            logger.debug("[DashVideoHttpClientHandler({})] [+] [VIDEO] Current MediaSegment name is not defined. (videoSeqNum={})",
                                    dashClient.getDashUnitId(), dashClient.getMpdManager().getVideoSegmentSeqNum()
                            );
                            return;
                        }

                        logger.trace("[DashVideoHttpClientHandler({})] [+] [VIDEO] [seq={}] MediaSegment is changed. ([{}] > [{}])",
                                dashClient.getDashUnitId(), curSeqNum, prevMediaSegmentName, curVideoSegmentName
                        );

                        // SegmentDuration 만큼(micro-sec) sleep
                        long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
                        /*double availabilityTimeOffset = dashClient.getMpdManager().getAvailabilityTimeOffset(); // 0.8
                        if (availabilityTimeOffset > 0) {
                            segmentDuration *= availabilityTimeOffset; // 800000
                        }*/
                        if (segmentDuration > 0) {
                            try {
                                logger.trace("[DashVideoHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                                timeUnit.sleep(segmentDuration);
                            } catch (Exception e) {
                                //logger.warn("");
                            }
                        }

                        dashClient.sendHttpGetRequest(
                                fileManager.concatFilePath(
                                        dashClient.getSrcBasePath(),
                                        curVideoSegmentName
                                ),
                                MessageType.VIDEO
                        );
                        break;
                }

                logger.trace("[DashVideoHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());
            }
        }
    }
    ////////////////////////////////////////////////////////////

}