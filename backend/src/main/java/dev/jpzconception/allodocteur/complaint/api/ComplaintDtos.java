package dev.jpzconception.allodocteur.complaint.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ComplaintDtos {
    private ComplaintDtos() {}

    public record CreateComplaintRequest(@NotBlank @Size(max = 255) String subject,
                                         @NotBlank @Size(max = 5000) String description,
                                         UUID appointmentId) {}

    public record MessageRequest(@NotBlank @Size(max = 5000) String body) {}

    public record ResolutionRequest(@NotBlank @Size(max = 5000) String text) {}
}
