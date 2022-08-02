package dash.client.handler.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.client.DashClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashAudioHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashAudioHttpClientHandler.class);

    private final DashClient dashClient;
    private final DashAudioHttpMessageHandler dashAudioHttpMessageHandler;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashAudioHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
        this.dashAudioHttpMessageHandler = new DashAudioHttpMessageHandler(dashClient);
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("(dashUnitId={}) {}", dashClient.getDashUnitId(), gson.toJson(cause));
        //cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (dashClient != null) {
            logger.warn("DashAudioHttpClientHandler is inactive. (dashUnitId={})", dashClient.getDashUnitId());
        }
        ctx.close();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashAudioHttpClientHandler] DashClient is null. Fail to recv the message.");
            channelHandlerContext.close();
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashAudioHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        processResponse(httpObject, channelHandlerContext);

        // CONTENT
        processContent(httpObject, channelHandlerContext);
    }
    ////////////////////////////////////////////////////////////

    private void processResponse(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        dashAudioHttpMessageHandler.processResponse(httpObject, channelHandlerContext);
    }

    private void processContent(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        dashAudioHttpMessageHandler.processContent(httpObject, channelHandlerContext);
    }

}