package dev.jpzconception.allodocteur.user.api;

import dev.jpzconception.allodocteur.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull UserStatus status) {}
