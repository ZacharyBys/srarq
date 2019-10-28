package http;

import java.util.stream.Collectors;

public class HttpResponse extends HttpMessage {
    private int status;
    private String reasonPhrase;
    private String body;

    public HttpResponse() {

    }

    public HttpResponse(int status, String reasonPhrase) {
        super();
        this.status = status;
        this.reasonPhrase = reasonPhrase;
    }

    public HttpResponse(int status, String reasonPhrase, String body) {
        super();
        this.status = status;
        this.reasonPhrase = reasonPhrase;
        this.body = body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    protected String getStartLine() {
        return String.format("%s %d %s", this.HTTP_VERSION, this.status, this.reasonPhrase);
    }

    @Override
    public String toString() {
        String headers = getHeaders().stream().collect(Collectors.joining("\n"));
        return String.format("%s\n%s\r\n\r\n%s", getStartLine(), headers, this.body);
    }

}
