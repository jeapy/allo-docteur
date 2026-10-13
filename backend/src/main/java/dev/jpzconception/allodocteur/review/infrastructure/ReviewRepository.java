package dev.jpzconception.allodocteur.review.infrastructure;

import dev.jpzconception.allodocteur.review.domain.Review;
import dev.jpzconception.allodocteur.review.domain.ReviewStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Page<Review> findByProfessionalIdAndStatusOrderByCreatedAtDesc(UUID professionalId, ReviewStatus status, Pageable pageable);

    Page<Review> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    Page<Review> findByProfessionalIdOrderByCreatedAtDesc(UUID professionalId, Pageable pageable);

    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Recalcule atomiquement la note moyenne et le nombre d'avis visibles d'un professionnel. */
    @Modifying
    @Query(value = """
            UPDATE professionals p SET
                rating_count = (SELECT COUNT(*) FROM reviews r WHERE r.professional_id = p.user_id AND r.status = 'VISIBLE'),
                rating_avg = COALESCE((SELECT ROUND(AVG(r.rating)::numeric, 2) FROM reviews r
                                       WHERE r.professional_id = p.user_id AND r.status = 'VISIBLE'), 0)
            WHERE p.user_id = :professionalId""", nativeQuery = true)
    void refreshRating(@Param("professionalId") UUID professionalId);
}
