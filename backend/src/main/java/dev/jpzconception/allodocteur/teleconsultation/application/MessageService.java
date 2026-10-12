package dev.jpzconception.allodocteur.teleconsultation.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.MessageDto;
import dev.jpzconception.allodocteur.teleconsultation.domain.Message;
import dev.jpzconception.allodocteur.teleconsultation.infrastructure.MessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Messagerie d'un rendez-vous, par interrogation régulière (polling) ; un WebSocket pourra la compléter plus tard. */
@Service
@Transactional
public class MessageService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final MessageRepository messages;
    private final AppointmentAccess access;
    private final ConversationPolicy policy;
    private final Clock clock;

    public MessageService(MessageRepository messages, AppointmentAccess access, ConversationPolicy policy, Clock clock) {
        this.messages = messages;
        this.access = access;
        this.policy = policy;
        this.clock = clock;
    }

    public MessageDto send(AuthenticatedUser me, UUID appointmentId, String content) {
        Ref ref = access.requireParticipant(me, appointmentId);
        policy.requireOpen(ref);
        Message m = new Message();
        m.setAppointmentId(appointmentId);
        m.setSenderId(me.id());
        m.setContent(content.trim());
        m.setSentAt(Instant.now(clock));
        messages.save(m);
        return toDto(m, ref);
    }

    /**
     * Messages triés par date d'envoi. {@code since} (exclu) sert de curseur pour le polling : le client repasse la date
     * du dernier message reçu et déduplique par identifiant.
     */
    @Transactional(readOnly = true)
    public List<MessageDto> list(AuthenticatedUser me, UUID appointmentId, Instant since, Integer limit) {
        Ref ref = access.requireParticipant(me, appointmentId);
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        var page = PageRequest.of(0, size);
        List<Message> found = since == null
                ? messages.findByAppointmentIdOrderBySentAtAscIdAsc(appointmentId, page)
                : messages.findByAppointmentIdAndSentAtAfterOrderBySentAtAscIdAsc(appointmentId, since, page);
        return found.stream().map(m -> toDto(m, ref)).toList();
    }

    /** Marque comme lus les messages reçus (ceux de l'autre participant). Renvoie leur nombre. */
    public long markRead(AuthenticatedUser me, UUID appointmentId) {
        access.requireParticipant(me, appointmentId);
        return messages.markRead(appointmentId, me.id(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser me, UUID appointmentId) {
        access.requireParticipant(me, appointmentId);
        return messages.countByAppointmentIdAndSenderIdNotAndReadAtIsNull(appointmentId, me.id());
    }

    private static MessageDto toDto(Message m, Ref ref) {
        String role = ref.isPatient(m.getSenderId()) ? "PATIENT" : "PROFESSIONAL";
        return new MessageDto(m.getId(), m.getSenderId(), role, m.getContent(), m.getSentAt(), m.getReadAt());
    }
}
