package dash.handler.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HttpMessageRouteTable {

    private static final Logger logger = LoggerFactory.getLogger(HttpMessageRouteTable.class);

    ////////////////////////////////////////////////////////////
    private final List<HttpMessageRoute> routes;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMessageRouteTable() {
        routes = new ArrayList<>();
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public void addRoute(final HttpMessageRoute route) {
        routes.add(route);
        logger.debug("[HttpMessageRouteTable] ROUTE [{}:{}] is added.", route.getMethod().name(), route.getPath());
    }

    public HttpMessageRoute findUriRoute(final HttpMethod method, final String path) {
        for (final HttpMessageRoute route : routes) {
            if (route.matches(method, path)) {
                logger.trace("[HttpMessageRouteTable] ROUTE [{}:{}]", route.getMethod().name(), route.getPath());
                return route;
            }
        }

        return null;
    }

    public void clear() {
        routes.clear();
        logger.debug("[HttpMessageRouteTable] ROUTE TABLE is cleared.");
    }

    public List<HttpMessageRoute> getRoutes() {
        return routes;
    }

    public List<String> getUriList() {
        List<String> uriList = new ArrayList<>();
        for (final HttpMessageRoute route : routes) {
            if (route == null) { continue; }

            uriList.add(route.getPath());
        }
        return uriList;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}