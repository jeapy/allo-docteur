package dev.jpzconception.allodocteur.professional.api;

import dev.jpzconception.allodocteur.professional.application.DocumentService;
import dev.jpzconception.allodocteur.professional.application.DocumentService.Download;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Téléchargement d'un justificatif : propriétaire ou administrateur uniquement. */
@RestController
@RequestMapping("/api/v1/professionals/documents")
@Tag(name = "Professionnel – justificatifs")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        Download d = documents.download(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.document().getContentType()))
                .contentLength(d.document().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.document().getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(d.content()));
    }
}
