package http;

import com.google.gson.JsonObject;

public class InternalServerError extends HttpResponse {
    private static final int RESPONSE_CODE = 500;
    private static final String REASON_PHRASE = "Internal Server Error";

    public InternalServerError() {
        super(RESPONSE_CODE, REASON_PHRASE);

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("success", Boolean.FALSE);
        responseBody.addProperty("error", "An error occurred");
        HttpResponse response = new HttpResponse(500, "Internal Server Error", responseBody.toString());
        response.addHeader("Content-Type", "application/json");

        setBody(responseBody.toString());
    }
}
