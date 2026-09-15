package dev.minime.skills;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SkillService {

    private final SkillRepository skills;
    private final ReminderRepository reminders;

    public SkillService(
            SkillRepository skills,
            ReminderRepository reminders) {

        this.skills = skills;
        this.reminders = reminders;
    }

    // ------------------------------------------------------------- skills ---

    @Transactional(readOnly = true)
    public List<SkillEntity> allSkills() {
        return skills.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public SkillEntity saveSkill(SkillEntity skill) {
        return skills.save(skill);
    }

    @Transactional
    public void deleteSkill(UUID id) {
        skills.deleteById(id);
    }

    @Transactional
    public SkillEntity toggleSkill(UUID id, boolean enabled) {

        SkillEntity skill =
                skills.findById(id)
                        .orElseThrow();

        skill.setEnabled(enabled);

        return skills.save(skill);
    }

    /**
     * System-prompt fragment for skills that apply to this message.
     *
     * Important:
     * This method only injects the configured skill instructions.
     * It does not add confirmation logic for MCP actions.
     *
     * MCP tools such as open_application are handled by ChatService
     * through LangChain4j tool calling.
     */
    @Transactional(readOnly = true)
    public String promptBlock(String userText) {

        String lower =
                userText.toLowerCase(Locale.ROOT);

        List<SkillEntity> active =
                skills.findByEnabledTrueOrderByCreatedAtAsc()
                        .stream()
                        .filter(s ->
                                s.getTriggerWord() == null
                                        || s.getTriggerWord().isBlank()
                                        || lower.contains(
                                        s.getTriggerWord()
                                                .toLowerCase(Locale.ROOT)
                                )
                        )
                        .toList();

        if (active.isEmpty()) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder(
                        "Active skills you must follow:\n"
                );

        for (SkillEntity skill : active) {

            sb.append("- ")
                    .append(skill.getName())
                    .append(": ")
                    .append(skill.getInstructions())
                    .append('\n');
        }

        return sb.toString();
    }

    // ---------------------------------------------------------- reminders ---

    @Transactional(readOnly = true)
    public List<ReminderEntity> allReminders() {
        return reminders.findAllByOrderByDueAtAsc();
    }

    @Transactional
    public ReminderEntity saveReminder(
            ReminderEntity reminder) {

        return reminders.save(reminder);
    }

    @Transactional
    public ReminderEntity completeReminder(
            UUID id,
            boolean done) {

        ReminderEntity reminder =
                reminders.findById(id)
                        .orElseThrow();

        reminder.setDone(done);

        return reminders.save(reminder);
    }

    @Transactional
    public void deleteReminder(UUID id) {
        reminders.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ReminderEntity> due(Instant now) {

        return reminders
                .findByDoneFalseAndNotifiedFalseAndDueAtLessThanEqualOrderByDueAtAsc(
                        now
                );
    }

    @Transactional
    public void markNotified(
            ReminderEntity reminder) {

        reminder.setNotified(true);

        reminders.save(reminder);
    }

    /**
     * Upcoming open reminders injected into the prompt
     * so MiniMe can use them as conversational context.
     */
    @Transactional(readOnly = true)
    public String reminderBlock() {

        List<ReminderEntity> open =
                reminders.findByDoneFalseOrderByDueAtAsc();

        if (open.isEmpty()) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder(
                        "Open reminders (UTC):\n"
                );

        open.stream()
                .limit(10)
                .forEach(reminder ->
                        sb.append("- ")
                                .append(reminder.getDueAt())
                                .append(" — ")
                                .append(reminder.getText())
                                .append('\n')
                );

        return sb.toString();
    }
}
