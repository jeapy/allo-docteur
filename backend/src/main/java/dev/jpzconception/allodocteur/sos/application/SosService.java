package dev.jpzconception.allodocteur.sos.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.appointment.application.AppointmentService;
import dev.jpzconception.allodocteur.appointment.application.AppointmentService.UrgentVisit;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalAccessLogger;
import dev.jpzconception.allodocteur.medicalrecord.application.MedicalRecordProvisioner;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.patient.application.PatientService;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup;
import dev.jpzconception.allodocteur.professional.application.ProfessionalLookup.Dispatcher;
import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.infrastructure.CityRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.geo.GeolocationProvider;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.sos.api.SosDtos.AdminAlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.AlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.CreateRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.EmergencyAccessDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.OpenAlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.ProfessionalRef;
import dev.jpzconception.allodocteur.sos.api.SosDtos.SummaryDto;
import dev.jpzconception.allodocteur.sos.domain.EmergencyAccessGrant;
import dev.jpzconception.allodocteur.sos.domain.SosAlert;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import dev.jpzconception.allodocteur.sos.infrastructure.EmergencyAccessGrantRepository;
import dev.jpzconception.allodocteur.sos.infrastructure.SosAlertRepository;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alertes SOS : déclenchement par le patient, prise en charge par un professionnel, suivi par l'administration. */
@Service
@Transactional
public class SosService {

    private final SosAlertRepository alerts;
    private final EmergencyAccessGrantRepository grants;
    private final FacilityService facilities;
    private final AppointmentService appointments;
    private final PatientService patients;
    private final ProfessionalLookup professionals;
    private final MedicalRecordProvisioner provisioner;
    private final MedicalAccessLogger accessLogger;
    private final CityRepository cities;
    private final UserRepository users;
    private final GeolocationProvider geo;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final SosProperties props;
    private final Clock clock;

    public SosService(SosAlertRepository alerts, EmergencyAccessGrantRepository grants, FacilityService facilities,
                      AppointmentService appointments, PatientService patients, ProfessionalLookup professionals,
                      MedicalRecordProvisioner provisioner, MedicalAccessLogger accessLogger, CityRepository cities,
                      UserRepository users, GeolocationProvider geo, AuditService audit,
                      ApplicationEventPublisher events, SosProperties props, Clock clock) {
        this.alerts = alerts;
        this.grants = grants;
        this.facilities = facilities;
        this.appointments = appointments;
        this.patients = patients;
        this.professionals = professionals;
        this.provisioner = provisioner;
        this.accessLogger = accessLogger;
        this.cities = cities;
        this.users = users;
        this.geo = geo;
        this.audit = audit;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ patient

    public AlertDto create(AuthenticatedUser me, CreateRequest req) {
        if (req.dependentId() != null) patients.requireOwnDependent(me.id(), req.dependentId());
        Integer cityId = req.cityId() != null ? req.cityId() : patients.cityIdOf(me.id());
        if (cityId != null && !cities.existsById(cityId)) {
            throw ApiException.badRequest("CITY_NOT_FOUND", "Ville inconnue.");
        }
        User user = users.findById(me.id())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié."));

        SosAlert a = new SosAlert();
        a.setAccountId(me.id());
        a.setDependentId(req.dependentId());
        a.setBeneficiaryName(patients.beneficiaryName(me.id(), req.dependentId()));
        a.setBeneficiaryBirthDate(patients.birthDateOf(me.id(), req.dependentId()));
        a.setCategory(req.category());
        a.setLatitude(req.latitude());
        a.setLongitude(req.longitude());
        a.setAddress(addressFor(req));
        a.setCityId(cityId);
        a.setContactPhone(blankToNull(req.contactPhone()) != null ? req.contactPhone().trim() : user.getPhone());
        a.setDescription(blankToNull(req.description()));
        try {
            alerts.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("SOS_ALREADY_ACTIVE", "Une alerte est déjà en cours pour cette personne.");
        }
        events.publishEvent(new SosEvents.Created(a.getId(), a.getAccountId(), a.getCityId(), a.getCategory()));
        return toDto(a, true);
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> mine(AuthenticatedUser me, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.Direction.DESC);
        Page<SosAlert> found = alerts.findByAccountId(me.id(), pageable);
        return new PageImpl<>(found.getContent().stream().map(a -> toDto(a, a.isActive())).toList(), pageable,
                found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AlertDto get(AuthenticatedUser me, UUID id) {
        SosAlert a = require(id);
        boolean owner = a.getAccountId().equals(me.id());
        boolean assigned = me.id().equals(a.getAssignedProfessionalId());
        if (!owner && !assigned) throw notFound();
        return toDto(a, owner && a.isActive());
    }

    public AlertDto cancelByPatient(AuthenticatedUser me, UUID id, String reason) {
        SosAlert a = require(id);
        if (!a.getAccountId().equals(me.id())) throw notFound();
        cancel(a, me.id(), blankToNull(reason));
        events.publishEvent(new SosEvents.Cancelled(a.getId(), a.getAccountId(), me.id()));
        return toDto(a, false);
    }

    // ------------------------------------------------------------------ professionnel

    @Transactional(readOnly = true)
    public Page<OpenAlertDto> open(AuthenticatedUser me, int page, int size) {
        Dispatcher pro = requireDispatcher(me);
        Pageable pageable = pageable(page, size, Sort.Direction.ASC); // la plus ancienne d'abord
        Page<SosAlert> found = alerts.findOpenFor(SosStatus.OPEN, pro.cityId(), pageable);
        Instant now = Instant.now(clock);
        Map<Integer, String> cityNames = cityNames(found.getContent().stream().map(SosAlert::getCityId).toList());
        List<OpenAlertDto> dtos = found.getContent().stream().map(a -> new OpenAlertDto(a.getId(), a.getCategory(),
                a.getCityId(), a.getCityId() == null ? null : cityNames.get(a.getCityId()), a.getCreatedAt(),
                Duration.between(a.getCreatedAt(), now).toMinutes())).toList();
        return new PageImpl<>(dtos, pageable, found.getTotalElements());
    }

    /** Le premier professionnel disponible l'emporte ; une visite à domicile immédiate est créée. */
    public AlertDto accept(AuthenticatedUser me, UUID id) {
        Dispatcher pro = requireDispatcher(me);
        SosAlert candidate = require(id);
        if (candidate.getCityId() != null && !candidate.getCityId().equals(pro.cityId())) throw notFound();

        Instant now = Instant.now(clock);
        if (alerts.claim(id, me.id(), now) == 0) {
            throw ApiException.conflict(candidate.getStatus() == SosStatus.OPEN || candidate.getStatus() == SosStatus.ACCEPTED
                    ? "SOS_ALREADY_TAKEN" : "SOS_NOT_OPEN", "Cette alerte n'est plus disponible.");
        }
        SosAlert a = require(id); // rechargée : la prise en charge a été écrite par une requête native
        UUID appointmentId = appointments.createUrgentVisit(new UrgentVisit(a.getAccountId(), a.getDependentId(),
                a.getBeneficiaryName(), a.getBeneficiaryBirthDate(), me.id(), a.getAddress(), a.getLatitude(),
                a.getLongitude(), a.getDescription()));
        a.setAppointmentId(appointmentId);
        events.publishEvent(new SosEvents.Accepted(a.getId(), a.getAccountId(), me.id(), appointmentId));
        return toDto(a, false);
    }

    /**
     * « Bris de glace » : le professionnel assigné déclare un motif pour consulter le dossier sans consentement du
     * patient pendant l'urgence. Journalisé dans le journal d'accès (visible du patient) et dans l'audit.
     */
    public EmergencyAccessDto emergencyAccess(AuthenticatedUser me, UUID id, String reason) {
        SosAlert a = requireAssigned(me, id);
        if (a.getStatus() != SosStatus.ACCEPTED) {
            throw ApiException.conflict("SOS_INVALID_STATE", "L'alerte n'est plus en cours.");
        }
        Instant now = Instant.now(clock);
        EmergencyAccessGrant g = grants.findByAlertIdAndProfessionalId(id, me.id()).orElse(null);
        if (g == null) {
            g = new EmergencyAccessGrant();
            g.setAlertId(id);
            g.setProfessionalId(me.id());
            g.setReason(reason.trim());
            g.setGrantedAt(now);
            g.setExpiresAt(now.plus(Duration.ofHours(props.emergencyAccessHours())));
            grants.save(g);
            UUID recordId = provisioner.ensure(a.getAccountId(), a.getDependentId());
            accessLogger.granted(me, recordId, AccessAction.EMERGENCY_ACCESS, id, "EMERGENCY_ACCESS_GRANTED");
            audit.record(me.id(), "SOS_EMERGENCY_ACCESS", "SOS_ALERT", id.toString(), "accès d'urgence au dossier");
        }
        return new EmergencyAccessDto(id, g.getGrantedAt(), g.getExpiresAt());
    }

    public AlertDto resolve(AuthenticatedUser me, UUID id, String note) {
        SosAlert a = requireAssigned(me, id);
        a.resolve(Instant.now(clock), blankToNull(note));
        events.publishEvent(new SosEvents.Resolved(a.getId(), a.getAccountId(), me.id()));
        return toDto(a, false);
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public Page<AdminAlertDto> adminList(Collection<SosStatus> statuses, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.Direction.DESC);
        Collection<SosStatus> filter = statuses == null || statuses.isEmpty() ? EnumSet.allOf(SosStatus.class) : statuses;
        Page<SosAlert> found = alerts.findByStatusIn(filter, pageable);
        Instant now = Instant.now(clock);

        Map<UUID, User> people = users.findAllById(found.getContent().stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getAccountId(), a.getAssignedProfessionalId()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Integer, String> cityNames = cityNames(found.getContent().stream().map(SosAlert::getCityId).toList());

        List<AdminAlertDto> dtos = found.getContent().stream().map(a -> {
            Instant until = a.getAcceptedAt() != null ? a.getAcceptedAt() : (a.isActive() ? now : a.getCreatedAt());
            return new AdminAlertDto(a.getId(), a.getStatus(), a.getCategory(), a.getAccountId(),
                    name(people.get(a.getAccountId())), a.getCityId(),
                    a.getCityId() == null ? null : cityNames.get(a.getCityId()),
                    a.getAssignedProfessionalId() == null ? null : new ProfessionalRef(a.getAssignedProfessionalId(),
                            name(people.get(a.getAssignedProfessionalId())), null),
                    a.getAppointmentId(), a.getCreatedAt(), a.getAcceptedAt(), a.getResolvedAt(), a.getCancelReason(),
                    Duration.between(a.getCreatedAt(), until).toMinutes());
        }).toList();
        return new PageImpl<>(dtos, pageable, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SummaryDto summary() {
        return new SummaryDto(alerts.countByStatus(SosStatus.OPEN), alerts.countByStatus(SosStatus.ACCEPTED));
    }

    /** Clôture administrative d'une alerte abandonnée ou erronée (SUPER, SUPPORT). */
    public void adminCancel(AuthenticatedUser admin, UUID id, String reason) {
        SosAlert a = require(id);
        cancel(a, admin.id(), reason.trim());
        audit.record(admin.id(), "SOS_CANCELLED_BY_ADMIN", "SOS_ALERT", id.toString(), reason.trim());
        events.publishEvent(new SosEvents.Cancelled(a.getId(), a.getAccountId(), admin.id()));
    }

    // ------------------------------------------------------------------ utilitaires

    private void cancel(SosAlert a, UUID by, String reason) {
        a.cancel(by, reason, Instant.now(clock));
        if (a.getAppointmentId() != null) {
            appointments.cancelBySystem(a.getAppointmentId(), by, "Alerte SOS annulée");
        }
    }

    private SosAlert require(UUID id) {
        return alerts.findById(id).orElseThrow(SosService::notFound);
    }

    private SosAlert requireAssigned(AuthenticatedUser me, UUID id) {
        SosAlert a = require(id);
        if (!me.id().equals(a.getAssignedProfessionalId())) throw notFound();
        return a;
    }

    private Dispatcher requireDispatcher(AuthenticatedUser me) {
        return professionals.dispatcher(me.id()).orElseThrow(() -> ApiException.forbidden("SOS_NOT_AVAILABLE",
                "Seuls les professionnels approuvés proposant la visite à domicile peuvent répondre aux alertes."));
    }

    private String addressFor(CreateRequest req) {
        String given = blankToNull(req.address());
        if (given != null) return given;
        return geo.reverseGeocode(req.latitude(), req.longitude())
                .orElse("Position GPS (" + req.latitude().toPlainString() + ", " + req.longitude().toPlainString() + ")");
    }

    private AlertDto toDto(SosAlert a, boolean withFacilities) {
        ProfessionalRef pro = null;
        if (a.getAssignedProfessionalId() != null) {
            pro = professionals.dispatcher(a.getAssignedProfessionalId())
                    .map(d -> new ProfessionalRef(d.id(), d.fullName(), d.phone()))
                    .orElseGet(() -> new ProfessionalRef(a.getAssignedProfessionalId(),
                            name(users.findById(a.getAssignedProfessionalId()).orElse(null)), null));
        }
        Instant until = a.getAssignedProfessionalId() == null ? null
                : grants.findByAlertIdAndProfessionalId(a.getId(), a.getAssignedProfessionalId())
                        .map(EmergencyAccessGrant::getExpiresAt).orElse(null);
        List<dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityDto> near = withFacilities
                ? facilities.nearest(a.getLatitude(), a.getLongitude(), props.nearestFacilities()) : List.of();
        return new AlertDto(a.getId(), a.getStatus(), a.getCategory(), a.getBeneficiaryName(), a.getBeneficiaryBirthDate(),
                a.getLatitude(), a.getLongitude(), a.getAddress(), a.getCityId(), a.getContactPhone(), a.getDescription(),
                pro, a.getAppointmentId(), a.getCreatedAt(), a.getAcceptedAt(), a.getResolvedAt(), a.getResolutionNote(),
                a.getCancelReason(), until, near);
    }

    private Map<Integer, String> cityNames(Collection<Integer> ids) {
        var set = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return cities.findAllById(set).stream().collect(Collectors.toMap(City::getId, City::getName));
    }

    private static Pageable pageable(int page, int size, Sort.Direction direction) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(direction, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
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

    private static ApiException notFound() {
        return ApiException.notFound("SOS_NOT_FOUND", "Alerte introuvable.");
    }
}
