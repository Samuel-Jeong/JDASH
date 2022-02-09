package dash.handler.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.handler.codec.http.HttpMethod;

public class HttpMessageRoute {

    ////////////////////////////////////////////////////////////
    private final HttpMethod method;
    private final String uri;
    private HttpMessageHandler handler;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMessageRoute(final HttpMethod method, final String uri, final HttpMessageHandler handler) {
        this.method = method;
        this.uri = uri;
        this.handler = handler;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public HttpMethod getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public HttpMessageHandler getHandler() {
        return handler;
    }

    public void setHandler(HttpMessageHandler handler) {
        this.handler = handler;
    }

    public boolean matches(final HttpMethod method, final String path) {
        return this.method.equals(method) && this.uri.equals(path);
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}