package dev.jpzconception.allodocteur.shared.security;

import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.util.UUID;

/** Principal placé dans le SecurityContext à partir du JWT d'accès. */
public record AuthenticatedUser(UUID id, Role role, AdminLevel adminLevel) {}
