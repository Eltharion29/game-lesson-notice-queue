package example.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class GameInboxTest {
    @Test void routesLearningGameDecisions() {
        assertEquals("asset.ready", GameInbox.eventFor("asset"));
        assertEquals("live_event.started", GameInbox.eventFor("live_event"));
        assertEquals("moderation.review", GameInbox.eventFor("moderation"));
        assertThrows(IllegalArgumentException.class, () -> GameInbox.eventFor("unknown"));
    }
}
