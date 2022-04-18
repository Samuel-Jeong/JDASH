package dash.client.handler.mpd;

import dash.client.DashClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashMpdHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    ////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMpdHttpClientHandler.class);

    private final DashClient dashClient;
    private final DashMpdHttpMessageHandler dashMpdHttpMessageHandler;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public DashMpdHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
        this.dashMpdHttpMessageHandler = new DashMpdHttpMessageHandler(dashClient);
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
            logger.warn("DashMpdHttpClientHandler is inactive. (dashUnitId={})", dashClient.getDashUnitId());
        }
        ctx.close();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashMpdHttpClientHandler] DashClient is null. Fail to recv the message.");
            channelHandlerContext.close();
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashMpdHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        processResponse(httpObject, channelHandlerContext);

        // CONTENT
        processContent(httpObject, channelHandlerContext);
    }
    ////////////////////////////////////////////////////////////

    private void processResponse(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        dashMpdHttpMessageHandler.processResponse(httpObject, channelHandlerContext);
    }

    private void processContent(HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        dashMpdHttpMessageHandler.processContent(httpObject, channelHandlerContext);
    }

}
