package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashVideoHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashVideoHttpClientHandler.class);

    private static final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final int retryCount;

    private final DashClient dashClient;
    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashVideoHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
        this.retryCount = AppInstance.getInstance().getConfigManager().getDownloadChunkRetryCount();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (dashClient != null) {
            logger.warn("DashVideoHttpClientHandler is inactive. (dashUnitId={})", dashClient.getDashUnitId());
        }
        ctx.close();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashVideoHttpClientHandler] DashClient is null. Fail to recv the message.");
            channelHandlerContext.close();
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashVideoHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        processResponse(httpObject, channelHandlerContext);

        // CONTENT
        processContent(httpObject, channelHandlerContext);
    }
    ////////////////////////////////////////////////////////////

    private void processResponse(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) httpObject;

            dashClient.stopVideoTimeout();
            if (!httpResponse.status().equals(HttpResponseStatus.OK)) {
                // 재시도 로직
                if (!retry()) {
                    logger.warn("[DashVideoHttpClientHandler({})] [-] [VIDEO] !!! RECV NOT OK. DashClient will be stopped. (status={}, retryCount={})",
                            dashClient.getDashUnitId(), httpResponse.status(), retryCount
                    );
                    finish(channelHandlerContext);
                }
                return;
            } else {
                if (dashClient.getVideoRetryCount() > 0) {
                    dashClient.setVideoRetryCount(0);
                    dashClient.setIsVideoRetrying(false);
                }
            }

            printHeader(httpResponse);
        }
    }

    private void processContent(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        if (httpObject instanceof HttpContent) {
            if (dashClient.isVideoRetrying()) { return; }

            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            if (buf == null) {
                logger.warn("[DashVideoHttpClientHandler({})] DatagramPacket's content is null.", dashClient.getDashUnitId());
                finish(channelHandlerContext);
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[DashVideoHttpClientHandler({})] Message is null.", dashClient.getDashUnitId());
                finish(channelHandlerContext);
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            String curVideoSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
            if (curVideoSegmentName == null) {
                logger.warn("[DashVideoHttpClientHandler({})] [+] [VIDEO] MediaSegment name is not defined. (videoSeqNum={})",
                        dashClient.getDashUnitId(), dashClient.getMpdManager().getVideoSegmentSeqNum()
                );
                finish(channelHandlerContext);
                return;
            }

            // VIDEO FSM
            DashClientFsmManager dashClientVideoFsmManager = dashClient.getDashClientVideoFsmManager();
            if (dashClientVideoFsmManager == null) {
                logger.warn("[DashAudioHttpClientHandler({})] Video Fsm manager is not defined.", dashClient.getDashUnitId());
                finish(channelHandlerContext);
                return;
            }

            StateManager videoStateManager = dashClientVideoFsmManager.getStateManager();
            StateHandler videoStateHandler = videoStateManager.getStateHandler(DashClientState.NAME);
            StateUnit videoStateUnit = videoStateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curVideoState = videoStateUnit.getCurState();
            switch (curVideoState) {
                case DashClientState.MPD_DONE:
                    dashClient.getMpdManager().makeInitSegment(fileManager, dashClient.getTargetVideoInitSegPath(), data);
                    break;
                case DashClientState.VIDEO_INIT_SEG_DONE:
                    String targetVideoMediaSegPath = fileManager.concatFilePath(
                            dashClient.getTargetBasePath(),
                            curVideoSegmentName
                    );
                    dashClient.getMpdManager().makeMediaSegment(fileManager, targetVideoMediaSegPath, data);
                    break;
                default:
                    break;
            }

            logger.trace("[DashVideoHttpClientHandler({})] [VIDEO] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                switch (curVideoState) {
                    case DashClientState.MPD_DONE:
                        videoStateHandler.fire(DashClientEvent.GET_VIDEO_INIT_SEG, videoStateUnit);
                        break;
                    case DashClientState.VIDEO_INIT_SEG_DONE:
                        long curSeqNum = dashClient.getMpdManager().incAndGetVideoSegmentSeqNum();
                        String newVideoSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
                        if (newVideoSegmentName == null) {
                            logger.warn("[DashVideoHttpClientHandler({})] [+] [VIDEO] Current MediaSegment name is not defined. (videoSeqNum={})",
                                    dashClient.getDashUnitId(), curSeqNum
                            );
                            finish(channelHandlerContext);
                            return;
                        }
                        //logger.debug("[DashVideoHttpClientHandler({})] [+] [VIDEO] [seq={}] MediaSegment is changed. ([{}] > [{}])", dashClient.getDashUnitId(), curSeqNum, curVideoSegmentName, newVideoSegmentName);

                        // SegmentDuration 만큼(micro-sec) sleep
                        long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
                        if (segmentDuration > 0) {
                            try {
                                segmentDuration = dashClient.getMpdManager().applyAtoIntoDuration(segmentDuration, MpdManager.CONTENT_VIDEO_TYPE);
                                //logger.debug("[DashVideoHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                                timeUnit.sleep(segmentDuration);
                            } catch (Exception e) {
                                //logger.warn("");
                            }
                        }

                        dashClient.sendHttpGetRequest(
                                fileManager.concatFilePath(
                                        dashClient.getSrcBasePath(),
                                        newVideoSegmentName
                                ),
                                MessageType.VIDEO
                        );
                        break;
                    default:
                        break;
                }

                logger.trace("[DashVideoHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());
            }
        }
    }

    private boolean retry() {
        int curVideoRetryCount = dashClient.incAndGetVideoRetryCount();
        if (curVideoRetryCount > retryCount) {
            dashClient.setIsVideoRetrying(false);
            return false;
        }

        dashClient.setIsVideoRetrying(true);

        // SegmentDuration 의 절반 만큼(micro-sec) sleep
        long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
        if (segmentDuration > 0) {
            try {
                segmentDuration = dashClient.getMpdManager().applyAtoIntoDuration(segmentDuration, MpdManager.CONTENT_VIDEO_TYPE); // 800000
                segmentDuration /= 2; // 400000
                timeUnit.sleep(segmentDuration);
                logger.trace("[DashVideoHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
            } catch (Exception e) {
                //logger.warn("");
            }
        }

        String curVideoSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
        dashClient.sendHttpGetRequest(
                fileManager.concatFilePath(
                        dashClient.getSrcBasePath(),
                        curVideoSegmentName
                ),
                MessageType.VIDEO
        );

        logger.warn("[DashVideoHttpClientHandler({})] [VIDEO] [count={}] Retrying... ({})",
                dashClient.getDashUnitId(), curVideoRetryCount, curVideoSegmentName
        );
        return true;
    }

    private void finish(ChannelHandlerContext channelHandlerContext) {
        DashUnit dashUnit = ServiceManager.getInstance().getDashServer().getDashUnitById(dashClient.getDashUnitId());
        if (dashUnit != null) {
            if (dashUnit.getType().equals(StreamType.STATIC)) {
                dashClient.stop();
            } else {
                ServiceManager.getInstance().getDashServer().deleteDashUnit(dashClient.getDashUnitId());
            }
        }
        channelHandlerContext.close();
    }

    private void printHeader(HttpResponse httpResponse) {
        if (logger.isTraceEnabled()) {
            logger.trace("[DashVideoHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), httpResponse.status());
            logger.trace("> VERSION: {}", httpResponse.protocolVersion());

            if (!httpResponse.headers().isEmpty()) {
                for (CharSequence name : httpResponse.headers().names()) {
                    for (CharSequence value : httpResponse.headers().getAll(name)) {
                        logger.trace("[DashVideoHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                    }
                }
            }

            if (HttpHeaderUtil.isTransferEncodingChunked(httpResponse)) {
                logger.trace("[DashVideoHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
            } else {
                logger.trace("[DashVideoHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
            }
        }
    }

}