package dev.jpzconception.allodocteur.pharmacy.api;

import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderSummaryDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyDto;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyOrderService;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Annuaire des pharmacies et commandes du patient. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Pharmacie")
public class PharmacyController {

    private final PharmacyService pharmacies;
    private final PharmacyOrderService orders;

    public PharmacyController(PharmacyService pharmacies, PharmacyOrderService orders) {
        this.pharmacies = pharmacies;
        this.orders = orders;
    }

    @GetMapping("/pharmacies")
    public PageResponse<PharmacyDto> search(@RequestParam(required = false) Integer cityId,
                                            @RequestParam(required = false) Integer districtId,
                                            @RequestParam(required = false) Boolean onDuty,
                                            @RequestParam(required = false) Boolean delivery,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(pharmacies.search(cityId, districtId, onDuty, delivery, q, true, page, size));
    }

    /** Pharmacies les plus proches d'une position (de garde seulement si {@code onDuty=true}). */
    @GetMapping("/pharmacies/nearby")
    public List<PharmacyDto> nearby(@RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude,
                                    @RequestParam(defaultValue = "false") boolean onDuty,
                                    @RequestParam(defaultValue = "5") int limit) {
        return pharmacies.nearby(latitude, longitude, onDuty, Math.min(Math.max(limit, 1), 20));
    }

    @GetMapping("/pharmacies/{id}")
    public PharmacyDto getPharmacy(@PathVariable UUID id) {
        return pharmacies.get(id);
    }

    @PostMapping("/pharmacy-orders")
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto place(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody OrderRequest request) {
        return orders.place(me, request);
    }

    @GetMapping("/pharmacy-orders")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<OrderSummaryDto> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(orders.mine(me, page, size));
    }

    @GetMapping("/pharmacy-orders/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public OrderDto getOrder(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return orders.get(me, id);
    }

    @PostMapping("/pharmacy-orders/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public OrderDto cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return orders.cancel(me, id);
    }
}
