package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.ConsentHistoryDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.ConsentStatusDto;
import dev.jpzconception.allodocteur.medicalrecord.domain.Consent;
import dev.jpzconception.allodocteur.medicalrecord.domain.ConsentVersion;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.ConsentRepository;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.ConsentVersionRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.web.ClientIp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consentement numérique du patient : horodaté, versionné, révocable. Un consentement ne vaut que pour la version
 * courante du texte : quand une nouvelle version est publiée, il faut le renouveler pour que les professionnels
 * puissent à nouveau accéder au dossier.
 */
@Service
@Transactional
public class ConsentService {

    private final ConsentRepository consents;
    private final ConsentVersionRepository versions;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, ConsentVersionRepository versions, Clock clock) {
        this.consents = consents;
        this.versions = versions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConsentStatusDto status(UUID userId) {
        ConsentVersion current = current();
        Optional<Consent> active = consents.findByUserIdAndRevokedAtIsNull(userId)
                .filter(c -> c.getVersion().equals(current.getVersion()));
        return new ConsentStatusDto(current.getVersion(), current.getTitle(), current.getContent(),
                current.getPublishedAt(), active.isPresent(), active.map(Consent::getGrantedAt).orElse(null));
    }

    public ConsentStatusDto accept(UUID userId, String version) {
        ConsentVersion current = current();
        if (!current.getVersion().equals(version)) {
            throw ApiException.conflict("CONSENT_VERSION_OUTDATED",
                    "Cette version du consentement n'est plus d'actualité (version courante : " + current.getVersion() + ").");
        }
        Optional<Consent> active = consents.findByUserIdAndRevokedAtIsNull(userId);
        if (active.isPresent() && active.get().getVersion().equals(version)) {
            return status(userId); // déjà accepté : idempotent
        }
        Instant now = Instant.now(clock);
        if (active.isPresent()) {
            active.get().revoke(now); // remplacé par la nouvelle version
            consents.flush(); // l'index unique n'admet qu'un consentement actif à la fois
        }
        consents.save(new Consent(userId, version, now, ClientIp.current()));
        return status(userId);
    }

    /** Retire le consentement : les professionnels perdent l'accès dès la requête suivante. */
    public void revoke(UUID userId) {
        consents.findByUserIdAndRevokedAtIsNull(userId).ifPresent(c -> c.revoke(Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public boolean hasActive(UUID userId) {
        String current = current().getVersion();
        return consents.findByUserIdAndRevokedAtIsNull(userId).filter(c -> c.getVersion().equals(current)).isPresent();
    }

    @Transactional(readOnly = true)
    public List<ConsentHistoryDto> history(UUID userId) {
        return consents.findByUserIdOrderByGrantedAtDesc(userId).stream()
                .map(c -> new ConsentHistoryDto(c.getVersion(), c.getGrantedAt(), c.getRevokedAt())).toList();
    }

    private ConsentVersion current() {
        return versions.findFirstByPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant.now(clock))
                .orElseThrow(() -> new IllegalStateException("Aucune version de consentement publiée"));
    }
}
