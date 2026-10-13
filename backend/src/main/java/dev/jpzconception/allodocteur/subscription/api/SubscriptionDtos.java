package dev.jpzconception.allodocteur.subscription.api;

import dev.jpzconception.allodocteur.subscription.domain.PlanAudience;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SubscriptionDtos {

    private SubscriptionDtos() {}

    public record PlanDto(UUID id, String code, PlanAudience audience, String name, String description, BigDecimal price,
                          String currency, int durationDays, BigDecimal commissionRate, boolean active) {}

    public record SubscriptionDto(UUID id, UUID userId, String userName, String planCode, String planName,
                                  SubscriptionStatus status, Instant startsAt, Instant endsAt, UUID paymentId) {}

    public record PlanUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0") BigDecimal price,
            @Min(1) @Max(3650) int durationDays,
            @DecimalMin("0") @DecimalMax("1") BigDecimal commissionRate,
            boolean active) {}
}
