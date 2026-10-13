package dev.jpzconception.allodocteur.complaint.infrastructure;

import dev.jpzconception.allodocteur.complaint.domain.ComplaintMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, UUID> {

    List<ComplaintMessage> findByComplaintIdOrderByCreatedAtAsc(UUID complaintId);
}
