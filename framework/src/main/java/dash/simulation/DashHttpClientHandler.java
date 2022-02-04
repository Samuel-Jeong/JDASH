package dash.simulation;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(DashHttpClientHandler.class);
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            logger.debug("STATUS: {}", response.status());
            logger.debug("VERSION: {}", response.protocolVersion());

            if (!response.headers().isEmpty()) {
                for (CharSequence name: response.headers().names()) {
                    for (CharSequence value: response.headers().getAll(name)) {
                        logger.debug("HEADER: {} = {}", name, value);
                    }
                }
            }

            if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                logger.debug("CHUNKED CONTENT {");
            } else {
                logger.debug("CONTENT {");
            }
        }

        if (httpObject instanceof HttpContent) {
            HttpContent content = (HttpContent) httpObject;
            logger.debug(content.content().toString(CharsetUtil.UTF_8));
            if (content instanceof LastHttpContent) {
                logger.debug("} END OF CONTENT");
                channelHandlerContext.close();
            }
        }
    }
}