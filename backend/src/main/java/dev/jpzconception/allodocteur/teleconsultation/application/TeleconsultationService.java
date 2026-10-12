package dev.jpzconception.allodocteur.teleconsultation.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentEvents;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.SessionDto;
import dev.jpzconception.allodocteur.teleconsultation.application.VideoProvider.JoinInfo;
import dev.jpzconception.allodocteur.teleconsultation.domain.MediaMode;
import dev.jpzconception.allodocteur.teleconsultation.domain.TeleconsultationSession;
import dev.jpzconception.allodocteur.teleconsultation.infrastructure.TeleconsultationSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ouvre la salle quand le rendez-vous démarre, la ferme quand il se termine, et délivre les jetons d'accès. */
@Service
@Transactional
public class TeleconsultationService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final TeleconsultationSessionRepository sessions;
    private final AppointmentAccess access;
    private final VideoProvider video;
    private final Clock clock;

    public TeleconsultationService(TeleconsultationSessionRepository sessions, AppointmentAccess access,
                                   VideoProvider video, Clock clock) {
        this.sessions = sessions;
        this.access = access;
        this.video = video;
        this.clock = clock;
    }

    /** Exécuté dans la transaction de AppointmentService.start : si la salle échoue, le démarrage est annulé. */
    @EventListener
    public void onStarted(AppointmentEvents.Started event) {
        if (!event.type().hasMediaSession()) return;
        if (sessions.findByAppointmentId(event.appointmentId()).isPresent()) return;
        MediaMode media = event.type() == AppointmentType.TELECONSULTATION_VIDEO ? MediaMode.VIDEO : MediaMode.AUDIO;
        TeleconsultationSession s = new TeleconsultationSession();
        s.setAppointmentId(event.appointmentId());
        s.setProvider(video.name());
        s.setRoomId(video.createRoom(event.appointmentId(), media));
        s.setMedia(media);
        s.setStartedAt(Instant.now(clock));
        sessions.save(s);
    }

    @EventListener
    public void onCompleted(AppointmentEvents.Completed event) {
        sessions.findByAppointmentId(event.appointmentId()).ifPresent(s -> {
            if (s.getEndedAt() == null) {
                s.setEndedAt(Instant.now(clock));
                video.closeRoom(s.getRoomId());
            }
        });
    }

    /** Jeton d'accès à la salle pour le patient ou le professionnel du rendez-vous en cours. */
    public SessionDto join(AuthenticatedUser me, UUID appointmentId) {
        Ref ref = access.requireParticipant(me, appointmentId);
        if (!ref.type().hasMediaSession()) {
            throw ApiException.conflict("NO_MEDIA_SESSION", "Ce rendez-vous n'a pas de session audio/vidéo.");
        }
        if (ref.status() != AppointmentStatus.IN_PROGRESS) {
            throw ApiException.conflict("SESSION_NOT_ACTIVE", "La consultation n'a pas encore démarré ou est terminée.");
        }
        TeleconsultationSession s = sessions.findByAppointmentId(appointmentId)
                .orElseThrow(() -> ApiException.conflict("SESSION_NOT_ACTIVE", "La consultation n'a pas encore démarré."));
        JoinInfo info = video.issueJoinInfo(s.getRoomId(), me.id(), TOKEN_TTL);
        return new SessionDto(s.getId(), s.getMedia(), s.getRoomId(), info.url(), info.token(), info.expiresAt(),
                s.getStartedAt());
    }
}
