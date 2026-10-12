package dev.jpzconception.allodocteur.appointment.application;

import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentRepository;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityException;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityExceptionRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityRuleRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calcule les créneaux réservables d'un professionnel à partir de ses plages, absences et rendez-vous. */
@Service
@Transactional(readOnly = true)
public class SlotService {

    static final int MAX_RANGE_DAYS = 14;

    public record Slot(Instant startAt, Instant endAt) {}

    private final ProfessionalRepository professionals;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final AppointmentRepository appointments;
    private final AppointmentProperties props;
    private final Clock clock;

    public SlotService(ProfessionalRepository professionals, AvailabilityRuleRepository rules,
                       AvailabilityExceptionRepository exceptions, AppointmentRepository appointments,
                       AppointmentProperties props, Clock clock) {
        this.professionals = professionals;
        this.rules = rules;
        this.exceptions = exceptions;
        this.appointments = appointments;
        this.props = props;
        this.clock = clock;
    }

    /** Un professionnel est réservable s'il est approuvé et que son compte est actif. */
    public Professional requireBookable(UUID professionalId) {
        return professionals.findById(professionalId)
                .filter(p -> p.isVisibleInDirectory() && p.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("PROFESSIONAL_NOT_FOUND", "Professionnel introuvable."));
    }

    public List<Slot> slots(UUID professionalId, LocalDate from, LocalDate to, AppointmentType type) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "La date de fin précède la date de début.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "Période limitée à " + MAX_RANGE_DAYS + " jours.");
        }
        return compute(requireBookable(professionalId), from, to, type);
    }

    public int durationMinutes(AppointmentType type) {
        return type.isRemote() ? props.teleconsultationMinutes() : props.homeVisitMinutes();
    }

    public ZoneId zone() {
        return ZoneId.of(props.zone());
    }

    List<Slot> compute(Professional p, LocalDate from, LocalDate to, AppointmentType type) {
        boolean remote = type.isRemote();
        if (remote ? !p.isOffersTeleconsultation() : !p.isOffersHomeVisit()) {
            throw ApiException.badRequest("MODE_NOT_OFFERED", "Ce professionnel ne propose pas ce mode de consultation.");
        }
        ZoneId zone = zone();
        int duration = durationMinutes(type);
        Instant now = clock.instant();
        Instant earliest = now.plus(props.minNoticeMinutes(), ChronoUnit.MINUTES);
        Instant latest = now.plus(props.horizonDays(), ChronoUnit.DAYS);

        List<AvailabilityRule> modeRules = rules.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(p.getId()).stream()
                .filter(r -> remote ? r.isTeleconsultation() : r.isHomeVisit())
                .toList();
        List<AvailabilityException> absences = exceptions.findByProfessionalIdAndDateBetween(p.getId(), from, to);
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> busy = appointments.findOverlapping(p.getId(), AppointmentStatus.BLOCKING, rangeStart, rangeEnd);

        List<Slot> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            final LocalDate d = day;
            int dow = d.getDayOfWeek().getValue();
            List<AvailabilityException> dayAbsences = absences.stream().filter(e -> e.getDate().equals(d)).toList();
            if (dayAbsences.stream().anyMatch(AvailabilityException::isFullDay)) continue;

            for (AvailabilityRule rule : modeRules) {
                if (rule.getDayOfWeek() != dow) continue;
                int endMinute = rule.getEndTime().toSecondOfDay() / 60;
                for (int m = rule.getStartTime().toSecondOfDay() / 60; m + duration <= endMinute; m += duration) {
                    LocalTime localStart = LocalTime.of(m / 60, m % 60);
                    LocalTime localEnd = localStart.plusMinutes(duration);
                    Instant start = d.atTime(localStart).atZone(zone).toInstant();
                    Instant end = start.plus(duration, ChronoUnit.MINUTES);
                    if (start.isBefore(earliest) || start.isAfter(latest)) continue;
                    if (overlapsAbsence(dayAbsences, localStart, m + duration)) continue;
                    if (busy.stream().anyMatch(a -> a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start))) continue;
                    result.add(new Slot(start, end));
                }
            }
        }
        result.sort((a, b) -> a.startAt().compareTo(b.startAt()));
        return result;
    }

    private static boolean overlapsAbsence(List<AvailabilityException> absences, LocalTime slotStart, int slotEndMinute) {
        int slotStartMinute = slotStart.toSecondOfDay() / 60;
        for (AvailabilityException e : absences) {
            int from = e.getStartTime().toSecondOfDay() / 60;
            int to = e.getEndTime().toSecondOfDay() / 60;
            if (from < slotEndMinute && to > slotStartMinute) return true;
        }
        return false;
    }
}
