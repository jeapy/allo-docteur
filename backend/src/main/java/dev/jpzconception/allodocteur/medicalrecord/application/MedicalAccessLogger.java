package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessOutcome;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalAccessLog;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalAccessLogRepository;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.ClientIp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Écrit le journal d'accès aux dossiers. Un échec d'écriture fait échouer l'accès : pas d'accès sans trace. */
@Component
public class MedicalAccessLogger {

    private final MedicalAccessLogRepository logs;
    private final Clock clock;

    public MedicalAccessLogger(MedicalAccessLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    /** Dans la transaction de l'appelant (qui ne doit pas être en lecture seule) : la trace suit le sort de l'accès. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void granted(AuthenticatedUser actor, UUID recordId, AccessAction action, UUID targetId) {
        granted(actor, recordId, action, targetId, null);
    }

    /** Accès autorisé à titre exceptionnel : {@code reason} précise pourquoi (ex. EMERGENCY_ACCESS). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void granted(AuthenticatedUser actor, UUID recordId, AccessAction action, UUID targetId, String reason) {
        logs.save(new MedicalAccessLog(recordId, actor.id(), actor.role(), action, targetId, AccessOutcome.GRANTED,
                reason, ClientIp.current(), Instant.now(clock)));
    }

    /** Transaction indépendante : un refus doit rester tracé même si la requête échoue ensuite. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(AuthenticatedUser actor, UUID recordId, AccessAction action, UUID targetId, String reason) {
        logs.save(new MedicalAccessLog(recordId, actor.id(), actor.role(), action, targetId, AccessOutcome.DENIED,
                reason, ClientIp.current(), Instant.now(clock)));
    }
}
