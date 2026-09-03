package dev.minime.skills;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<SkillEntity, UUID> {
    List<SkillEntity> findByEnabledTrueOrderByCreatedAtAsc();
    List<SkillEntity> findAllByOrderByCreatedAtAsc();
}
