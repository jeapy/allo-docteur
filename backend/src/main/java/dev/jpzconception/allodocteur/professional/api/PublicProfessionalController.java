package dev.jpzconception.allodocteur.professional.api;

import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.ApplicationReceipt;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.ApplicationRequest;
import dev.jpzconception.allodocteur.professional.api.PublicProfessionalDtos.LicenseCheckResponse;
import dev.jpzconception.allodocteur.professional.application.ProfessionalApplicationService;
import dev.jpzconception.allodocteur.professional.domain.DocumentType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints publics (sans JWT, limités par IP) : candidature d'un professionnel et contrôle du numéro d'ordre. */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public – candidature professionnelle")
public class PublicProfessionalController {

    private final ProfessionalApplicationService service;

    public PublicProfessionalController(ProfessionalApplicationService service) {
        this.service = service;
    }

    @GetMapping("/license-check")
    public LicenseCheckResponse licenseCheck(@RequestParam Integer typeId, @RequestParam String number) {
        return service.checkLicense(typeId, number);
    }

    /**
     * Multipart : partie JSON {@code application}, puis {@code types} (un par fichier, dans le même ordre)
     * et {@code files} (PDF/JPEG/PNG, 5 Mo max chacun, 5 fichiers max, dont au moins un justificatif LICENSE).
     */
    @PostMapping(value = "/professional-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationReceipt submit(@RequestPart("application") @Valid ApplicationRequest application,
                                     @RequestParam("types") List<DocumentType> types,
                                     @RequestPart("files") List<MultipartFile> files) {
        return service.submit(application, types, files);
    }
}
