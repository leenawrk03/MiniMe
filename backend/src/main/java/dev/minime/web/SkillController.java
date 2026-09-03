package dev.minime.web;

import dev.minime.skills.ReminderEntity;
import dev.minime.skills.SkillEntity;
import dev.minime.skills.SkillService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** "Skills & Reminders" tab. */
@RestController
@RequestMapping("/api")
public class SkillController {

    public record SkillDto(String id, String name, String triggerWord, String instructions,
                           boolean enabled, Instant createdAt) {
        static SkillDto from(SkillEntity s) {
            return new SkillDto(s.getId().toString(), s.getName(), s.getTriggerWord(),
                    s.getInstructions(), s.isEnabled(), s.getCreatedAt());
        }
    }

    public record SkillRequest(@NotBlank String name, String triggerWord, @NotBlank String instructions) {}

    public record ReminderDto(String id, String text, Instant dueAt, boolean done, Instant createdAt) {
        static ReminderDto from(ReminderEntity r) {
            return new ReminderDto(r.getId().toString(), r.getText(), r.getDueAt(), r.isDone(), r.getCreatedAt());
        }
    }

    public record ReminderRequest(@NotBlank String text, Instant dueAt) {}

    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    // ------------------------------------------------------------- skills ---
    @GetMapping("/skills")
    public List<SkillDto> skills() {
        return service.allSkills().stream().map(SkillDto::from).toList();
    }

    @PostMapping("/skills")
    public SkillDto createSkill(@RequestBody SkillRequest request) {
        SkillEntity skill = new SkillEntity();
        skill.setName(request.name());
        skill.setTriggerWord(request.triggerWord());
        skill.setInstructions(request.instructions());
        return SkillDto.from(service.saveSkill(skill));
    }

    @PatchMapping("/skills/{id}")
    public SkillDto toggleSkill(@PathVariable UUID id, @RequestParam boolean enabled) {
        return SkillDto.from(service.toggleSkill(id, enabled));
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<Void> deleteSkill(@PathVariable UUID id) {
        service.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------- reminders ---
    @GetMapping("/reminders")
    public List<ReminderDto> reminders() {
        return service.allReminders().stream().map(ReminderDto::from).toList();
    }

    @PostMapping("/reminders")
    public ReminderDto createReminder(@RequestBody ReminderRequest request) {
        ReminderEntity reminder = new ReminderEntity();
        reminder.setText(request.text());
        reminder.setDueAt(request.dueAt() == null ? Instant.now().plusSeconds(3600) : request.dueAt());
        return ReminderDto.from(service.saveReminder(reminder));
    }

    @PatchMapping("/reminders/{id}")
    public ReminderDto completeReminder(@PathVariable UUID id, @RequestParam boolean done) {
        return ReminderDto.from(service.completeReminder(id, done));
    }

    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id) {
        service.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }
}
