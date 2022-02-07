package dash.handler;

import dash.handler.definition.HttpMessageHandler;
import dash.handler.definition.HttpRequest;
import dash.handler.definition.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class DashMessageHandler implements HttpMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    TransformerFactory transformerFactory = TransformerFactory.newInstance();

    @Override
    public Object handle(HttpRequest request, HttpResponse response) {
        if (request == null) { return null; }

        String uri = request.getRequest().uri();
        logger.debug("[DashMessageHandler] URI: [{}]", uri);

        /*try {
            // TODO : XML TO STRING
            StringWriter writer = new StringWriter();

            //transform document to string
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(new DOMSource(xmlDocument), new StreamResult(writer));

            String xmlString = writer.getBuffer().toString();
            logger.debug(xmlString);
        } catch (Exception e) {
            logger.warn("DashMessageHandler.handle.Exception", e);
        }*/

        return uri;
    }

}
