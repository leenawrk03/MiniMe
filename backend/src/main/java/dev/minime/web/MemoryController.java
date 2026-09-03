package dev.minime.web;

import dev.minime.memory.MemoryFact;
import dev.minime.memory.MemoryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** "What I know about you" tab. Everything here is local and user-editable. */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    public record CreateRequest(@NotBlank String content) {}
    public record UpdateRequest(String content, Boolean pinned) {}

    private final MemoryService memory;

    public MemoryController(MemoryService memory) {
        this.memory = memory;
    }

    @GetMapping
    public List<MemoryFact> list() {
        return memory.all();
    }

    @GetMapping("/search")
    public List<MemoryFact> search(@RequestParam String q) {
        return memory.recall(q);
    }

    @PostMapping
    public MemoryFact create(@RequestBody CreateRequest request) {
        return memory.add(request.content(), "fact", "manual", 1.0);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        if (request.content() != null && !request.content().isBlank()) memory.update(id, request.content());
        if (request.pinned() != null) memory.setPinned(id, request.pinned());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        memory.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> forgetAll() {
        memory.forgetEverything();
        return ResponseEntity.noContent().build();
    }
}
