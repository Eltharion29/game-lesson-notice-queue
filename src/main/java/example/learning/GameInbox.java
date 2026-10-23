package example.learning;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GameInbox {
    private static final String QUEUE = "game-notices";
    private final InfraiClient api;
    public GameInbox(InfraiClient api) { this.api = api; }

    public record Notice(String messageId, String playerId, String kind, String itemId) {}

    public static String eventFor(String kind) {
        return switch (kind) {
            case "asset" -> "asset.ready";
            case "live_event" -> "live_event.started";
            case "moderation" -> "moderation.review";
            default -> throw new IllegalArgumentException("Unknown notice kind");
        };
    }

    public JsonNode enqueue(Notice notice) throws IOException, InterruptedException {
        eventFor(notice.kind());
        return api.post("/v1/queue/publish", Map.of("queue", QUEUE, "payload", notice), notice.messageId());
    }

    public int deliver(int maxMessages) throws IOException, InterruptedException {
        JsonNode batch = api.post("/v1/queue/consume",
                Map.of("queue", QUEUE, "max_messages", maxMessages, "visibility_timeout", 30),
                "consume-" + java.util.UUID.randomUUID());
        int count = 0;
        for (JsonNode message : batch.path("messages")) {
            JsonNode payload = message.path("payload");
            String messageId = message.path("message_id").asText();
            String playerId = payload.path("playerId").asText();
            String event = eventFor(payload.path("kind").asText());
            api.post("/v1/realtime/publish", Map.of(
                    "channel", "player-" + playerId,
                    "event", event,
                    "data", Map.of("messageId", messageId, "itemId", payload.path("itemId").asText()),
                    "account_id", playerId), messageId);
            count++;
        }
        return count;
    }
}
