package dev.jpzconception.allodocteur.partner.api;

import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class PartnerDtos {
    private PartnerDtos() {}

    public record PartnerRequest(@NotBlank @Size(max = 150) String name, @NotNull PartnerType type, Integer cityId,
                                 @Size(max = 255) String address, @Email @Size(max = 255) String contactEmail,
                                 @Size(max = 30) String contactPhone, LocalDate contractStartDate) {}

    public record PolicyRequest(UUID dependentId, @NotNull UUID partnerId, @NotBlank @Size(max = 60) String policyNumber,
                                @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal coverageRate,
                                @NotNull LocalDate validUntil) {}
}
