package dash.handler.definition;

import io.netty.handler.codec.http.HttpMethod;

public class HttpMessageRoute {
    private final HttpMethod method;
    private final String path;
    private final HttpMessageHandler handler;

    public HttpMessageRoute(final HttpMethod method, final String path, final HttpMessageHandler handler) {
        this.method = method;
        this.path = path;
        this.handler = handler;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public HttpMessageHandler getHandler() {
        return handler;
    }

    public boolean matches(final HttpMethod method, final String path) {
        return this.method.equals(method) && this.path.equals(path);
    }
}