package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientState;
import dash.client.handler.base.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DashMpdHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(DashMpdHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.SECONDS;

    private final DashClient dashClient;

    public DashMpdHttpClientHandler(DashClient dashClient) {
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
                logger.warn("[DashHttpClientHandler({})] [-] [MPD] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
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
            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            if (buf == null) {
                logger.warn("[ProcessClientChannelHandler] DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[ProcessClientChannelHandler] Message is null.");
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            StateManager stateManager = dashClient.getDashClientFsmManager().getStateManager();
            StateHandler stateHandler = dashClient.getDashClientFsmManager().getStateManager().getStateHandler(DashClientState.NAME);
            StateUnit stateUnit = stateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curState = stateUnit.getCurState();
            //if (DashClientState.IDLE.equals(curState)) {
                dashClient.getMpdManager().makeMpd(dashClient.getTargetMpdPath(), data);
            //}

            logger.trace("[DashHttpClientHandler({})] [MPD] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                if (DashClientState.IDLE.equals(curState)) {
                    stateHandler.fire(DashClientEvent.GET_MPD, stateUnit);
                }

                dashClient.getMpdManager().setIsMpdDone(true);
                logger.trace("[DashHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());

                Duration maxSegmentDuration = dashClient.getMpdManager().getMaxSegmentDuration();
                if (maxSegmentDuration != null) {
                    try {
                        long seconds = maxSegmentDuration.getSeconds();
                        if (seconds > 0) {
                            seconds *= 3;
                            timeUnit.sleep(seconds);
                            logger.trace("[DashHttpClientHandler({})] [MPD] Waiting... ({})", dashClient.getDashUnitId(), seconds);

                            dashClient.sendHttpGetRequest(dashClient.getSrcPath(), MessageType.MPD);
                        }
                    } catch (Exception e) {
                        //logger.warn("");
                    }
                }
            }
        }
    }

}