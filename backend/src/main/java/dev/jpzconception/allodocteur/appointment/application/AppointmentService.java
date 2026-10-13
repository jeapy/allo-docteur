package dev.jpzconception.allodocteur.appointment.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.AppointmentDto;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.BookRequest;
import dev.jpzconception.allodocteur.appointment.application.SlotService.Slot;
import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentEvents;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentRepository;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentSpecifications;
import dev.jpzconception.allodocteur.patient.application.PatientService;
import dev.jpzconception.allodocteur.patient.domain.Dependent;
import dev.jpzconception.allodocteur.patient.domain.Patient;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Réservation et cycle de vie des rendez-vous. */
@Service
@Transactional
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private static final Map<String, String> SORTABLE = Map.of(
            "startAt", "startAt",
            "createdAt", "createdAt",
            "status", "status");

    private final AppointmentRepository appointments;
    private final SlotService slotService;
    private final PatientService patients;
    private final UserRepository users;
    private final AppointmentAssembler assembler;
    private final AppointmentProperties props;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final PaymentGate paymentGate;

    public AppointmentService(AppointmentRepository appointments, SlotService slotService, PatientService patients,
                              UserRepository users, AppointmentAssembler assembler, AppointmentProperties props,
                              AuditService audit, ApplicationEventPublisher events, Clock clock, PaymentGate paymentGate) {
        this.appointments = appointments;
        this.slotService = slotService;
        this.patients = patients;
        this.users = users;
        this.assembler = assembler;
        this.props = props;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.paymentGate = paymentGate;
    }

    // ------------------------------------------------------------------ réservation

    public AppointmentDto book(AuthenticatedUser me, BookRequest req) {
        AppointmentType type = req.type();
        if (req.reason().requiresHomeVisit() && type != AppointmentType.HOME_VISIT) {
            throw ApiException.badRequest("REASON_REQUIRES_HOME_VISIT", "Ce motif nécessite une visite à domicile.");
        }
        String address = req.address() == null || req.address().isBlank() ? null : req.address().trim();
        if (type == AppointmentType.HOME_VISIT && address == null) {
            throw ApiException.badRequest("ADDRESS_REQUIRED", "L'adresse est obligatoire pour une visite à domicile.");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw ApiException.badRequest("INVALID_COORDINATES", "Indiquez la latitude et la longitude, ou aucune.");
        }

        Professional pro = slotService.requireBookable(req.professionalId());
        LocalDate day = req.startAt().atZone(slotService.zone()).toLocalDate();
        Slot slot = slotService.compute(pro, day, day, type).stream()
                .filter(s -> s.startAt().equals(req.startAt()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("SLOT_UNAVAILABLE", "Ce créneau n'est plus disponible."));

        Appointment a = new Appointment();
        a.setPatientId(me.id());
        a.setProfessionalId(pro.getId());
        a.setType(type);
        a.setReason(req.reason());
        a.setReasonDetails(req.reasonDetails() == null || req.reasonDetails().isBlank() ? null : req.reasonDetails().trim());
        a.setStartAt(slot.startAt());
        a.setEndAt(slot.endAt());
        a.setStatus(AppointmentStatus.REQUESTED);
        if (type == AppointmentType.HOME_VISIT) {
            a.setAddress(address);
            a.setLatitude(req.latitude());
            a.setLongitude(req.longitude());
        }
        BigDecimal fee = type == AppointmentType.HOME_VISIT ? pro.getHomeVisitFee() : pro.getConsultationFee();
        a.setFee(fee == null ? BigDecimal.ZERO : fee);
        a.setCurrency(pro.getCurrency());

        if (req.dependentId() != null) {
            Dependent d = patients.requireOwnDependent(me.id(), req.dependentId());
            a.setDependentId(d.getId());
            a.setBeneficiaryName(d.fullName());
            a.setBeneficiaryBirthDate(d.getBirthDate());
            if (appointments.existsOverlapForDependent(d.getId(), AppointmentStatus.BLOCKING, slot.startAt(), slot.endAt())) {
                throw ApiException.conflict("PATIENT_BUSY", "Un rendez-vous existe déjà sur ce créneau pour cette personne.");
            }
        } else {
            User user = users.findById(me.id())
                    .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié."));
            Patient profile = patients.findProfile(me.id());
            a.setBeneficiaryName(((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                    + (user.getLastName() == null ? "" : user.getLastName())).trim());
            a.setBeneficiaryBirthDate(profile == null ? null : profile.getBirthDate());
            if (appointments.existsOverlapForSelf(me.id(), AppointmentStatus.BLOCKING, slot.startAt(), slot.endAt())) {
                throw ApiException.conflict("PATIENT_BUSY", "Vous avez déjà un rendez-vous sur ce créneau.");
            }
        }
        if (a.getBeneficiaryName().isBlank()) {
            a.setBeneficiaryName(user(me.id()).getEmail());
        }

        try {
            appointments.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            // Course perdue contre une autre réservation : la contrainte d'exclusion de la base fait foi.
            log.debug("Créneau pris entre-temps pour le professionnel {}", pro.getId());
            throw ApiException.conflict("SLOT_UNAVAILABLE", "Ce créneau n'est plus disponible.");
        }
        events.publishEvent(new AppointmentEvents.Requested(a.getId(), a.getPatientId(), a.getProfessionalId()));
        return assembler.toDto(a, false);
    }

    // ------------------------------------------------------------------ visite urgente (SOS)

    /** Demande de visite immédiate issue d'une alerte SOS : pas de créneau, le professionnel part tout de suite. */
    public record UrgentVisit(UUID patientId, UUID dependentId, String beneficiaryName, LocalDate beneficiaryBirthDate,
                              UUID professionalId, String address, BigDecimal latitude, BigDecimal longitude,
                              String details) {}

    /**
     * Crée un rendez-vous de visite à domicile déjà confirmé, qui démarre maintenant. Seul le chevauchement avec un autre
     * rendez-vous du professionnel est refusé (409 {@code PROFESSIONAL_BUSY}) ; la disponibilité hebdomadaire est ignorée.
     */
    public UUID createUrgentVisit(UrgentVisit req) {
        Professional pro = slotService.requireBookable(req.professionalId());
        Instant now = now();
        Appointment a = new Appointment();
        a.setPatientId(req.patientId());
        a.setDependentId(req.dependentId());
        a.setBeneficiaryName(req.beneficiaryName());
        a.setBeneficiaryBirthDate(req.beneficiaryBirthDate());
        a.setProfessionalId(pro.getId());
        a.setType(AppointmentType.HOME_VISIT);
        a.setReason(dev.jpzconception.allodocteur.appointment.domain.ReasonCategory.CONSULTATION);
        a.setReasonDetails(req.details());
        a.setStartAt(now);
        a.setEndAt(now.plus(slotService.durationMinutes(AppointmentType.HOME_VISIT), ChronoUnit.MINUTES));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setConfirmedAt(now);
        a.setAddress(req.address());
        a.setLatitude(req.latitude());
        a.setLongitude(req.longitude());
        a.setFee(pro.getHomeVisitFee() == null ? BigDecimal.ZERO : pro.getHomeVisitFee());
        a.setCurrency(pro.getCurrency());
        try {
            appointments.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PROFESSIONAL_BUSY", "Ce professionnel a déjà un rendez-vous en cours.");
        }
        events.publishEvent(new AppointmentEvents.Requested(a.getId(), a.getPatientId(), a.getProfessionalId()));
        events.publishEvent(new AppointmentEvents.Confirmed(a.getId(), a.getPatientId(), a.getProfessionalId()));
        return a.getId();
    }

    /** Annulation décidée par le système (alerte SOS annulée) : sans délai minimal. Sans effet si déjà terminé. */
    public void cancelBySystem(UUID appointmentId, UUID by, String reason) {
        appointments.findById(appointmentId)
                .filter(a -> a.getStatus() == AppointmentStatus.REQUESTED || a.getStatus() == AppointmentStatus.CONFIRMED)
                .ifPresent(a -> {
                    a.cancel(by, reason, now());
                    events.publishEvent(new AppointmentEvents.Cancelled(a.getId(), a.getPatientId(), a.getProfessionalId(), by));
                });
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public AppointmentDto get(AuthenticatedUser me, UUID id) {
        Appointment a = appointments.findById(id).orElseThrow(AppointmentService::notFound);
        boolean admin = me.role() == Role.ADMIN;
        if (!admin && !a.isParticipant(me.id())) throw notFound();
        return assembler.toDto(a, admin);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentDto> listMine(AuthenticatedUser me, Collection<AppointmentStatus> statuses, Instant from,
                                         Instant to, int page, int size, String sort) {
        boolean asProfessional = me.role() == Role.PROFESSIONAL;
        var spec = AppointmentSpecifications.filter(asProfessional ? null : me.id(), asProfessional ? me.id() : null,
                statuses, null, from, to);
        return toDtoPage(appointments.findAll(spec, pageable(page, size, sort)), false);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentDto> listAll(UUID patientId, UUID professionalId, Collection<AppointmentStatus> statuses,
                                        AppointmentType type, Instant from, Instant to, int page, int size, String sort) {
        var spec = AppointmentSpecifications.filter(patientId, professionalId, statuses, type, from, to);
        return toDtoPage(appointments.findAll(spec, pageable(page, size, sort)), true);
    }

    // ------------------------------------------------------------------ cycle de vie

    public AppointmentDto confirm(AuthenticatedUser me, UUID id) {
        Appointment a = requireOwnedByProfessional(me, id);
        paymentGate.requirePaid(a.getId(), a.getFee());
        a.confirm(now());
        events.publishEvent(new AppointmentEvents.Confirmed(a.getId(), a.getPatientId(), a.getProfessionalId()));
        return assembler.toDto(a, false);
    }

    public AppointmentDto cancel(AuthenticatedUser me, UUID id, String reason) {
        Appointment a = appointments.findById(id).orElseThrow(AppointmentService::notFound);
        String cleaned = reason == null || reason.isBlank() ? null : reason.trim();
        Instant now = now();
        boolean admin = me.role() == Role.ADMIN;

        if (admin) {
            if (me.adminLevel() != AdminLevel.SUPER && me.adminLevel() != AdminLevel.SUPPORT) {
                throw ApiException.forbidden("ACCESS_DENIED", "Accès refusé.");
            }
        } else if (!a.isParticipant(me.id())) {
            throw notFound();
        }

        boolean byPatient = !admin && a.getPatientId().equals(me.id());
        if (byPatient) {
            Instant limit = a.getStartAt().minus(props.patientCancelMinutes(), ChronoUnit.MINUTES);
            if (a.getStatus() == AppointmentStatus.CONFIRMED && now.isAfter(limit)) {
                throw ApiException.conflict("CANCELLATION_TOO_LATE",
                        "Annulation impossible à moins de " + props.patientCancelMinutes() + " minutes du rendez-vous.");
            }
        } else if (cleaned == null) {
            throw ApiException.badRequest("REASON_REQUIRED", "Un motif d'annulation est obligatoire.");
        }

        a.cancel(me.id(), cleaned, now);
        if (admin) {
            audit.record(me.id(), "APPOINTMENT_CANCELLED", "APPOINTMENT", a.getId().toString(), cleaned);
        }
        events.publishEvent(new AppointmentEvents.Cancelled(a.getId(), a.getPatientId(), a.getProfessionalId(), me.id()));
        return assembler.toDto(a, admin);
    }

    public AppointmentDto start(AuthenticatedUser me, UUID id) {
        Appointment a = requireOwnedByProfessional(me, id);
        Instant now = now();
        if (now.isBefore(a.getStartAt().minus(props.startEarlyMinutes(), ChronoUnit.MINUTES))) {
            throw ApiException.conflict("TOO_EARLY", "Le rendez-vous ne peut pas encore être démarré.");
        }
        if (now.isAfter(a.getEndAt())) {
            throw ApiException.conflict("TOO_LATE", "Le créneau du rendez-vous est terminé.");
        }
        a.start(now);
        events.publishEvent(new AppointmentEvents.Started(a.getId(), a.getPatientId(), a.getProfessionalId(), a.getType()));
        return assembler.toDto(a, false);
    }

    public AppointmentDto complete(AuthenticatedUser me, UUID id) {
        Appointment a = requireOwnedByProfessional(me, id);
        a.complete(now());
        events.publishEvent(new AppointmentEvents.Completed(a.getId(), a.getPatientId(), a.getProfessionalId()));
        return assembler.toDto(a, false);
    }

    public AppointmentDto noShow(AuthenticatedUser me, UUID id) {
        Appointment a = requireOwnedByProfessional(me, id);
        if (now().isBefore(a.getStartAt().plus(props.noShowAfterMinutes(), ChronoUnit.MINUTES))) {
            throw ApiException.conflict("TOO_EARLY", "L'absence ne peut être déclarée qu'après l'heure du rendez-vous.");
        }
        a.markNoShow();
        events.publishEvent(new AppointmentEvents.NoShow(a.getId(), a.getPatientId(), a.getProfessionalId()));
        return assembler.toDto(a, false);
    }

    // ------------------------------------------------------------------ helpers

    private Appointment requireOwnedByProfessional(AuthenticatedUser me, UUID id) {
        return appointments.findById(id)
                .filter(a -> a.getProfessionalId().equals(me.id()))
                .orElseThrow(AppointmentService::notFound);
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié."));
    }

    private Page<AppointmentDto> toDtoPage(Page<Appointment> page, boolean redact) {
        List<AppointmentDto> dtos = assembler.toDtos(page.getContent(), redact);
        return new org.springframework.data.domain.PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static ApiException notFound() {
        return ApiException.notFound("APPOINTMENT_NOT_FOUND", "Rendez-vous introuvable.");
    }

    static Pageable pageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort order = Sort.by(Sort.Direction.DESC, "startAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = SORTABLE.get(parts[0].trim());
            if (property == null) {
                throw ApiException.badRequest("INVALID_SORT", "Tri non supporté : " + parts[0]);
            }
            Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            order = Sort.by(dir, property);
        }
        // départage stable pour que la pagination ne saute ni ne répète de lignes
        return PageRequest.of(safePage, safeSize, order.and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
