package dev.jpzconception.allodocteur.teleconsultation.api;

import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.CountDto;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.MessageDto;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.SendMessageRequest;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.SessionDto;
import dev.jpzconception.allodocteur.teleconsultation.api.TeleconsultationDtos.SharedDocumentDto;
import dev.jpzconception.allodocteur.teleconsultation.application.MessageService;
import dev.jpzconception.allodocteur.teleconsultation.application.SharedDocumentService;
import dev.jpzconception.allodocteur.teleconsultation.application.SharedDocumentService.Download;
import dev.jpzconception.allodocteur.teleconsultation.application.TeleconsultationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Session audio/vidéo, messagerie et documents d'un rendez-vous. Réservé aux deux participants : le contenu n'est
 * volontairement pas accessible aux administrateurs.
 */
@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Téléconsultation, messagerie et documents")
public class ConversationController {

    private final TeleconsultationService teleconsultation;
    private final MessageService messages;
    private final SharedDocumentService documents;

    public ConversationController(TeleconsultationService teleconsultation, MessageService messages,
                                  SharedDocumentService documents) {
        this.teleconsultation = teleconsultation;
        this.messages = messages;
        this.documents = documents;
    }

    // ---------------------------------------------------------------- session audio/vidéo

    /** Jeton d'accès à la salle ; disponible uniquement quand le professionnel a démarré le rendez-vous. */
    @PostMapping("/session")
    public SessionDto join(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId) {
        return teleconsultation.join(me, appointmentId);
    }

    // ---------------------------------------------------------------- messagerie

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId,
                           @Valid @RequestBody SendMessageRequest request) {
        return messages.send(me, appointmentId, request.content());
    }

    @GetMapping("/messages")
    public List<MessageDto> messages(
            @AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) Integer limit) {
        return messages.list(me, appointmentId, since, limit);
    }

    @GetMapping("/messages/unread-count")
    public CountDto unread(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId) {
        return new CountDto(messages.unreadCount(me, appointmentId));
    }

    @PostMapping("/messages/read")
    public CountDto markRead(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId) {
        return new CountDto(messages.markRead(me, appointmentId));
    }

    // ---------------------------------------------------------------- documents

    @GetMapping("/documents")
    public List<SharedDocumentDto> documents(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId) {
        return documents.list(me, appointmentId);
    }

    /** Multipart : {@code file} (PDF, JPEG, PNG ou WebP, 10 Mo max) et {@code caption} facultatif. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SharedDocumentDto upload(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "caption", required = false) String caption) {
        return documents.upload(me, appointmentId, file, caption);
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal AuthenticatedUser me,
                                                       @PathVariable UUID appointmentId, @PathVariable UUID documentId) {
        Download d = documents.download(me, appointmentId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.document().getContentType()))
                .contentLength(d.document().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.document().getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(d.content()));
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId,
                       @PathVariable UUID documentId) {
        documents.delete(me, appointmentId, documentId);
    }
}
