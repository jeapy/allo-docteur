package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecord;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalRecordRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point unique de décision d'accès à un dossier :
 * <ul>
 *   <li>le propriétaire du compte (patient ou tuteur) y accède toujours ;</li>
 *   <li>un professionnel y accède s'il a un lien de soin (rendez-vous confirmé, en cours ou terminé) ET que le patient a
 *       un consentement actif pour la version courante ;</li>
 *   <li>exception : un accès d'urgence (« bris de glace ») motivé, lié à une alerte SOS en cours, remplace le consentement ;</li>
 *   <li>les administrateurs n'y accèdent jamais.</li>
 * </ul>
 * Chaque accès d'un tiers, chaque écriture et chaque refus sont journalisés.
 */
@Service
@Transactional
public class RecordAccessService {

    public record Context(MedicalRecord record, boolean owner) {}

    private final MedicalRecordRepository records;
    private final AppointmentAccess appointments;
    private final ConsentService consents;
    private final MedicalAccessLogger logger;
    private final EmergencyAccessPolicy emergency;

    public RecordAccessService(MedicalRecordRepository records, AppointmentAccess appointments, ConsentService consents,
                               MedicalAccessLogger logger, EmergencyAccessPolicy emergency) {
        this.emergency = emergency;
        this.records = records;
        this.appointments = appointments;
        this.consents = consents;
        this.logger = logger;
    }

    /**
     * @param ownerOnly vrai pour les actions réservées au propriétaire (modifier la fiche, lire le journal d'accès)
     * @param targetId  objet concerné (entrée, ordonnance…), pour le journal
     */
    public Context resolve(AuthenticatedUser me, UUID recordId, AccessAction action, UUID targetId, boolean ownerOnly) {
        MedicalRecord record = records.findById(recordId).orElseThrow(RecordAccessService::notFound);

        if (me.role() == Role.PATIENT) {
            if (!record.getAccountId().equals(me.id())) {
                logger.denied(me, recordId, action, targetId, "NOT_OWNER");
                throw notFound(); // ne révèle pas l'existence du dossier
            }
            if (action.isWrite()) logger.granted(me, recordId, action, targetId);
            return new Context(record, true);
        }

        if (me.role() == Role.PROFESSIONAL) {
            if (ownerOnly) {
                logger.denied(me, recordId, action, targetId, "OWNER_ONLY");
                throw ApiException.forbidden("OWNER_ONLY", "Action réservée au patient.");
            }
            if (!appointments.hasCareRelationship(me.id(), record.getAccountId(), record.getDependentId())) {
                logger.denied(me, recordId, action, targetId, "NO_CARE_RELATIONSHIP");
                throw notFound();
            }
            if (!consents.hasActive(record.getAccountId())) {
                if (emergency.allows(me.id(), record.getAccountId(), record.getDependentId())) {
                    // « bris de glace » : accès sans consentement, toujours journalisé avec son motif
                    logger.granted(me, recordId, action, targetId, "EMERGENCY_ACCESS");
                    return new Context(record, false);
                }
                logger.denied(me, recordId, action, targetId, "NO_CONSENT");
                throw ApiException.forbidden("CONSENT_REQUIRED",
                        "Le patient n'a pas donné (ou a retiré) son consentement à l'accès à son dossier.");
            }
            logger.granted(me, recordId, action, targetId);
            return new Context(record, false);
        }

        throw ApiException.forbidden("ACCESS_DENIED", "Accès refusé.");
    }

    private static ApiException notFound() {
        return ApiException.notFound("RECORD_NOT_FOUND", "Dossier introuvable.");
    }
}
