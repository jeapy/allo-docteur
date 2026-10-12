package dev.jpzconception.allodocteur.appointment.application;

import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.AppointmentDto;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.PersonRef;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.ProfessionalRef;
import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Convertit des rendez-vous en DTO en chargeant les noms en lot (2 requêtes pour toute une page). */
@Component
public class AppointmentAssembler {

    private final UserRepository users;
    private final ProfessionalRepository professionals;

    public AppointmentAssembler(UserRepository users, ProfessionalRepository professionals) {
        this.users = users;
        this.professionals = professionals;
    }

    /** À appeler dans une transaction (les associations du professionnel sont chargées paresseusement). */
    public List<AppointmentDto> toDtos(List<Appointment> list, boolean redactHealthData) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> professionalIds = new HashSet<>();
        for (Appointment a : list) {
            userIds.add(a.getPatientId());
            userIds.add(a.getProfessionalId());
            professionalIds.add(a.getProfessionalId());
        }
        Map<UUID, User> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Professional> professionalById = professionals.findAllById(professionalIds).stream()
                .collect(Collectors.toMap(Professional::getId, Function.identity()));
        return list.stream().map(a -> toDto(a, userById, professionalById, redactHealthData)).toList();
    }

    public AppointmentDto toDto(Appointment a, boolean redactHealthData) {
        return toDtos(List.of(a), redactHealthData).get(0);
    }

    private static AppointmentDto toDto(Appointment a, Map<UUID, User> users, Map<UUID, Professional> professionals,
                                        boolean redact) {
        User patient = users.get(a.getPatientId());
        User proUser = users.get(a.getProfessionalId());
        Professional pro = professionals.get(a.getProfessionalId());
        return new AppointmentDto(
                a.getId(), a.getStatus(), a.getType(), a.getReason(),
                redact ? null : a.getReasonDetails(),
                a.getStartAt(), a.getEndAt(), a.getFee(), a.getCurrency(),
                a.getAddress(), a.getLatitude(), a.getLongitude(),
                new PersonRef(a.getPatientId(), name(patient)),
                a.getDependentId(), a.getBeneficiaryName(),
                redact ? null : a.getBeneficiaryBirthDate(),
                new ProfessionalRef(a.getProfessionalId(), name(proUser),
                        pro == null ? null : pro.getQualification(),
                        pro == null ? null : pro.getType().getLabelFr()),
                a.getConfirmedAt(), a.getStartedAt(), a.getCompletedAt(), a.getCancelledAt(),
                a.getCancelledBy(), a.getCancelReason(), a.getCreatedAt());
    }

    private static String name(User u) {
        if (u == null) return "Compte supprimé";
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }
}
