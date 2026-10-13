package dev.jpzconception.allodocteur.sos.application;

import dev.jpzconception.allodocteur.medicalrecord.application.EmergencyAccessPolicy;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import dev.jpzconception.allodocteur.sos.infrastructure.EmergencyAccessGrantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Un accès d'urgence vaut tant que l'alerte est prise en charge (non clôturée, non annulée) et avant son expiration. */
@Service
@Transactional(readOnly = true)
public class EmergencyAccessService implements EmergencyAccessPolicy {

    private final EmergencyAccessGrantRepository grants;
    private final Clock clock;

    public EmergencyAccessService(EmergencyAccessGrantRepository grants, Clock clock) {
        this.grants = grants;
        this.clock = clock;
    }

    @Override
    public boolean allows(UUID professionalId, UUID accountId, UUID dependentId) {
        Instant now = Instant.now(clock);
        return dependentId == null
                ? grants.activeForSelf(professionalId, accountId, SosStatus.ACCEPTED, now)
                : grants.activeForDependent(professionalId, dependentId, SosStatus.ACCEPTED, now);
    }
}
