package dev.jpzconception.allodocteur.appointment.api;

import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.SlotDto;
import dev.jpzconception.allodocteur.appointment.application.SlotService;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/professionals/{id}/slots")
@Tag(name = "Rendez-vous")
public class SlotController {

    private final SlotService service;

    public SlotController(SlotService service) {
        this.service = service;
    }

    /** Créneaux libres d'un professionnel pour un mode donné, sur 14 jours maximum. */
    @GetMapping
    public List<SlotDto> slots(
            @PathVariable UUID id,
            @RequestParam AppointmentType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.slots(id, from, to == null ? from : to, type).stream()
                .map(s -> new SlotDto(s.startAt(), s.endAt())).toList();
    }
}
