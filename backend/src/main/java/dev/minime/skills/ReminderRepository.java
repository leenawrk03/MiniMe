package dev.minime.skills;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {
    List<ReminderEntity> findAllByOrderByDueAtAsc();
    List<ReminderEntity> findByDoneFalseAndNotifiedFalseAndDueAtLessThanEqualOrderByDueAtAsc(Instant now);
    List<ReminderEntity> findByDoneFalseOrderByDueAtAsc();
}
