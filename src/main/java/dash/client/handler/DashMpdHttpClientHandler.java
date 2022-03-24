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
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DashMpdHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMpdHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.SECONDS;

    private final DashClient dashClient;
    private final FileManager fileManager = new FileManager();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashMpdHttpClientHandler(DashClient dashClient) {
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
            logger.warn("[DashMpdHttpClientHandler] DashClient is null. Fail to recv the message.");
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashMpdHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            if (!response.status().equals(HttpResponseStatus.OK)) {
                logger.warn("[DashMpdHttpClientHandler({})] [-] [MPD] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
                dashClient.stop();
                channelHandlerContext.close();
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[DashMpdHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), response.status());
                logger.trace("> VERSION: {}", response.protocolVersion());

                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            logger.trace("[DashMpdHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                        }
                    }
                }

                if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                    logger.trace("[DashMpdHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
                } else {
                    logger.trace("[DashMpdHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
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

            // AUDIO FSM
            DashClientFsmManager dashClientAudioFsmManager = dashClient.getDashClientAudioFsmManager();
            if (dashClientAudioFsmManager == null) {
                logger.warn("[DashAudioHttpClientHandler({})] Audio Fsm manager is not defined.", dashClient.getDashUnitId());
                return;
            }

            // VIDEO FSM
            DashClientFsmManager dashClientVideoFsmManager = dashClient.getDashClientVideoFsmManager();

            //if (DashClientState.IDLE.equals(curState)) {
            dashClient.getMpdManager().makeMpd(
                    fileManager,
                    dashClient.getTargetMpdPath(),
                    data
            );
            //}

            logger.trace("[DashMpdHttpClientHandler({})] [MPD] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                logger.trace("[DashMpdHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());

                ////////////////////////////
                // GET MPD DONE > PARSE MPD & GET META DATA
                if (!dashClient.getMpdManager().parseMpd(dashClient.getTargetMpdPath(), true)) {
                    logger.warn("[DashMpdHttpClientHandler({})] Fail to parse the mpd. (dashClient={})", dashClient.getDashUnitId(), dashClient);
                    return;
                }

                if (AppInstance.getInstance().getConfigManager().isEnableValidation()) {
                    if (dashClient.getMpdManager().validate()) {
                        logger.debug("[DashMpdHttpClientHandler({})] Success to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
                    } else {
                        logger.warn("[DashMpdHttpClientHandler({})] Fail to validate the mpd. (mpdPath={})", dashClient.getDashUnitId(), dashClient.getTargetMpdPath());
                        dashClient.stop();
                        return;
                    }
                }
                dashClient.getMpdManager().setIsMpdDone(true);
                ////////////////////////////

                ////////////////////////////
                // FIRE TO NEXT EVENT IN DashClientAudioFsmManager
                StateManager audioStateManager = dashClientAudioFsmManager.getStateManager();
                StateHandler audioStateHandler = dashClientAudioFsmManager.getStateManager().getStateHandler(DashClientState.NAME);
                StateUnit audioStateUnit = audioStateManager.getStateUnit(dashClient.getDashClientStateUnitId());
                String curAudioState = audioStateUnit.getCurState();
                if (DashClientState.IDLE.equals(curAudioState)) {
                    dashClient.getMpdManager().setSegmentStartNumber(MpdManager.CONTENT_AUDIO_TYPE);
                    audioStateHandler.fire(DashClientEvent.GET_MPD_AUDIO, audioStateUnit);
                }

                if (dashClientVideoFsmManager != null) {
                    // FIRE TO NEXT EVENT IN DashClientVideoFsmManager
                    StateManager videoStateManager = dashClientVideoFsmManager.getStateManager();
                    StateHandler videoStateHandler = dashClientVideoFsmManager.getStateManager().getStateHandler(DashClientState.NAME);
                    StateUnit videoStateUnit = videoStateManager.getStateUnit(dashClient.getDashClientStateUnitId());
                    String curVideoState = videoStateUnit.getCurState();
                    if (DashClientState.IDLE.equals(curVideoState)) {
                        dashClient.getMpdManager().setSegmentStartNumber(MpdManager.CONTENT_VIDEO_TYPE);
                        videoStateHandler.fire(DashClientEvent.GET_MPD_VIDEO, videoStateUnit);
                    }
                }
                ////////////////////////////

                ////////////////////////////
                // SEND MPD REQUEST again for MediaPresentationDuration
                /**
                 * @ mediaPresentationDuration : Refers to the duration of the media content
                 *      It has 'PT' as prefix denoting that
                 *          time-range is in units of seconds (S), minutes (M), hours (H) and days (D).
                 *      In this scenario we have the value as "PT23M12.128S",
                 *          i.e., the media content has a total duration of 23 minutes 12.128 seconds
                 */
                Duration mediaPresentationDuration = dashClient.getMpdManager().getMediaPresentationDuration();
                if (mediaPresentationDuration != null) {
                    try {
                        long seconds = mediaPresentationDuration.getSeconds();
                        if (seconds > 0) {
                            timeUnit.sleep(seconds);
                            logger.trace("[DashMpdHttpClientHandler({})] [MPD] Waiting... ({})", dashClient.getDashUnitId(), seconds);

                            dashClient.sendHttpGetRequest(dashClient.getSrcPath(), MessageType.MPD);
                        }
                    } catch (Exception e) {
                        //logger.warn("");
                    }
                }
                ////////////////////////////
            }
        }
    }
    ////////////////////////////////////////////////////////////

}