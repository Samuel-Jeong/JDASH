package dash.client.handler.base;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;

public abstract class DashHttpMessageHandler {

    public abstract void processContent(HttpObject httpObject, ChannelHandlerContext channelHandlerContext);
    public abstract void processResponse(HttpObject httpObject, ChannelHandlerContext channelHandlerContext);
    protected abstract void printHeader(HttpResponse httpResponse);
    protected abstract void sendReqForSegment(ChannelHandlerContext channelHandlerContext, boolean isTrySleep);
    protected abstract boolean retry();
    protected abstract void finish(ChannelHandlerContext channelHandlerContext);

}
