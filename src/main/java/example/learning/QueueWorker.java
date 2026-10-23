package example.learning;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RestController
public class QueueWorker {
    private final GameInbox inbox;
    public QueueWorker(GameInbox inbox) { this.inbox = inbox; }
    public static void main(String[] args) { SpringApplication.run(QueueWorker.class, args); }

    @PostMapping("/notices")
    public ResponseEntity<?> enqueue(@RequestBody GameInbox.Notice notice) throws IOException, InterruptedException {
        inbox.enqueue(notice);
        return ResponseEntity.accepted().body(java.util.Map.of("messageId", notice.messageId()));
    }

    @PostMapping("/deliver")
    public java.util.Map<String, Integer> deliver(@RequestParam(defaultValue = "10") int maxMessages)
            throws IOException, InterruptedException {
        if (maxMessages < 1 || maxMessages > 100) throw new IllegalArgumentException("maxMessages must be 1..100");
        return java.util.Map.of("published", inbox.deliver(maxMessages));
    }

    @ExceptionHandler(InfraiClient.InfraiException.class)
    public ResponseEntity<String> upstream(InfraiClient.InfraiException error) {
        return ResponseEntity.status(error.status() >= 400 && error.status() < 500
                ? error.status() : HttpStatus.BAD_GATEWAY.value()).body(error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
