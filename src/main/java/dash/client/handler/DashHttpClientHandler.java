package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(DashHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final DashClient dashClient;

    public DashHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashHttpClientHandler] DashClient is null. Fail to recv the message.");
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            if (!response.status().equals(HttpResponseStatus.OK)) {
                logger.trace("[DashHttpClientHandler({})] [-] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
                dashClient.stop();
                channelHandlerContext.close();
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[DashHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), response.status());
                logger.trace("> VERSION: {}", response.protocolVersion());

                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            logger.trace("[DashHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                        }
                    }
                }

                if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                    logger.trace("[DashHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
                } else {
                    logger.trace("[DashHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
                }
            }
        }

        // CONTENT
        if (httpObject instanceof HttpContent) {
            HttpContent content = (HttpContent) httpObject;
            String contentString = content.content().toString(CharsetUtil.UTF_8);

            StateManager stateManager = dashClient.getDashClientFsmManager().getStateManager();
            StateHandler stateHandler = dashClient.getDashClientFsmManager().getStateManager().getStateHandler(DashClientState.NAME);
            StateUnit stateUnit = stateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curState = stateUnit.getCurState();
            switch (curState) {
                case DashClientState.IDLE:
                    dashClient.getMpdManager().makeMpd(dashClient.getTargetMpdPath(), contentString);
                    break;
                case DashClientState.MPD_DONE:
                    if (AppInstance.getInstance().getConfigManager().isEnableValidation()
                            && dashClient.getMpdManager().validate()) {
                        logger.debug("[DashHttpClientHandler({})] Success to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
                    } else {
                        logger.warn("[DashHttpClientHandler({})] Fail to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
                    }

                    dashClient.getMpdManager().makeInitSegment(dashClient.getTargetAudioInitSegPath(), contentString);
                    break;
                case DashClientState.INIT_SEG_DONE:
                    String targetAudioMediaSegPath = FileManager.concatFilePath(
                            dashClient.getTargetBasePath(),
                            dashClient.getMpdManager().getMediaSegmentName()
                    );
                    dashClient.getMpdManager().makeMediaSegment(targetAudioMediaSegPath, contentString);;
                    break;
            }

            logger.trace("[DashHttpClientHandler({})] {}", dashClient.getDashUnitId(), contentString);
            if (content instanceof LastHttpContent) {
                switch (curState) {
                    case DashClientState.IDLE:
                        stateHandler.fire(DashClientEvent.GET_MPD, stateUnit);
                        break;
                    case DashClientState.MPD_DONE:
                        stateHandler.fire(DashClientEvent.GET_INIT_SEG, stateUnit);
                        break;
                    case DashClientState.INIT_SEG_DONE:
                        String prevMediaSegmentName = dashClient.getMpdManager().getMediaSegmentName();
                        dashClient.getMpdManager().incAndGetAudioSegmentSeqNum();
                        String curMediaSegmentName = dashClient.getMpdManager().getMediaSegmentName();
                        logger.debug("[DashHttpClientHandler({})] [+] MediaSegment is changed. ([{}] > [{}])", dashClient.getDashUnitId(), prevMediaSegmentName, curMediaSegmentName);

                        // SegmentDuration 만큼(micro-sec) sleep
                        long segmentDuration = dashClient.getMpdManager().getAudioSegmentDuration();
                        if (segmentDuration > 0) {
                            try {
                                timeUnit.sleep(segmentDuration);
                            } catch (Exception e) {
                                //logger.warn("");
                            }
                        }

                        dashClient.sendHttpGetRequest(
                                FileManager.concatFilePath(
                                        dashClient.getSrcBasePath(),
                                        curMediaSegmentName
                                )
                        );
                        break;
                }

                logger.trace("[DashHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());
            }
        }
    }

}