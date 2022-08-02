package dash.client.handler.video;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.client.DashClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashVideoHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashVideoHttpClientHandler.class);

    private final DashClient dashClient;
    private final DashVideoHttpMessageHandler dashVideoHttpMessageHandler;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashVideoHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
        this.dashVideoHttpMessageHandler = new DashVideoHttpMessageHandler(dashClient);
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
        dashVideoHttpMessageHandler.processResponse(httpObject, channelHandlerContext);
    }

    private void processContent(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        dashVideoHttpMessageHandler.processContent(httpObject, channelHandlerContext);
    }

}