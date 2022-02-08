package dash.handler.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class HttpMessageRouteTable {

    private static final Logger logger = LoggerFactory.getLogger(HttpMessageRouteTable.class);

    ////////////////////////////////////////////////////////////
    private final ArrayList<HttpMessageRoute> routes;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMessageRouteTable() {
        this.routes = new ArrayList<>();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void addRoute(final HttpMessageRoute route) {
        this.routes.add(route);
        logger.debug("[HttpMessageRouteTable] ROUTE [{}] is added.", route);
    }

    public HttpMessageRoute findUriRoute(final HttpMethod method, final String path) {
        for (final HttpMessageRoute route : routes) {
            if (route.matches(method, path)) {
                return route;
            }
        }

        return null;
    }

    public void clear() {
        routes.clear();
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}