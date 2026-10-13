package dev.jpzconception.allodocteur.pharmacy.api;

import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.AcceptRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.DeliverRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderSummaryDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.ReadyRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.ReasonRequest;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyFulfillmentService;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyService;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Espace des pharmaciens rattachés à une pharmacie. */
@RestController
@RequestMapping("/api/v1/pharmacy")
@PreAuthorize("hasRole('PROFESSIONAL')")
@Tag(name = "Pharmacie – traitement des commandes")
public class PharmacyFulfillmentController {

    private final PharmacyFulfillmentService service;
    private final PharmacyService pharmacies;

    public PharmacyFulfillmentController(PharmacyFulfillmentService service, PharmacyService pharmacies) {
        this.service = service;
        this.pharmacies = pharmacies;
    }

    @GetMapping("/mine")
    public List<PharmacyDto> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return pharmacies.mine(me.id());
    }

    @GetMapping("/orders")
    public PageResponse<OrderSummaryDto> list(@AuthenticationPrincipal AuthenticatedUser me,
                                              @RequestParam(required = false) List<OrderStatus> status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(me, status, page, size));
    }

    /** Contenu de la commande (lignes d'ordonnance) : la lecture est tracée dans le journal d'accès du patient. */
    @GetMapping("/orders/{id}")
    public OrderDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/orders/{id}/accept")
    public OrderDto accept(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                           @Valid @RequestBody(required = false) AcceptRequest request) {
        return service.accept(me, id, request == null ? new AcceptRequest(null, null, null) : request);
    }

    @PostMapping("/orders/{id}/reject")
    public OrderDto reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                           @Valid @RequestBody ReasonRequest request) {
        return service.reject(me, id, request.reason());
    }

    @PostMapping("/orders/{id}/ready")
    public OrderDto ready(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                          @Valid @RequestBody(required = false) ReadyRequest request) {
        return service.ready(me, id, request == null ? null : request.message());
    }

    @PostMapping("/orders/{id}/handover")
    public OrderDto handOver(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.handOver(me, id);
    }

    @PostMapping("/orders/{id}/dispatch")
    public OrderDto dispatch(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.dispatch(me, id);
    }

    @PostMapping("/orders/{id}/deliver")
    public OrderDto deliver(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                            @Valid @RequestBody DeliverRequest request) {
        return service.deliver(me, id, request.code());
    }

    @PostMapping("/orders/{id}/delivery-failed")
    public OrderDto deliveryFailed(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                   @Valid @RequestBody ReasonRequest request) {
        return service.deliveryFailed(me, id, request.reason());
    }
}
