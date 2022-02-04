package dash.handler.definition;

import io.netty.handler.codec.http.HttpMethod;

import java.util.ArrayList;

public class HttpMessageRouteTable {

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
    }

    public HttpMessageRoute findRoute(final HttpMethod method, final String path) {
        for (final HttpMessageRoute route : routes) {
            if (route.matches(method, path)) {
                return route;
            }
        }

        return null;
    }
    ////////////////////////////////////////////////////////////

}