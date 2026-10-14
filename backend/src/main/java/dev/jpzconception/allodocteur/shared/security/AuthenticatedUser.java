package dev.jpzconception.allodocteur.shared.security;

import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.util.UUID;

/**
 * Principal placé dans le SecurityContext à partir du JWT d'accès.
 *
 * @param twoFactor la double authentification est activée sur le compte (lue dans le jeton ; elle change
 *                  donc au prochain rafraîchissement du jeton, pas en cours de validité d'un jeton existant)
 */
public record AuthenticatedUser(UUID id, Role role, AdminLevel adminLevel, boolean twoFactor) {

    public AuthenticatedUser(UUID id, Role role, AdminLevel adminLevel) {
        this(id, role, adminLevel, true);
    }

    /** Administrateurs dont le niveau donne accès à la finance ou à tous les droits : 2FA exigée. */
    public boolean mustEnrollTwoFactor() {
        return role == Role.ADMIN
                && !twoFactor
                && (adminLevel == null || adminLevel == AdminLevel.SUPER || adminLevel == AdminLevel.FINANCE);
    }
}
