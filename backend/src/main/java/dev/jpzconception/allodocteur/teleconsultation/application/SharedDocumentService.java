package dev.jpzconception.allodocteur.teleconsultation.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess.Ref;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.storage.FileInspector;
import dev.jpzconception.allodocteur.shared.storage.StorageService;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.SharedDocumentDto;
import dev.jpzconception.allodocteur.teleconsultation.domain.DocumentKind;
import dev.jpzconception.allodocteur.teleconsultation.domain.SharedDocument;
import dev.jpzconception.allodocteur.teleconsultation.infrastructure.SharedDocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Images médicales et documents partagés entre les deux participants d'un rendez-vous. */
@Service
@Transactional
public class SharedDocumentService {

    static final int MAX_DOCUMENTS = 30;
    static final long MAX_BYTES = 10L * 1024 * 1024;

    public record Download(SharedDocument document, InputStream content) {}

    private final SharedDocumentRepository documents;
    private final AppointmentAccess access;
    private final ConversationPolicy policy;
    private final StorageService storage;
    private final Clock clock;

    public SharedDocumentService(SharedDocumentRepository documents, AppointmentAccess access,
                                 ConversationPolicy policy, StorageService storage, Clock clock) {
        this.documents = documents;
        this.access = access;
        this.policy = policy;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SharedDocumentDto> list(AuthenticatedUser me, UUID appointmentId) {
        access.requireParticipant(me, appointmentId);
        return documents.findByAppointmentIdOrderByUploadedAtAsc(appointmentId).stream().map(this::toDto).toList();
    }

    public SharedDocumentDto upload(AuthenticatedUser me, UUID appointmentId, MultipartFile file, String caption) {
        Ref ref = access.requireParticipant(me, appointmentId);
        policy.requireOpen(ref);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Fichier vide.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Fichier trop volumineux (10 Mo maximum).");
        }
        if (documents.countByAppointmentId(appointmentId) >= MAX_DOCUMENTS) {
            throw ApiException.conflict("TOO_MANY_DOCUMENTS", "Nombre maximal de documents atteint pour ce rendez-vous.");
        }
        try {
            byte[] bytes = file.getBytes();
            String contentType = FileInspector.detectContentType(bytes);
            if (contentType == null) {
                throw ApiException.badRequest("UNSUPPORTED_FILE", "Formats acceptés : PDF, JPEG, PNG, WebP.");
            }
            String key = "appointments/" + appointmentId + "/" + UUID.randomUUID();
            storage.store(key, bytes);

            SharedDocument d = new SharedDocument();
            d.setAppointmentId(appointmentId);
            d.setUploadedBy(me.id());
            d.setKind("application/pdf".equals(contentType) ? DocumentKind.PDF : DocumentKind.IMAGE);
            d.setOriginalFilename(FileInspector.sanitizeFilename(file.getOriginalFilename()));
            d.setCaption(caption == null || caption.isBlank() ? null : trimTo(caption.trim(), 255));
            d.setStorageKey(key);
            d.setContentType(contentType);
            d.setSizeBytes(bytes.length);
            d.setChecksumSha256(FileInspector.sha256(bytes));
            d.setUploadedAt(Instant.now(clock));
            documents.save(d);
            return toDto(d);
        } catch (IOException e) {
            throw new IllegalStateException("Échec de l'enregistrement du fichier", e);
        }
    }

    @Transactional(readOnly = true)
    public Download download(AuthenticatedUser me, UUID appointmentId, UUID documentId) {
        access.requireParticipant(me, appointmentId);
        SharedDocument d = find(appointmentId, documentId);
        try {
            return new Download(d, storage.open(d.getStorageKey()));
        } catch (IOException e) {
            throw ApiException.notFound("FILE_MISSING", "Fichier indisponible.");
        }
    }

    /** Seul l'auteur peut retirer son document, tant que la conversation est ouverte. */
    public void delete(AuthenticatedUser me, UUID appointmentId, UUID documentId) {
        Ref ref = access.requireParticipant(me, appointmentId);
        SharedDocument d = find(appointmentId, documentId);
        if (!d.getUploadedBy().equals(me.id())) {
            throw ApiException.forbidden("NOT_UPLOADER", "Seul l'auteur peut supprimer ce document.");
        }
        policy.requireOpen(ref);
        documents.delete(d);
        try {
            storage.delete(d.getStorageKey());
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la suppression du fichier", e);
        }
    }

    private SharedDocument find(UUID appointmentId, UUID documentId) {
        return documents.findById(documentId)
                .filter(d -> d.getAppointmentId().equals(appointmentId))
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_NOT_FOUND", "Document introuvable."));
    }

    private SharedDocumentDto toDto(SharedDocument d) {
        return new SharedDocumentDto(d.getId(), d.getUploadedBy(), d.getKind(), d.getOriginalFilename(), d.getCaption(),
                d.getContentType(), d.getSizeBytes(), d.getUploadedAt());
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
