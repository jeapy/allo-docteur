package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.notification.application.EmailSender;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.AdminProfessionalRow;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalDetail;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalDocumentRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workflow de validation des professionnels par l'administration. */
@Service
@Transactional
public class VerificationService {

    private final ProfessionalRepository professionals;
    private final ProfessionalDocumentRepository documents;
    private final ProfessionalService professionalService;
    private final ProfessionalAssembler assembler;
    private final AuditService audit;
    private final EmailSender email;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    public VerificationService(ProfessionalRepository professionals, ProfessionalDocumentRepository documents,
                               ProfessionalService professionalService, ProfessionalAssembler assembler,
                               AuditService audit, EmailSender email, Clock clock,
                               org.springframework.context.ApplicationEventPublisher events) {
        this.professionals = professionals;
        this.documents = documents;
        this.professionalService = professionalService;
        this.assembler = assembler;
        this.audit = audit;
        this.email = email;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<AdminProfessionalRow> list(VerificationStatus status, Integer typeId, String q,
                                           int page, int size, String sort) {
        Sort order = "oldest".equals(sort) ? Sort.by("createdAt").ascending() : Sort.by("createdAt").descending();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), order);
        return professionals.findAll(filter(status, typeId, q), pageable).map(assembler::toRow);
    }

    @Transactional(readOnly = true)
    public ProfessionalDetail detail(UUID id) {
        return professionalService.detail(professionalService.require(id), true);
    }

    public ProfessionalDetail approve(UUID adminId, UUID id) {
        Professional p = professionalService.require(id);
        if (documents.countByProfessionalId(id) == 0) {
            throw ApiException.conflict("DOCUMENTS_REQUIRED", "Au moins un justificatif est requis avant validation.");
        }
        p.approve(adminId, Instant.now(clock));
        audit.record(adminId, "PROFESSIONAL_APPROVED", "PROFESSIONAL", id.toString(), p.getLicenseNumber());
        events.publishEvent(new ProfessionalEvents.VerificationChanged(id, VerificationStatus.APPROVED));
        email.send(p.getUser().getEmail(), "Votre profil Allo Docteur est validé",
                "Votre profil est désormais visible dans le répertoire des professionnels.");
        return professionalService.detail(p, true);
    }

    public ProfessionalDetail reject(UUID adminId, UUID id, String reason) {
        Professional p = professionalService.require(id);
        p.reject(adminId, Instant.now(clock), reason);
        audit.record(adminId, "PROFESSIONAL_REJECTED", "PROFESSIONAL", id.toString(), reason);
        events.publishEvent(new ProfessionalEvents.VerificationChanged(id, VerificationStatus.REJECTED));
        email.send(p.getUser().getEmail(), "Votre profil Allo Docteur n'a pas été validé",
                "Motif : " + reason + "\nVous pouvez corriger votre profil et le soumettre à nouveau.");
        return professionalService.detail(p, true);
    }

    public ProfessionalDetail suspend(UUID adminId, UUID id, String reason) {
        Professional p = professionalService.require(id);
        p.suspend(adminId, Instant.now(clock), reason);
        audit.record(adminId, "PROFESSIONAL_SUSPENDED", "PROFESSIONAL", id.toString(), reason);
        events.publishEvent(new ProfessionalEvents.VerificationChanged(id, VerificationStatus.SUSPENDED));
        email.send(p.getUser().getEmail(), "Votre profil Allo Docteur est suspendu", "Motif : " + reason);
        return professionalService.detail(p, true);
    }

    private static Specification<Professional> filter(VerificationStatus status, Integer typeId, String q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("verificationStatus"), status));
            if (typeId != null) ps.add(cb.equal(root.get("type").get("id"), typeId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var user = root.join("user");
                ps.add(cb.or(
                        cb.like(cb.lower(user.get("email")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(user.<String>get("firstName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(user.<String>get("lastName"), "")), like, '\\'),
                        cb.like(cb.lower(root.get("licenseNumber")), like, '\\')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
