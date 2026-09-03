package dev.minime.skills;

import dev.minime.web.ChatSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Polls every 20s and pushes due reminders to the orb over the WebSocket so it
 * can pulse and speak them.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final SkillService service;
    private final ChatSocketHandler socket;

    public ReminderScheduler(SkillService service, ChatSocketHandler socket) {
        this.service = service;
        this.socket = socket;
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 10_000)
    public void fireDueReminders() {
        for (ReminderEntity reminder : service.due(Instant.now())) {
            log.info("Reminder due: {}", reminder.getText());
            socket.broadcast(Map.of(
                    "type", "reminder",
                    "id", reminder.getId().toString(),
                    "text", reminder.getText(),
                    "dueAt", reminder.getDueAt().toString()));
            service.markNotified(reminder);
        }
    }
}
