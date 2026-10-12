package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.DocumentDto;
import dev.jpzconception.allodocteur.professional.domain.DocumentType;
import dev.jpzconception.allodocteur.professional.domain.ProfessionalDocument;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalDocumentRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.storage.FileInspector;
import dev.jpzconception.allodocteur.shared.storage.StorageService;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Justificatifs d'un professionnel (numéro d'ordre, diplômes, pièce d'identité). */
@Service
@Transactional
public class DocumentService {

    static final int MAX_DOCUMENTS = 10;
    static final long MAX_BYTES = 5L * 1024 * 1024;

    public record Download(ProfessionalDocument document, InputStream content) {}

    private final ProfessionalRepository professionals;
    private final ProfessionalDocumentRepository documents;
    private final StorageService storage;
    private final ProfessionalAssembler assembler;
    private final Clock clock;

    public DocumentService(ProfessionalRepository professionals, ProfessionalDocumentRepository documents,
                           StorageService storage, ProfessionalAssembler assembler, Clock clock) {
        this.professionals = professionals;
        this.documents = documents;
        this.storage = storage;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list(UUID professionalId) {
        return documents.findByProfessionalIdOrderByUploadedAtDesc(professionalId).stream()
                .map(assembler::document).toList();
    }

    public DocumentDto upload(UUID professionalId, DocumentType type, MultipartFile file) {
        if (!professionals.existsById(professionalId)) {
            throw ApiException.notFound("PROFILE_REQUIRED", "Créez d'abord votre profil professionnel.");
        }
        if (documents.countByProfessionalId(professionalId) >= MAX_DOCUMENTS) {
            throw ApiException.conflict("TOO_MANY_DOCUMENTS", "Nombre maximal de justificatifs atteint.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Fichier vide.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Fichier trop volumineux (5 Mo maximum).");
        }
        try {
            byte[] bytes = file.getBytes();
            String contentType = detectContentType(bytes);
            if (contentType == null) {
                throw ApiException.badRequest("UNSUPPORTED_FILE", "Formats acceptés : PDF, JPEG, PNG.");
            }
            String key = "professionals/" + professionalId + "/" + UUID.randomUUID();
            storage.store(key, bytes);

            ProfessionalDocument doc = new ProfessionalDocument();
            doc.setProfessionalId(professionalId);
            doc.setType(type);
            doc.setOriginalFilename(sanitize(file.getOriginalFilename()));
            doc.setStorageKey(key);
            doc.setContentType(contentType);
            doc.setSizeBytes(bytes.length);
            doc.setChecksumSha256(sha256(bytes));
            doc.setUploadedAt(Instant.now(clock));
            documents.save(doc);
            return assembler.document(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Échec de l'enregistrement du fichier", e);
        }
    }

    public void delete(UUID professionalId, UUID documentId) {
        ProfessionalDocument doc = documents.findById(documentId)
                .filter(d -> d.getProfessionalId().equals(professionalId))
                .orElseThrow(DocumentService::notFound);
        documents.delete(doc);
        try {
            storage.delete(doc.getStorageKey());
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la suppression du fichier", e);
        }
    }

    /** Accessible au propriétaire et aux administrateurs uniquement. */
    @Transactional(readOnly = true)
    public Download download(AuthenticatedUser requester, UUID documentId) {
        ProfessionalDocument doc = documents.findById(documentId).orElseThrow(DocumentService::notFound);
        boolean allowed = requester.role() == Role.ADMIN || requester.id().equals(doc.getProfessionalId());
        if (!allowed) throw notFound(); // ne révèle pas l'existence du fichier
        try {
            return new Download(doc, storage.open(doc.getStorageKey()));
        } catch (IOException e) {
            throw ApiException.notFound("FILE_MISSING", "Fichier indisponible.");
        }
    }

    /** Les justificatifs n'acceptent que PDF, JPEG et PNG. */
    static String detectContentType(byte[] b) {
        String type = FileInspector.detectContentType(b);
        return "image/webp".equals(type) ? null : type;
    }

    static String sanitize(String name) {
        return FileInspector.sanitizeFilename(name);
    }

    private static String sha256(byte[] bytes) {
        return FileInspector.sha256(bytes);
    }

    private static ApiException notFound() {
        return ApiException.notFound("DOCUMENT_NOT_FOUND", "Document introuvable.");
    }
}
