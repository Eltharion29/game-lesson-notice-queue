package example.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiClient(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                        @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.key = key;
    }

    public JsonNode post(String path, Map<String, ?> body, String requestId) throws IOException, InterruptedException {
        String payload = json.writeValueAsString(body);
        for (int attempt = 0; ; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 4) {
                long delay = response.headers().firstValue("Retry-After")
                        .map(value -> { try { return Long.parseLong(value) * 1000L; }
                                        catch (NumberFormatException ignored) { return 0L; } })
                        .filter(value -> value > 0)
                        .orElse(500L * (1L << attempt));
                Thread.sleep(delay);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                throw new InfraiException(response.statusCode(), envelope.path("error").toString());
            }
            if (response.statusCode() >= 500) throw new IOException("Upstream HTTP " + response.statusCode());
            return envelope.path("data");
        }
    }

    public static class InfraiException extends RuntimeException {
        private final int status;
        public InfraiException(int status, String error) { super(error); this.status = status; }
        public int status() { return status; }
    }
}
