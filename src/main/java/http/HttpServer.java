package http;

import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    private ServerSocket server;
    private boolean running;
    private boolean debug;

    private HttpRequestHandler getRequestHandler;
    private HttpRequestHandler postRequestHandler;

    public HttpServer(int port) throws IOException {
        this.server = new ServerSocket(port);
        System.out.println("Server listening on port " + this.server.getLocalPort());
    }

    public void run() throws IOException {
        this.running = true;
        while (running) {
            Socket client = server.accept();
            try {
                if (this.debug) {
                    System.out.println("Connection accepted\n");
                }
                readAndHandleRequestFromClient(client);
            } catch (Exception e) {
                e.printStackTrace();
                HttpResponse response = new InternalServerError();
                response.addHeader("Content-Type", "application/json");
                client.getOutputStream().write(response.toString().getBytes());
            } finally {
                client.close();
            }
        }
    }

    public void stop() {
        this.running = false;
    }

    public void setGetRequestHandler(HttpRequestHandler requestHandler) {
        this.getRequestHandler = requestHandler;
    }

    public void setPostRequestHandler(HttpRequestHandler requestHandler) {
        this.postRequestHandler = requestHandler;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    private void readAndHandleRequestFromClient(Socket client) throws IOException {
        BufferedReader reader = new BufferedReader( new InputStreamReader(client.getInputStream()));

        StringBuilder stringBuilder = new StringBuilder();
        while (reader.ready()) {
            stringBuilder.append((char)reader.read());
        }
        System.out.println(stringBuilder);
        HttpClientRequest clientRequest = HttpClientRequest.fromRaw(stringBuilder.toString());

        if (this.debug) {
            System.out.println("Received request:\n");
            System.out.println(clientRequest);
        }

        HttpResponse serverResponse = handleHttpClientRequest(clientRequest);
        client.getOutputStream().write(serverResponse.toString().getBytes());
        reader.close();
    }

    private HttpResponse handleHttpClientRequest(HttpClientRequest clientRequest) {
        HttpResponse serverResponse = null;
        switch (clientRequest.getMethod()) {
            case "GET": {
                serverResponse = getRequestHandler.handleRequest(clientRequest);
                break;
            }
            case "POST": {
                serverResponse = postRequestHandler.handleRequest(clientRequest);
                break;
            }
        }
        return serverResponse;
    }

}
