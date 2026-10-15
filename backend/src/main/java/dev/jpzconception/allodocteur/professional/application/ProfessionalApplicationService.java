package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.auth.api.AuthDtos.RegisterRequest;
import dev.jpzconception.allodocteur.auth.application.AuthService;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.notification.application.EmailSender;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.ApplicationReceipt;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.ApplicationRequest;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.LicenseCheckOutcome;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.LicenseCheckResponse;
import dev.jpzconception.allodocteur.professional.domain.DocumentType;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.reference.infrastructure.ProfessionalTypeRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.web.ClientIp;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidature d'un professionnel sans compte préalable : création du compte, du profil (en attente de validation)
 * et des justificatifs en une seule transaction. Rien n'est créé si une étape échoue.
 */
@Service
public class ProfessionalApplicationService {

    static final int MAX_FILES = 5;

    private final AuthService auth;
    private final ProfessionalService professionals;
    private final DocumentService documents;
    private final LicenseVerifier licenseVerifier;
    private final ProfessionalRepository repository;
    private final ProfessionalTypeRepository types;
    private final AuditService audit;
    private final EmailSender email;

    public ProfessionalApplicationService(AuthService auth, ProfessionalService professionals,
                                          DocumentService documents, LicenseVerifier licenseVerifier,
                                          ProfessionalRepository repository, ProfessionalTypeRepository types,
                                          AuditService audit, EmailSender email) {
        this.auth = auth;
        this.professionals = professionals;
        this.documents = documents;
        this.licenseVerifier = licenseVerifier;
        this.repository = repository;
        this.types = types;
        this.audit = audit;
        this.email = email;
    }

    @Transactional(readOnly = true)
    public LicenseCheckResponse checkLicense(Integer typeId, String rawNumber) {
        var type = types.findById(typeId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TYPE", "Type de professionnel inconnu."));
        String number = LicenseVerifier.normalize(rawNumber);
        var result = licenseVerifier.check(type, number);
        return switch (result) {
            case INVALID_FORMAT -> new LicenseCheckResponse(LicenseCheckOutcome.INVALID_FORMAT,
                    "Format de numéro d'ordre invalide pour ce type de professionnel.");
            case REGISTRY_NOT_FOUND -> new LicenseCheckResponse(LicenseCheckOutcome.REGISTRY_NOT_FOUND,
                    "Ce numéro n'a pas été trouvé dans le registre de l'ordre.");
            case REGISTRY_CONFIRMED, FORMAT_VALID -> {
                if (repository.licenseExists(number)) {
                    yield new LicenseCheckResponse(LicenseCheckOutcome.ALREADY_REGISTERED,
                            "Ce numéro d'ordre est déjà enregistré.");
                }
                yield result == LicenseVerifier.Result.REGISTRY_CONFIRMED
                        ? new LicenseCheckResponse(LicenseCheckOutcome.REGISTRY_CONFIRMED, "Numéro confirmé par l'ordre.")
                        : new LicenseCheckResponse(LicenseCheckOutcome.FORMAT_VALID,
                                "Format valide. Le numéro sera vérifié par notre équipe.");
            }
        };
    }

    @Transactional
    public ApplicationReceipt submit(ApplicationRequest request, List<DocumentType> documentTypes,
                                     List<MultipartFile> files) {
        validateFiles(documentTypes, files);
        var acc = request.account();
        User user = auth.register(new RegisterRequest(acc.email(), acc.password(), acc.firstName(), acc.lastName(),
                acc.phone(), Role.PROFESSIONAL));
        var detail = professionals.upsert(user.getId(), request.profile());
        for (int i = 0; i < files.size(); i++) {
            documents.upload(user.getId(), documentTypes.get(i), files.get(i));
        }
        audit.record(user.getId(), "PROFESSIONAL_APPLICATION_SUBMITTED", "PROFESSIONAL", user.getId().toString(),
                ClientIp.current(), detail.licenseNumber());
        email.send(user.getEmail(), "Votre candidature Allo Docteur a bien été reçue",
                "Merci " + user.getFirstName() + ". Notre équipe vérifie votre numéro d'ordre et vos justificatifs. "
                        + "Vous serez averti par e-mail dès que votre profil sera validé.");
        return new ApplicationReceipt(user.getId(), user.getEmail(), detail.verificationStatus());
    }

    private void validateFiles(List<DocumentType> documentTypes, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || documentTypes == null) {
            throw ApiException.badRequest("DOCUMENTS_REQUIRED", "Au moins un justificatif est requis.");
        }
        if (files.size() > MAX_FILES) {
            throw ApiException.badRequest("TOO_MANY_DOCUMENTS", "Cinq justificatifs maximum.");
        }
        if (documentTypes.size() != files.size()) {
            throw ApiException.badRequest("DOCUMENT_TYPES_MISMATCH", "Chaque fichier doit avoir un type.");
        }
        if (!documentTypes.contains(DocumentType.LICENSE)) {
            throw ApiException.badRequest("LICENSE_DOCUMENT_REQUIRED",
                    "Joignez un justificatif du numéro d'ordre (carte ou attestation).");
        }
        files.forEach(documents::precheck);
    }
}
