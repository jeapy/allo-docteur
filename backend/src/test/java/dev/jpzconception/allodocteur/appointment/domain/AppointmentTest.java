package dev.jpzconception.allodocteur.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentTest {

    static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");

    private Appointment requested() {
        Appointment a = new Appointment();
        a.setPatientId(UUID.randomUUID());
        a.setProfessionalId(UUID.randomUUID());
        return a;
    }

    @Test
    void happyPath() {
        Appointment a = requested();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
        a.confirm(NOW);
        a.start(NOW);
        a.complete(NOW);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(a.getConfirmedAt()).isEqualTo(NOW);
        assertThat(a.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotStartBeforeConfirmationOrCompleteBeforeStart() {
        Appointment a = requested();
        assertThatThrownBy(() -> a.start(NOW)).isInstanceOf(ApiException.class);
        a.confirm(NOW);
        assertThatThrownBy(() -> a.complete(NOW)).isInstanceOf(ApiException.class);
    }

    @Test
    void cancellationOnlyBeforeTheConsultationStarts() {
        UUID by = UUID.randomUUID();
        Appointment a = requested();
        a.cancel(by, "Empêchement", NOW);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancelledBy()).isEqualTo(by);

        Appointment started = requested();
        started.confirm(NOW);
        started.start(NOW);
        assertThatThrownBy(() -> started.cancel(by, "x", NOW))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_STATE");
    }

    @Test
    void noShowOnlyFromConfirmed() {
        Appointment a = requested();
        assertThatThrownBy(a::markNoShow).isInstanceOf(ApiException.class);
        a.confirm(NOW);
        a.markNoShow();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void participantsAreThePatientAccountAndTheProfessional() {
        Appointment a = requested();
        assertThat(a.isParticipant(a.getPatientId())).isTrue();
        assertThat(a.isParticipant(a.getProfessionalId())).isTrue();
        assertThat(a.isParticipant(UUID.randomUUID())).isFalse();
    }

    @Test
    void physicalCareCannotBeDoneRemotely() {
        assertThat(ReasonCategory.INJECTION.requiresHomeVisit()).isTrue();
        assertThat(ReasonCategory.ELDERLY_ASSISTANCE.requiresHomeVisit()).isTrue();
        assertThat(ReasonCategory.CONSULTATION.requiresHomeVisit()).isFalse();
        assertThat(ReasonCategory.PREGNANCY_FOLLOWUP.requiresHomeVisit()).isFalse();
        assertThat(AppointmentType.CHAT.hasMediaSession()).isFalse();
        assertThat(AppointmentType.TELECONSULTATION_AUDIO.hasMediaSession()).isTrue();
        assertThat(AppointmentType.HOME_VISIT.isRemote()).isFalse();
    }
}
