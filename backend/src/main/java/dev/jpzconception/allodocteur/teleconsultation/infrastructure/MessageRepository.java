package dev.jpzconception.allodocteur.teleconsultation.infrastructure;

import dev.jpzconception.allodocteur.teleconsultation.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByAppointmentIdOrderBySentAtAscIdAsc(UUID appointmentId, Pageable pageable);

    List<Message> findByAppointmentIdAndSentAtAfterOrderBySentAtAscIdAsc(UUID appointmentId, Instant since, Pageable pageable);

    long countByAppointmentIdAndSenderIdNotAndReadAtIsNull(UUID appointmentId, UUID readerId);

    @Modifying(clearAutomatically = true)
    @Query("update Message m set m.readAt = :now where m.appointmentId = :aid and m.senderId <> :reader and m.readAt is null")
    int markRead(@Param("aid") UUID appointmentId, @Param("reader") UUID readerId, @Param("now") Instant now);
}
