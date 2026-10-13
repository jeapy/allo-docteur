package dev.jpzconception.allodocteur.complaint.infrastructure;

import dev.jpzconception.allodocteur.complaint.domain.Complaint;
import dev.jpzconception.allodocteur.complaint.domain.ComplaintStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    @Query(value = "select nextval('complaint_number_seq')", nativeQuery = true)
    long nextNumber();

    Page<Complaint> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    Page<Complaint> findByStatusOrderByCreatedAtAsc(ComplaintStatus status, Pageable pageable);

    Page<Complaint> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ComplaintStatus status);

    @Query("select c.status as status, count(c) as total from Complaint c group by c.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        ComplaintStatus getStatus();

        long getTotal();
    }
}
