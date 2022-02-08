package dash.handler.definition;

public interface HttpMessageHandler {

    ////////////////////////////////////////////////////////////
    Object handle(HttpRequest request, HttpResponse response, String originUri) throws Exception;
    ////////////////////////////////////////////////////////////

}