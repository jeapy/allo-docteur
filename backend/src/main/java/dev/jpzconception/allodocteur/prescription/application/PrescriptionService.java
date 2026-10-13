package dev.jpzconception.allodocteur.prescription.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalRecordProvisioner;
import dev.jpzconception.allodocteur.medicalrecord.application.RecordAccessService;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.CancelRequest;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.DetailDto;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.IssueRequest;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.ItemDto;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.ItemRequest;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.SummaryDto;
import dev.jpzconception.allodocteur.prescription.domain.Prescription;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionHasher;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionItem;
import dev.jpzconception.allodocteur.prescription.domain.PrescriptionStatus;
import dev.jpzconception.allodocteur.prescription.infrastructure.PrescriptionItemRepository;
import dev.jpzconception.allodocteur.prescription.infrastructure.PrescriptionRepository;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup.Summary;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ordonnances électroniques. Toute lecture du contenu passe par {@link RecordAccessService} (propriété, lien de soin, consentement). */
@Service
@Transactional
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final AppointmentAccess appointments;
    private final MedicalRecordProvisioner provisioner;
    private final RecordAccessService access;
    private final ProfessionalLookup professionals;
    private final PrescriptionProperties props;
    private final Clock clock;

    private final org.springframework.context.ApplicationEventPublisher events;

    public PrescriptionService(PrescriptionRepository prescriptions, PrescriptionItemRepository items,
                               AppointmentAccess appointments, MedicalRecordProvisioner provisioner,
                               RecordAccessService access, ProfessionalLookup professionals,
                               PrescriptionProperties props, Clock clock,
                               org.springframework.context.ApplicationEventPublisher events) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.appointments = appointments;
        this.provisioner = provisioner;
        this.access = access;
        this.professionals = professionals;
        this.props = props;
        this.clock = clock;
        this.events = events;
    }

    // ------------------------------------------------------------------ émission

    public DetailDto issue(AuthenticatedUser me, UUID appointmentId, IssueRequest req) {
        Ref ref = appointments.requireParticipant(me, appointmentId);
        Instant now = Instant.now(clock);

        Summary pro = professionals.find(me.id())
                .orElseThrow(() -> ApiException.forbidden("PRESCRIBER_NOT_ALLOWED", "Profil professionnel introuvable."));
        if (!props.allowedProfessionalTypes().contains(pro.typeCode())) {
            throw ApiException.forbidden("PRESCRIBER_NOT_ALLOWED",
                    "Votre profession n'est pas habilitée à émettre des ordonnances.");
        }
        if (!isPrescribable(ref, now)) {
            throw ApiException.conflict("PRESCRIPTION_NOT_ALLOWED",
                    "Une ordonnance ne peut être émise que pendant la consultation ou dans les "
                            + props.issueWindowDays() + " jours qui suivent.");
        }

        UUID recordId = provisioner.ensure(ref.patientId(), ref.dependentId());
        // lien de soin + consentement actif, journalisé
        access.resolve(me, recordId, AccessAction.CREATE_PRESCRIPTION, appointmentId, false);

        Prescription p = new Prescription();
        p.setNumber(formatNumber(now, prescriptions.nextNumber()));
        p.setAppointmentId(appointmentId);
        p.setRecordId(recordId);
        p.setProfessionalId(me.id());
        p.setAccountId(ref.patientId());
        p.setDependentId(ref.dependentId());
        p.setBeneficiaryName(ref.beneficiaryName());
        p.setIssuedAt(now);
        p.setValidUntil(now.plus(Duration.ofDays(props.validityDays())));
        p.setNotes(blankToNull(req.notes()));

        List<PrescriptionItem> lines = new ArrayList<>();
        int n = 1;
        for (ItemRequest r : req.items()) {
            PrescriptionItem i = new PrescriptionItem();
            i.setPrescriptionId(p.getId());
            i.setLineNumber(n++);
            i.setMedicationName(r.medicationName().trim());
            i.setDosage(blankToNull(r.dosage()));
            i.setFrequency(blankToNull(r.frequency()));
            i.setDurationDays(r.durationDays());
            i.setQuantity(r.quantity());
            i.setInstructions(blankToNull(r.instructions()));
            lines.add(i);
        }
        p.setIntegrityHash(PrescriptionHasher.hash(p, lines));
        prescriptions.save(p);
        items.saveAll(lines);
        events.publishEvent(new PrescriptionEvents.Issued(p.getId(), p.getAccountId(), p.getProfessionalId()));
        return toDetail(p, lines, pro);
    }

    private boolean isPrescribable(Ref ref, Instant now) {
        if (ref.status() == AppointmentStatus.IN_PROGRESS) return true;
        if (ref.status() == AppointmentStatus.COMPLETED && ref.completedAt() != null) {
            return !now.isAfter(ref.completedAt().plus(Duration.ofDays(props.issueWindowDays())));
        }
        return false;
    }

    static String formatNumber(Instant now, long seq) {
        int year = now.atZone(java.time.ZoneOffset.UTC).getYear();
        return String.format("ORD-%d-%06d", year, seq);
    }

    // ------------------------------------------------------------------ lecture

    /** Mes ordonnances : reçues (patient) ou émises (professionnel). Résumés sans donnée de santé. */
    @Transactional(readOnly = true)
    public Page<SummaryDto> mine(AuthenticatedUser me, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<Prescription> found = me.role() == Role.PATIENT
                ? prescriptions.findByAccountId(me.id(), pageable)
                : prescriptions.findByProfessionalId(me.id(), pageable);
        Instant now = Instant.now(clock);
        Map<UUID, String> names = proNames(found.getContent());
        List<SummaryDto> dtos = found.getContent().stream().map(p -> new SummaryDto(p.getId(), p.getNumber(),
                p.getAppointmentId(), p.getRecordId(), names.get(p.getProfessionalId()), p.getBeneficiaryName(),
                p.getIssuedAt(), p.getValidUntil(), p.getStatus(), p.isExpired(now))).toList();
        return new PageImpl<>(dtos, pageable, found.getTotalElements());
    }

    public DetailDto get(AuthenticatedUser me, UUID id) {
        Prescription p = require(id);
        access.resolve(me, p.getRecordId(), AccessAction.VIEW_PRESCRIPTION, id, false);
        return toDetail(p, items.findByPrescriptionIdOrderByLineNumber(id), professionals.find(p.getProfessionalId()).orElse(null));
    }

    /** Ordonnances d'un dossier (propriétaire, ou professionnel traitant avec consentement). */
    public Page<DetailDto> forRecord(AuthenticatedUser me, UUID recordId, int page, int size) {
        access.resolve(me, recordId, AccessAction.VIEW_PRESCRIPTIONS, null, false);
        Pageable pageable = pageable(page, size);
        Page<Prescription> found = prescriptions.findByRecordId(recordId, pageable);
        Set<UUID> ids = found.getContent().stream().map(Prescription::getId).collect(Collectors.toSet());
        Map<UUID, List<PrescriptionItem>> byPrescription = ids.isEmpty() ? Map.of()
                : items.findByPrescriptionIdInOrderByLineNumber(ids).stream()
                        .collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId));
        Map<UUID, Summary> pros = new HashMap<>();
        for (Prescription p : found.getContent()) {
            pros.computeIfAbsent(p.getProfessionalId(), k -> professionals.find(k).orElse(null));
        }
        List<DetailDto> dtos = found.getContent().stream()
                .map(p -> toDetail(p, byPrescription.getOrDefault(p.getId(), List.of()), pros.get(p.getProfessionalId())))
                .toList();
        return new PageImpl<>(dtos, pageable, found.getTotalElements());
    }

    // ------------------------------------------------------------------ annulation

    /** Seul le professionnel émetteur peut annuler (erreur de saisie, contre-indication…). L'ordonnance reste consultable. */
    public DetailDto cancel(AuthenticatedUser me, UUID id, CancelRequest req) {
        Prescription p = require(id);
        access.resolve(me, p.getRecordId(), AccessAction.CANCEL_PRESCRIPTION, id, false);
        if (!p.getProfessionalId().equals(me.id())) {
            throw ApiException.forbidden("NOT_ISSUER", "Seul le professionnel émetteur peut annuler cette ordonnance.");
        }
        if (p.getStatus() == PrescriptionStatus.CANCELLED) {
            throw ApiException.conflict("PRESCRIPTION_ALREADY_CANCELLED", "Cette ordonnance est déjà annulée.");
        }
        p.cancel(Instant.now(clock), req.reason().trim());
        return toDetail(p, items.findByPrescriptionIdOrderByLineNumber(id), professionals.find(me.id()).orElse(null));
    }

    // ------------------------------------------------------------------ utilitaires

    private Prescription require(UUID id) {
        return prescriptions.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRESCRIPTION_NOT_FOUND", "Ordonnance introuvable."));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private Map<UUID, String> proNames(List<Prescription> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(p -> ids.add(p.getProfessionalId()));
        Map<UUID, String> names = new HashMap<>();
        ids.forEach(i -> names.put(i, professionals.find(i).map(Summary::fullName).orElse("Professionnel supprimé")));
        return names;
    }

    private DetailDto toDetail(Prescription p, List<PrescriptionItem> lines, Summary pro) {
        Instant now = Instant.now(clock);
        boolean intact = PrescriptionHasher.hash(p, lines).equals(p.getIntegrityHash());
        return new DetailDto(p.getId(), p.getNumber(), p.getAppointmentId(), p.getRecordId(), p.getProfessionalId(),
                pro == null ? "Professionnel supprimé" : pro.fullName(), pro == null ? null : pro.qualification(),
                pro == null ? null : pro.licenseNumber(), p.getBeneficiaryName(), p.getIssuedAt(), p.getValidUntil(),
                p.getStatus(), p.isExpired(now), p.getCancelledAt(), p.getCancelReason(), p.getNotes(),
                lines.stream().map(i -> new ItemDto(i.getLineNumber(), i.getMedicationName(), i.getDosage(),
                        i.getFrequency(), i.getDurationDays(), i.getQuantity(), i.getInstructions())).toList(),
                intact);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
