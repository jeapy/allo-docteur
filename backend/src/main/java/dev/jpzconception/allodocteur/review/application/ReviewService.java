package dev.jpzconception.allodocteur.review.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.review.domain.Review;
import dev.jpzconception.allodocteur.review.domain.ReviewStatus;
import dev.jpzconception.allodocteur.review.infrastructure.ReviewRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avis et notes : dépôt par le patient après une consultation terminée, modération par l'administration. */
@Service
@Transactional
public class ReviewService {

    public record ReviewView(UUID id, UUID appointmentId, UUID professionalId, String authorName, int rating,
                             String comment, ReviewStatus status, String hiddenReason, Instant createdAt) {}

    private final ReviewRepository reviews;
    private final AppointmentAccess appointments;
    private final UserRepository users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, AppointmentAccess appointments, UserRepository users,
                         AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.reviews = reviews;
        this.appointments = appointments;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public ReviewView post(AuthenticatedUser me, UUID appointmentId, int rating, String comment) {
        var appt = appointments.requireParticipant(me, appointmentId);
        if (!appt.isPatient(me.id())) {
            throw ApiException.notFound("APPOINTMENT_NOT_FOUND", "Rendez-vous introuvable.");
        }
        if (appt.status() != AppointmentStatus.COMPLETED) {
            throw ApiException.conflict("REVIEW_NOT_ALLOWED", "Vous pourrez noter ce professionnel une fois la consultation terminée.");
        }
        if (reviews.existsByAppointmentId(appointmentId)) {
            throw ApiException.conflict("ALREADY_REVIEWED", "Vous avez déjà donné votre avis sur cette consultation.");
        }
        var user = users.findById(me.id()).orElseThrow();
        Review r = new Review();
        r.setAppointmentId(appointmentId);
        r.setProfessionalId(appt.professionalId());
        r.setAccountId(me.id());
        r.setAuthorName(shortName(user.getFirstName(), user.getLastName()));
        r.setRating(rating);
        r.setComment(comment == null || comment.isBlank() ? null : comment.strip());
        try {
            reviews.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_REVIEWED", "Vous avez déjà donné votre avis sur cette consultation.");
        }
        reviews.refreshRating(r.getProfessionalId());
        events.publishEvent(new ReviewEvents.Posted(r.getId(), r.getProfessionalId(), rating));
        return view(r);
    }

    @Transactional(readOnly = true)
    public Page<ReviewView> forProfessional(UUID professionalId, int page, int size) {
        return reviews.findByProfessionalIdAndStatusOrderByCreatedAtDesc(professionalId, ReviewStatus.VISIBLE, pageable(page, size))
                .map(ReviewService::view);
    }

    @Transactional(readOnly = true)
    public Page<ReviewView> mine(UUID accountId, int page, int size) {
        return reviews.findByAccountIdOrderByCreatedAtDesc(accountId, pageable(page, size)).map(ReviewService::view);
    }

    @Transactional(readOnly = true)
    public Page<ReviewView> adminList(ReviewStatus status, UUID professionalId, int page, int size) {
        Pageable p = pageable(page, size);
        Page<Review> found;
        if (professionalId != null) {
            found = reviews.findByProfessionalIdOrderByCreatedAtDesc(professionalId, p);
        } else if (status != null) {
            found = reviews.findByStatusOrderByCreatedAtDesc(status, p);
        } else {
            found = reviews.findAllByOrderByCreatedAtDesc(p);
        }
        return found.map(ReviewService::view);
    }

    public ReviewView hide(AuthenticatedUser admin, UUID id, String reason) {
        Review r = find(id);
        if (r.getStatus() == ReviewStatus.HIDDEN) {
            throw ApiException.conflict("REVIEW_ALREADY_HIDDEN", "Cet avis est déjà masqué.");
        }
        r.setStatus(ReviewStatus.HIDDEN);
        r.setHiddenReason(reason);
        r.setHiddenBy(admin.id());
        r.setHiddenAt(clock.instant());
        reviews.flush();
        reviews.refreshRating(r.getProfessionalId());
        audit.record(admin.id(), "REVIEW_HIDDEN", "REVIEW", id.toString(), reason);
        return view(r);
    }

    public ReviewView show(AuthenticatedUser admin, UUID id) {
        Review r = find(id);
        if (r.getStatus() == ReviewStatus.VISIBLE) {
            throw ApiException.conflict("REVIEW_ALREADY_VISIBLE", "Cet avis est déjà visible.");
        }
        r.setStatus(ReviewStatus.VISIBLE);
        r.setHiddenReason(null);
        r.setHiddenBy(null);
        r.setHiddenAt(null);
        reviews.flush();
        reviews.refreshRating(r.getProfessionalId());
        audit.record(admin.id(), "REVIEW_RESTORED", "REVIEW", id.toString(), null);
        return view(r);
    }

    private Review find(UUID id) {
        return reviews.findById(id).orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Avis introuvable."));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    static String shortName(String first, String last) {
        String f = first == null ? "" : first.strip();
        String l = last == null ? "" : last.strip();
        if (f.isEmpty() && l.isEmpty()) {
            return "Patient";
        }
        if (l.isEmpty()) {
            return f;
        }
        return (f + " " + l.substring(0, 1).toUpperCase() + ".").strip();
    }

    private static ReviewView view(Review r) {
        return new ReviewView(r.getId(), r.getAppointmentId(), r.getProfessionalId(), r.getAuthorName(), r.getRating(),
                r.getComment(), r.getStatus(), r.getHiddenReason(), r.getCreatedAt());
    }
}
