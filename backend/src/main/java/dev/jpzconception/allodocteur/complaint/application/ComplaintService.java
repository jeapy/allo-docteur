package dev.jpzconception.allodocteur.complaint.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.complaint.domain.Complaint;
import dev.jpzconception.allodocteur.complaint.domain.ComplaintMessage;
import dev.jpzconception.allodocteur.complaint.domain.ComplaintStatus;
import dev.jpzconception.allodocteur.complaint.infrastructure.ComplaintMessageRepository;
import dev.jpzconception.allodocteur.complaint.infrastructure.ComplaintRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réclamations des patients et des professionnels. Cycle : OPEN → IN_REVIEW → RESOLVED | REJECTED. Un non-auteur reçoit
 * 404 ; l'administration traite, répond dans le fil et le déposant est notifié à chaque changement.
 */
@Service
@Transactional
public class ComplaintService {

    public record MessageView(UUID id, UUID authorId, boolean fromAdmin, String body, Instant createdAt) {}

    public record ComplaintSummary(UUID id, String number, UUID authorId, String authorName, Role authorRole,
                                   UUID appointmentId, String subject, ComplaintStatus status, UUID assignedTo,
                                   Instant createdAt, Instant resolvedAt) {}

    public record ComplaintDetail(ComplaintSummary summary, String description, String resolution,
                                  List<MessageView> messages) {}

    private final ComplaintRepository complaints;
    private final ComplaintMessageRepository messages;
    private final UserRepository users;
    private final AppointmentAccess appointments;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ComplaintService(ComplaintRepository complaints, ComplaintMessageRepository messages, UserRepository users,
                            AppointmentAccess appointments, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.complaints = complaints;
        this.messages = messages;
        this.users = users;
        this.appointments = appointments;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ---- Déposant ----

    public ComplaintDetail create(AuthenticatedUser me, String subject, String description, UUID appointmentId) {
        if (me.role() != Role.PATIENT && me.role() != Role.PROFESSIONAL) {
            throw ApiException.forbidden("FORBIDDEN", "Action réservée aux patients et aux professionnels.");
        }
        if (appointmentId != null) {
            appointments.requireParticipant(me, appointmentId); // 404 si le rendez-vous ne le concerne pas
        }
        Instant now = clock.instant();
        Complaint c = new Complaint();
        c.setNumber(String.format("CLM-%d-%06d", now.atZone(ZoneOffset.UTC).getYear(), complaints.nextNumber()));
        c.setAuthorId(me.id());
        c.setAuthorRole(me.role());
        c.setAppointmentId(appointmentId);
        c.setSubject(subject.strip());
        c.setDescription(description.strip());
        complaints.save(c);
        return detail(c);
    }

    @Transactional(readOnly = true)
    public Page<ComplaintSummary> mine(UUID authorId, int page, int size) {
        return summaries(complaints.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public ComplaintDetail getMine(UUID authorId, UUID id) {
        return detail(owned(authorId, id));
    }

    public ComplaintDetail reply(AuthenticatedUser me, UUID id, String body) {
        Complaint c = owned(me.id(), id);
        requireOpen(c);
        addMessage(c, me.id(), false, body);
        return detail(c);
    }

    // ---- Administration ----

    @Transactional(readOnly = true)
    public Page<ComplaintSummary> adminList(ComplaintStatus status, int page, int size) {
        Pageable p = pageable(page, size);
        return summaries(status == null ? complaints.findAllByOrderByCreatedAtDesc(p)
                : complaints.findByStatusOrderByCreatedAtAsc(status, p));
    }

    @Transactional(readOnly = true)
    public Map<ComplaintStatus, Long> summary() {
        Map<ComplaintStatus, Long> out = new EnumMap<>(ComplaintStatus.class);
        for (ComplaintStatus s : ComplaintStatus.values()) {
            out.put(s, 0L);
        }
        complaints.countByStatus().forEach(r -> out.put(r.getStatus(), r.getTotal()));
        return out;
    }

    @Transactional(readOnly = true)
    public ComplaintDetail adminGet(UUID id) {
        return detail(find(id));
    }

    public ComplaintDetail take(AuthenticatedUser admin, UUID id) {
        Complaint c = find(id);
        if (c.getStatus() != ComplaintStatus.OPEN) {
            throw ApiException.conflict("INVALID_STATE", "Seule une réclamation ouverte peut être prise en charge.");
        }
        c.setStatus(ComplaintStatus.IN_REVIEW);
        c.setAssignedTo(admin.id());
        audit.record(admin.id(), "COMPLAINT_TAKEN", "COMPLAINT", id.toString(), c.getNumber());
        publish(c, false);
        return detail(c);
    }

    public ComplaintDetail adminReply(AuthenticatedUser admin, UUID id, String body) {
        Complaint c = find(id);
        requireOpen(c);
        if (c.getStatus() == ComplaintStatus.OPEN) {
            c.setStatus(ComplaintStatus.IN_REVIEW);
            c.setAssignedTo(admin.id());
        }
        addMessage(c, admin.id(), true, body);
        audit.record(admin.id(), "COMPLAINT_REPLIED", "COMPLAINT", id.toString(), c.getNumber());
        publish(c, true);
        return detail(c);
    }

    public ComplaintDetail resolve(AuthenticatedUser admin, UUID id, String resolution) {
        return close(admin, id, ComplaintStatus.RESOLVED, resolution, "COMPLAINT_RESOLVED");
    }

    public ComplaintDetail reject(AuthenticatedUser admin, UUID id, String reason) {
        return close(admin, id, ComplaintStatus.REJECTED, reason, "COMPLAINT_REJECTED");
    }

    // ---- Interne ----

    private ComplaintDetail close(AuthenticatedUser admin, UUID id, ComplaintStatus target, String text, String action) {
        Complaint c = find(id);
        requireOpen(c);
        c.setStatus(target);
        c.setResolution(text.strip());
        c.setResolvedAt(clock.instant());
        if (c.getAssignedTo() == null) {
            c.setAssignedTo(admin.id());
        }
        audit.record(admin.id(), action, "COMPLAINT", id.toString(), c.getNumber());
        publish(c, false);
        return detail(c);
    }

    private void addMessage(Complaint c, UUID authorId, boolean fromAdmin, String body) {
        ComplaintMessage m = new ComplaintMessage();
        m.setComplaintId(c.getId());
        m.setAuthorId(authorId);
        m.setFromAdmin(fromAdmin);
        m.setBody(body.strip());
        m.setCreatedAt(clock.instant());
        messages.save(m);
    }

    private void publish(Complaint c, boolean reply) {
        events.publishEvent(new ComplaintEvents.Updated(c.getId(), c.getNumber(), c.getAuthorId(), c.getStatus(), reply));
    }

    private static void requireOpen(Complaint c) {
        if (c.getStatus().isClosed()) {
            throw ApiException.conflict("COMPLAINT_CLOSED", "Cette réclamation est clôturée.");
        }
    }

    private Complaint owned(UUID authorId, UUID id) {
        return complaints.findById(id).filter(c -> c.getAuthorId().equals(authorId))
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Réclamation introuvable."));
    }

    private Complaint find(UUID id) {
        return complaints.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Réclamation introuvable."));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private Page<ComplaintSummary> summaries(Page<Complaint> page) {
        Map<UUID, User> authors = users.findAllById(page.getContent().stream().map(Complaint::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return page.map(c -> summary(c, authors.get(c.getAuthorId())));
    }

    private ComplaintDetail detail(Complaint c) {
        ComplaintSummary s = summary(c, users.findById(c.getAuthorId()).orElse(null));
        List<MessageView> thread = messages.findByComplaintIdOrderByCreatedAtAsc(c.getId()).stream()
                .map(m -> new MessageView(m.getId(), m.getAuthorId(), m.isFromAdmin(), m.getBody(), m.getCreatedAt())).toList();
        return new ComplaintDetail(s, c.getDescription(), c.getResolution(), thread);
    }

    private static ComplaintSummary summary(Complaint c, User author) {
        String name = author == null ? null : ((author.getFirstName() == null ? "" : author.getFirstName()) + " "
                + (author.getLastName() == null ? "" : author.getLastName())).strip();
        return new ComplaintSummary(c.getId(), c.getNumber(), c.getAuthorId(), name == null || name.isEmpty() ? null : name,
                c.getAuthorRole(), c.getAppointmentId(), c.getSubject(), c.getStatus(), c.getAssignedTo(),
                c.getCreatedAt(), c.getResolvedAt());
    }
}
