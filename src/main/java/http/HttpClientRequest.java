package http;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HttpClientRequest extends HttpMessage {
    private String method;
    private String path;
    private String body;

    public HttpClientRequest() {

    }

    public static HttpClientRequest fromRaw(String raw) {
        HttpClientRequest clientRequest = new HttpClientRequest();
        addStatusAndReasonPhraseToResponseFromRaw(raw, clientRequest);
        addHeadersToResponseFromRaw(raw, clientRequest);
        addBodyToResponseFromRaw(raw, clientRequest);
        return clientRequest;
    }

    public String getMethod() {
        return this.method;
    }

    public String getPath() {
        return this.path;
    }

    public String getBody() { return this.body; }

    private static void addStatusAndReasonPhraseToResponseFromRaw(String raw, HttpClientRequest clientRequest) {
        Pattern startLineRegex = Pattern.compile("^(GET|POST)\\s(\\/\\w*[(\\-|\\_)\\w+]*(\\.\\w+)?)\\s(HTTP\\/\\d.\\d)(\\n|\\r|.)*");
        String startLine = raw.split("\n")[0];
        Matcher matcher = startLineRegex.matcher(startLine);
        if (matcher.find()) {
            clientRequest.method = matcher.group(1);
            clientRequest.path = matcher.group(2);
            clientRequest.HTTP_VERSION = matcher.group(4);
        }
    }

    private static void addHeadersToResponseFromRaw(String raw, HttpClientRequest clientRequest) {
        Map<String, String> headers = Arrays.stream(raw.split("(\n|\r)"))
                .filter(line -> line.matches("([\\w-]+):(.*)"))
                .collect(Collectors.toMap(
                        header -> header.split(":")[0],
                        header -> header.split(":")[1])
                );
        clientRequest.headers = headers;
    }

    private static void addBodyToResponseFromRaw(String raw, HttpClientRequest clientRequest) {
        String[] split = raw.split("\r\n\r\n");
        String body = "";
        if (split.length > 1) {
            body = split[1];
        }
        clientRequest.body = body;
    }

    @Override
    protected String getStartLine() {
        return String.format("%s %s %s", this.method, this.path, this.HTTP_VERSION);
    }

    @Override
    public String toString() {
        String headers = getHeaders().stream().collect(Collectors.joining("\n"));
        return String.format("%s\n%s\n\n%s", getStartLine(), headers, this.body);
    }
}
