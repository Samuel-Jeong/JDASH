package dash.handler;

import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpRequest;
import dash.handler.definition.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashMessageHandler implements HttpMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);
    
    @Override
    public Object handle(HttpRequest request, HttpResponse response) {
        if (request == null) { return null; }

        String uri = request.getRequest().uri();
        logger.debug("[DashMessageHandler] URI: [{}]", uri);

        return uri;
    }

}
