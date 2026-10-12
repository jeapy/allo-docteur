package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ExceptionDto;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ExceptionRequest;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.RuleDto;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.RuleRequest;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityException;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityExceptionRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityRuleRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AvailabilityService {

    public record AvailabilityView(List<RuleDto> rules, List<ExceptionDto> exceptions) {}

    private final ProfessionalRepository professionals;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final ProfessionalAssembler assembler;
    private final Clock clock;

    public AvailabilityService(ProfessionalRepository professionals, AvailabilityRuleRepository rules,
                               AvailabilityExceptionRepository exceptions, ProfessionalAssembler assembler, Clock clock) {
        this.professionals = professionals;
        this.rules = rules;
        this.exceptions = exceptions;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityView view(UUID professionalId) {
        ensureProfile(professionalId);
        return buildView(professionalId);
    }

    /** Remplace l'ensemble des plages hebdomadaires. */
    public AvailabilityView replaceRules(UUID professionalId, List<RuleRequest> requested) {
        ensureProfile(professionalId);
        validateRules(requested);
        rules.deleteAllFor(professionalId);
        for (RuleRequest r : requested) {
            AvailabilityRule rule = new AvailabilityRule();
            rule.setProfessionalId(professionalId);
            rule.setDayOfWeek(r.dayOfWeek());
            rule.setStartTime(r.startTime());
            rule.setEndTime(r.endTime());
            rule.setTeleconsultation(r.teleconsultation());
            rule.setHomeVisit(r.homeVisit());
            rules.save(rule);
        }
        return buildView(professionalId);
    }

    public ExceptionDto addException(UUID professionalId, ExceptionRequest req) {
        ensureProfile(professionalId);
        if (req.date().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("PAST_DATE", "La date ne peut pas être dans le passé.");
        }
        boolean none = req.startTime() == null && req.endTime() == null;
        boolean both = req.startTime() != null && req.endTime() != null;
        if (!none && !both) {
            throw ApiException.badRequest("INVALID_TIME_RANGE", "Indiquez les deux heures, ou aucune pour la journée entière.");
        }
        if (both && !req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("INVALID_TIME_RANGE", "L'heure de fin doit suivre l'heure de début.");
        }
        AvailabilityException e = new AvailabilityException();
        e.setProfessionalId(professionalId);
        e.setDate(req.date());
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setReason(req.reason());
        exceptions.save(e);
        return toDto(e);
    }

    public void deleteException(UUID professionalId, UUID exceptionId) {
        AvailabilityException e = exceptions.findById(exceptionId)
                .filter(x -> x.getProfessionalId().equals(professionalId))
                .orElseThrow(() -> ApiException.notFound("EXCEPTION_NOT_FOUND", "Indisponibilité introuvable."));
        exceptions.delete(e);
    }

    /** Public pour être testé unitairement : plages valides, sans chevauchement sur un même jour. */
    public static void validateRules(List<RuleRequest> requested) {
        for (RuleRequest r : requested) {
            if (!r.endTime().isAfter(r.startTime())) {
                throw ApiException.badRequest("INVALID_TIME_RANGE", "L'heure de fin doit suivre l'heure de début.");
            }
            if (!r.teleconsultation() && !r.homeVisit()) {
                throw ApiException.badRequest("NO_MODE", "Chaque plage doit autoriser la téléconsultation ou la visite à domicile.");
            }
        }
        List<RuleRequest> sorted = requested.stream()
                .sorted(Comparator.comparingInt(RuleRequest::dayOfWeek).thenComparing(RuleRequest::startTime))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            RuleRequest prev = sorted.get(i - 1);
            RuleRequest cur = sorted.get(i);
            if (prev.dayOfWeek() == cur.dayOfWeek() && cur.startTime().isBefore(prev.endTime())) {
                throw ApiException.badRequest("OVERLAPPING_RULES", "Des plages horaires se chevauchent le même jour.");
            }
        }
    }

    private AvailabilityView buildView(UUID professionalId) {
        List<RuleDto> ruleDtos = rules.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(professionalId)
                .stream().map(assembler::rule).toList();
        List<ExceptionDto> excDtos = exceptions
                .findByProfessionalIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(professionalId, LocalDate.now(clock))
                .stream().map(this::toDto).toList();
        return new AvailabilityView(ruleDtos, excDtos);
    }

    private ExceptionDto toDto(AvailabilityException e) {
        return new ExceptionDto(e.getId(), e.getDate(), e.getStartTime(), e.getEndTime(), e.getReason());
    }

    private void ensureProfile(UUID professionalId) {
        if (!professionals.existsById(professionalId)) {
            throw ApiException.notFound("PROFILE_REQUIRED", "Créez d'abord votre profil professionnel.");
        }
    }
}
