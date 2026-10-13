package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.AccessLogDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.EntryDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.EntryRequest;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.RecordDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.SummaryRequest;
import dev.jpzconception.allodocteur.medicalrecord.application.RecordAccessService.Context;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.medicalrecord.domain.EntryKind;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalAccessLog;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecord;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecordEntry;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalAccessLogRepository;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalAccessLogSpecifications;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalRecordEntryRepository;
import dev.jpzconception.allodocteur.patient.application.PatientService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dossier médical et carnet de santé. Toute lecture/écriture passe par {@link RecordAccessService}. */
@Service
@Transactional
public class MedicalRecordService {

    private final MedicalRecordProvisioner provisioner;
    private final RecordAccessService access;
    private final MedicalRecordEntryRepository entries;
    private final MedicalAccessLogRepository accessLogs;
    private final AppointmentAccess appointments;
    private final PatientService patients;
    private final UserRepository users;
    private final Clock clock;

    public MedicalRecordService(MedicalRecordProvisioner provisioner, RecordAccessService access,
                                MedicalRecordEntryRepository entries, MedicalAccessLogRepository accessLogs,
                                AppointmentAccess appointments, PatientService patients, UserRepository users,
                                Clock clock) {
        this.provisioner = provisioner;
        this.access = access;
        this.entries = entries;
        this.accessLogs = accessLogs;
        this.appointments = appointments;
        this.patients = patients;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ accès au dossier

    /** Mon propre dossier (créé à la première demande). */
    public RecordDto own(AuthenticatedUser me) {
        UUID id = provisioner.ensure(me.id(), null);
        return toDto(access.resolve(me, id, AccessAction.VIEW_RECORD, null, false).record());
    }

    /** Dossier d'une de mes personnes à charge. */
    public RecordDto forDependent(AuthenticatedUser me, UUID dependentId) {
        patients.requireOwnDependent(me.id(), dependentId); // 404 si elle n'est pas à moi
        UUID id = provisioner.ensure(me.id(), dependentId);
        return toDto(access.resolve(me, id, AccessAction.VIEW_RECORD, null, false).record());
    }

    /** Dossier de la personne concernée par un rendez-vous, pour le professionnel de ce rendez-vous. */
    public RecordDto forAppointment(AuthenticatedUser me, UUID appointmentId) {
        Ref ref = appointments.requireParticipant(me, appointmentId);
        UUID id = provisioner.ensure(ref.patientId(), ref.dependentId());
        return toDto(access.resolve(me, id, AccessAction.VIEW_RECORD, appointmentId, false).record());
    }

    public RecordDto get(AuthenticatedUser me, UUID recordId) {
        return toDto(access.resolve(me, recordId, AccessAction.VIEW_RECORD, null, false).record());
    }

    /** Fiche de synthèse (groupe sanguin, allergies…) : réservée au propriétaire. */
    public RecordDto updateSummary(AuthenticatedUser me, UUID recordId, SummaryRequest req) {
        MedicalRecord r = access.resolve(me, recordId, AccessAction.UPDATE_SUMMARY, null, true).record();
        r.setBloodGroup(req.bloodGroup());
        r.setAllergies(blankToNull(req.allergies()));
        r.setChronicConditions(blankToNull(req.chronicConditions()));
        r.setCurrentMedications(blankToNull(req.currentMedications()));
        r.setFamilyHistory(blankToNull(req.familyHistory()));
        return toDto(r);
    }

    // ------------------------------------------------------------------ entrées / carnet de santé

    public Page<EntryDto> listEntries(AuthenticatedUser me, UUID recordId, EntryKind kind, int page, int size) {
        Context ctx = access.resolve(me, recordId, AccessAction.VIEW_ENTRIES, null, false);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredOn").and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.ASC, "id")));
        Page<MedicalRecordEntry> found = kind == null
                ? entries.findByRecordId(ctx.record().getId(), pageable)
                : entries.findByRecordIdAndKind(ctx.record().getId(), kind, pageable);
        Map<UUID, User> authors = users.findAllById(
                found.getContent().stream().map(MedicalRecordEntry::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<EntryDto> dtos = found.getContent().stream().map(e -> toDto(e, authors.get(e.getAuthorId()), me)).toList();
        return new PageImpl<>(dtos, pageable, found.getTotalElements());
    }

    public EntryDto addEntry(AuthenticatedUser me, UUID recordId, EntryRequest req) {
        boolean allowed = me.role() == Role.PATIENT ? req.kind().allowedForPatient() : req.kind().allowedForProfessional();
        if (!allowed) {
            throw ApiException.forbidden("ENTRY_KIND_NOT_ALLOWED", "Ce type d'entrée n'est pas autorisé pour votre rôle.");
        }
        Context ctx = access.resolve(me, recordId, AccessAction.CREATE_ENTRY, null, false);
        MedicalRecord record = ctx.record();
        if (req.appointmentId() != null) {
            Ref ref = appointments.requireParticipant(me, req.appointmentId());
            if (!ref.patientId().equals(record.getAccountId()) || !Objects.equals(ref.dependentId(), record.getDependentId())) {
                throw ApiException.badRequest("APPOINTMENT_MISMATCH", "Ce rendez-vous ne concerne pas cette personne.");
            }
        }
        Instant now = Instant.now(clock);
        MedicalRecordEntry e = new MedicalRecordEntry();
        e.setRecordId(record.getId());
        e.setAppointmentId(req.appointmentId());
        e.setAuthorId(me.id());
        e.setAuthorRole(me.role());
        e.setKind(req.kind());
        e.setTitle(req.title().trim());
        e.setContent(blankToNull(req.content()));
        e.setOccurredOn(req.occurredOn() == null ? LocalDate.ofInstant(now, ZoneOffset.UTC) : req.occurredOn());
        e.setCreatedAt(now);
        entries.save(e);
        return toDto(e, users.findById(me.id()).orElse(null), me);
    }

    /** Seul le patient auteur peut retirer sa propre entrée ; les notes cliniques sont conservées. */
    public void deleteEntry(AuthenticatedUser me, UUID recordId, UUID entryId) {
        access.resolve(me, recordId, AccessAction.DELETE_ENTRY, entryId, false);
        MedicalRecordEntry e = entries.findByIdAndRecordId(entryId, recordId)
                .orElseThrow(() -> ApiException.notFound("ENTRY_NOT_FOUND", "Entrée introuvable."));
        if (me.role() != Role.PATIENT || !e.getAuthorId().equals(me.id())) {
            throw ApiException.forbidden("ENTRY_IMMUTABLE", "Cette entrée ne peut pas être supprimée.");
        }
        e.setDeletedAt(Instant.now(clock));
    }

    // ------------------------------------------------------------------ journal d'accès

    /** Le propriétaire du dossier consulte qui y a accédé. */
    public Page<AccessLogDto> accessLog(AuthenticatedUser me, UUID recordId, int page, int size) {
        access.resolve(me, recordId, AccessAction.VIEW_ACCESS_LOG, null, true);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<MedicalAccessLog> found = accessLogs.findAll(
                MedicalAccessLogSpecifications.filter(recordId, null, null, null, null), pageable);
        return new PageImpl<>(toAccessDtos(found.getContent(), false), pageable, found.getTotalElements());
    }

    /** Vue d'administration (SUPER) : tous les dossiers, avec l'adresse IP. */
    @Transactional(readOnly = true)
    public Page<AccessLogDto> searchAccessLogs(MedicalAccessLogQuery q, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<MedicalAccessLog> found = accessLogs.findAll(
                MedicalAccessLogSpecifications.filter(q.recordId(), q.actorId(), q.outcome(), q.from(), q.to()), pageable);
        return new PageImpl<>(toAccessDtos(found.getContent(), true), pageable, found.getTotalElements());
    }

    // ------------------------------------------------------------------ conversion

    private List<AccessLogDto> toAccessDtos(List<MedicalAccessLog> logs, boolean withIp) {
        Set<UUID> ids = new HashSet<>();
        logs.forEach(l -> ids.add(l.getActorId()));
        Map<UUID, User> actors = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return logs.stream().map(l -> new AccessLogDto(l.getId(), l.getRecordId(), l.getActorId(),
                name(actors.get(l.getActorId())), l.getActorRole(), l.getAction(), l.getTargetId(), l.getOutcome(),
                l.getReason(), withIp ? l.getIpAddress() : null, l.getOccurredAt())).toList();
    }

    private RecordDto toDto(MedicalRecord r) {
        return new RecordDto(r.getId(), r.getAccountId(), r.getDependentId(),
                patients.beneficiaryName(r.getAccountId(), r.getDependentId()), r.getBloodGroup(), r.getAllergies(),
                r.getChronicConditions(), r.getCurrentMedications(), r.getFamilyHistory(), r.getUpdatedAt());
    }

    private static EntryDto toDto(MedicalRecordEntry e, User author, AuthenticatedUser viewer) {
        boolean removable = viewer.role() == Role.PATIENT && e.getAuthorId().equals(viewer.id());
        return new EntryDto(e.getId(), e.getRecordId(), e.getAppointmentId(), e.getAuthorId(), name(author),
                e.getAuthorRole(), e.getKind(), e.getTitle(), e.getContent(), e.getOccurredOn(), e.getCreatedAt(), removable);
    }

    private static String name(User u) {
        if (u == null) return "Compte supprimé";
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
