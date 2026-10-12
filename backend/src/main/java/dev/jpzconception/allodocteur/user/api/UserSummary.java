package dev.jpzconception.allodocteur.user.api;

import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** Ligne de la liste d'administration des utilisateurs. */
public record UserSummary(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        Role role,
        UserStatus status,
        Instant createdAt) {}
