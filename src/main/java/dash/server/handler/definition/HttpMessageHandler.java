package dash.server.handler.definition;

import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;

public interface HttpMessageHandler {

    ////////////////////////////////////////////////////////////
    Object handle(HttpRequest request, HttpResponse response, String originUri, String uriFileName, ChannelHandlerContext ctx, DashUnit dashUnit) throws Exception;
    ////////////////////////////////////////////////////////////

}