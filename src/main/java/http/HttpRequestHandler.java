package http;

public interface HttpRequestHandler {
    HttpResponse handleRequest(HttpClientRequest clientRequest);
}
