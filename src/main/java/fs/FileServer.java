package fs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fm.FileManager;
import fm.FileManagerImpl;
import http.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FileServer {
    private HttpServer httpServer;
    private FileManager fileManager;
    private String dataDirectory;

    private boolean debug;

    public FileServer(int port, String dataDirectory, boolean debug) throws IOException {
        this.httpServer = new HttpServer(port);
        this.httpServer.setDebug(debug);
        this.httpServer.setGetRequestHandler(new GetRequestHandler());
        this.httpServer.setPostRequestHandler(new PostRequestHandler());
        this.fileManager = new FileManagerImpl();
        this.dataDirectory = resolveDataDirectory(dataDirectory);
        this.debug = debug;
    }

    private String resolveDataDirectory(String dataDirectory) {
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path dataDir = Paths.get(dataDirectory).normalize();
        return Paths.get(currentDir.toString(), dataDir.toString()).toString();
    }

    public void run() throws IOException {
        if (this.debug) {
            System.out.println("Serving from " + this.dataDirectory);
        }
        this.httpServer.run();
    }

    private class GetRequestHandler implements  HttpRequestHandler {
        @Override
        public HttpResponse handleRequest(HttpClientRequest clientRequest) {
            HttpResponse response = null;
            String requestPath = clientRequest.getPath();
            if (requestPath.equals("/")) {
                try {
                    Gson gson = new Gson();
                    String filesJson = gson.toJson(listFilesInDataDirectory());
                    JsonObject responseBody = new JsonObject();
                    responseBody.addProperty("success", Boolean.TRUE);
                    responseBody.addProperty("files", filesJson);
                    response = new HttpResponse(200, "OK", responseBody.toString());
                    response.addHeader("Content-Type", "application/json");
                } catch (IOException e) {
                    response = new InternalServerError();
                    response.addHeader("Content-Type", "application/json");
                }
            } else {
                try {
                    String fileContents = readFile(requestPath.substring(1));
                    JsonObject responseBody = new JsonObject();
                    responseBody.addProperty("success", Boolean.TRUE);
                    responseBody.addProperty("file", requestPath.substring(1));
                    responseBody.addProperty("fileContents", fileContents);
                    response = new HttpResponse(200, "OK", responseBody.toString());
                    response.addHeader("Content-Type", "application/json");
                } catch (FileNotFoundException e) {
                    JsonObject responseBody = new JsonObject();
                    responseBody.addProperty("success", Boolean.FALSE);
                    responseBody.addProperty("error", "The requested file does not exist");
                    response = new HttpResponse(404, "File Not Found", responseBody.toString());
                    response.addHeader("Content-Type", "application/json");
                } catch (IOException e) {
                    response = new InternalServerError();
                    response.addHeader("Content-Type", "application/json");
                }
            }
            return response;
        }

        private String readFile(String filePath) throws IOException {
            Path absoluteFilePath = Paths.get(FileServer.this.dataDirectory, filePath);
            BufferedReader reader = new BufferedReader(new FileReader(absoluteFilePath.toFile()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
            return builder.toString();
        }

        private List<String> listFilesInDataDirectory() throws IOException {
            return FileServer.this.fileManager
                    .listDir(Paths.get(dataDirectory).toFile().getAbsolutePath())
                    .stream()
                    .filter(f -> !f.isHidden())
                    .map(f -> f.getName())
                    .collect(Collectors.toList());
        }
    }

    private class PostRequestHandler implements  HttpRequestHandler {
        @Override
        public HttpResponse handleRequest(HttpClientRequest clientRequest) {
            HttpResponse response = null;
            String fileName = getFileNameFromRequestPath(clientRequest.getPath());
            if (fileName != null) {
                try {
                    writeBodyToFile(clientRequest.getBody(), fileName);
                    JsonObject responseBody = new JsonObject();
                    responseBody.addProperty("success", Boolean.TRUE);
                    responseBody.addProperty("message", "Successfully Saved File");
                    response = new HttpResponse(200, "OK", responseBody.toString());
                    response.addHeader("Content-Type", "application/json");
                }
                catch (Exception e) {
                    response = new InternalServerError();
                    response.addHeader("Content-Type", "application/json");
                }
            }
            return response;
        }

        private String getFileNameFromRequestPath(String requestPath) {
            String[] requestParts = requestPath.split("/");
            if (requestParts.length == 2) {
                return requestParts[1];
            }
            return null;
        }

        private void writeBodyToFile(String body, String fileName) throws FileNotFoundException, UnsupportedEncodingException {
            Path absoluteFilePath = Paths.get(FileServer.this.dataDirectory, fileName);
            PrintWriter writer = new PrintWriter(absoluteFilePath.toFile(), "UTF-8");
            writer.println(body);
            writer.close();
        }
    }
}
