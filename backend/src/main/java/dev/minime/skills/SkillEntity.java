package dev.minime.skills;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A custom skill: extra instructions MiniMe follows, optionally trigger-gated. */
@Entity
@Table(name = "skill")
public class SkillEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 120)
    private String name;

    /** When set, the skill only applies if the user's message contains this word. */
    @Column(name = "trigger_word", length = 120)
    private String triggerWord;

    @Column(nullable = false, columnDefinition = "text")
    private String instructions;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTriggerWord() { return triggerWord; }
    public void setTriggerWord(String triggerWord) { this.triggerWord = triggerWord; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
