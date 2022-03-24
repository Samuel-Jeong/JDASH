package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientFsmManager;
import dash.client.fsm.DashClientState;
import dash.client.handler.base.MessageType;
import dash.mpd.MpdManager;
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

public class DashAudioHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashAudioHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final DashClient dashClient;
    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashAudioHttpClientHandler(DashClient dashClient) {
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
            logger.warn("[DashAudioHttpClientHandler] DashClient is null. Fail to recv the message.");
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashAudioHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // AUDIO FSM
        DashClientFsmManager dashClientAudioFsmManager = dashClient.getDashClientAudioFsmManager();
        if (dashClientAudioFsmManager == null) {
            logger.warn("[DashAudioHttpClientHandler({})] Audio Fsm manager is not defined.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            if (!response.status().equals(HttpResponseStatus.OK)) {
                // 재시도 로직
                int curAudioRetryCount = dashClient.incAndGetAudioRetryCount();
                if (curAudioRetryCount > StreamConfigManager.AUDIO_RETRY_LIMIT) {
                    logger.warn("[DashAudioHttpClientHandler({})] [-] [AUDIO] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
                    dashClient.stop();
                    channelHandlerContext.close();
                } else {
                    dashClient.setIsAudioRetrying(true);

                    // SegmentDuration 의 절반 만큼(micro-sec) sleep
                    long segmentDuration = dashClient.getMpdManager().getAudioSegmentDuration(true); // 1000000
                    if (segmentDuration > 0) {
                        try {
                            segmentDuration = dashClient.getMpdManager().applyAtoIntoDuration(segmentDuration, MpdManager.CONTENT_AUDIO_TYPE); // 800000
                            segmentDuration /= 2; // 400000
                            timeUnit.sleep(segmentDuration);
                            logger.trace("[DashAudioHttpClientHandler({})] [AUDIO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                        } catch (Exception e) {
                            //logger.warn("");
                        }
                    }

                    String curAudioSegmentName = dashClient.getMpdManager().getAudioMediaSegmentName();
                    dashClient.sendHttpGetRequest(
                            fileManager.concatFilePath(
                                    dashClient.getSrcBasePath(),
                                    curAudioSegmentName
                            ),
                            MessageType.AUDIO
                    );

                    logger.warn("[DashAudioHttpClientHandler({})] [AUDIO] [count={}] Retrying... ({})",
                            dashClient.getDashUnitId(), curAudioRetryCount, curAudioSegmentName
                    );
                }
                return;
            } else {
                if (dashClient.getAudioRetryCount() > 0) {
                    dashClient.setAudioRetryCount(0);
                    dashClient.setIsAudioRetrying(false);
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[DashAudioHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), response.status());
                logger.trace("> VERSION: {}", response.protocolVersion());

                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            logger.trace("[DashAudioHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                        }
                    }
                }

                if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                    logger.trace("[DashAudioHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
                } else {
                    logger.trace("[DashAudioHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
                }
            }
        }

        // CONTENT
        if (httpObject instanceof HttpContent) {
            if (dashClient.isAudioRetrying()) { return; }

            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            if (buf == null) {
                logger.warn("[DashAudioHttpClientHandler({})] DatagramPacket's content is null.", dashClient.getDashUnitId());
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[DashAudioHttpClientHandler({})] Message is null.", dashClient.getDashUnitId());
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            String curAudioSegmentName = dashClient.getMpdManager().getAudioMediaSegmentName();
            if (curAudioSegmentName == null) {
                logger.warn("[DashVideoHttpClientHandler({})] [+] [AUDIO] MediaSegment name is not defined. (audioSeqNum={})",
                        dashClient.getDashUnitId(), dashClient.getMpdManager().getAudioSegmentSeqNum()
                );
                return;
            }

            StateManager audioStateManager = dashClientAudioFsmManager.getStateManager();
            StateHandler audioStateHandler = dashClientAudioFsmManager.getStateManager().getStateHandler(DashClientState.NAME);
            StateUnit audioStateUnit = audioStateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curAudioState = audioStateUnit.getCurState();
            switch (curAudioState) {
                case DashClientState.MPD_DONE:
                    dashClient.getMpdManager().makeInitSegment(fileManager, dashClient.getTargetAudioInitSegPath(), data);
                    break;
                case DashClientState.AUDIO_INIT_SEG_DONE:
                    String targetAudioMediaSegPath = fileManager.concatFilePath(
                            dashClient.getTargetBasePath(),
                            dashClient.getMpdManager().getAudioMediaSegmentName()
                    );
                    dashClient.getMpdManager().makeMediaSegment(fileManager, targetAudioMediaSegPath, data);
                    break;
            }

            logger.trace("[DashAudioHttpClientHandler({})] [AUDIO] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                switch (curAudioState) {
                    case DashClientState.MPD_DONE:
                        audioStateHandler.fire(DashClientEvent.GET_AUDIO_INIT_SEG, audioStateUnit);
                        break;
                    case DashClientState.AUDIO_INIT_SEG_DONE:
                        long curSeqNum = dashClient.getMpdManager().incAndGetAudioSegmentSeqNum();
                        String newAudioSegmentName = dashClient.getMpdManager().getAudioMediaSegmentName();
                        if (newAudioSegmentName == null) {
                            logger.warn("[DashVideoHttpClientHandler({})] [+] [AUDIO] Current MediaSegment name is not defined. (audioSeqNum={})",
                                    dashClient.getDashUnitId(), curSeqNum
                            );
                            return;
                        }
                        //logger.debug("[DashAudioHttpClientHandler({})] [+] [AUDIO] [seq={}] MediaSegment is changed. ([{}] > [{}])", dashClient.getDashUnitId(), curSeqNum, curAudioSegmentName, curAudioSegmentName);

                        // SegmentDuration 만큼(micro-sec) sleep
                        long segmentDuration = dashClient.getMpdManager().getAudioSegmentDuration(true); // 1000000
                        if (segmentDuration > 0) {
                            try {
                                segmentDuration = dashClient.getMpdManager().applyAtoIntoDuration(segmentDuration, MpdManager.CONTENT_AUDIO_TYPE);
                                //logger.debug("[DashAudioHttpClientHandler({})] [AUDIO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                                timeUnit.sleep(segmentDuration);
                            } catch (Exception e) {
                                //logger.warn("");
                            }
                        }

                        dashClient.sendHttpGetRequest(
                                fileManager.concatFilePath(
                                        dashClient.getSrcBasePath(),
                                        newAudioSegmentName
                                ),
                                MessageType.AUDIO
                        );
                        break;
                }

                logger.trace("[DashAudioHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());
            }
        }
    }
    ////////////////////////////////////////////////////////////

}