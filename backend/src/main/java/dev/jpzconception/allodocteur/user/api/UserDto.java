package dev.jpzconception.allodocteur.user.api;

import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import java.util.UUID;

/** Vue du compte courant (/auth/me, réponses d'authentification). */
public record UserDto(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        Role role,
        AdminLevel adminLevel,
        UserStatus status,
        boolean mfaEnabled) {}
