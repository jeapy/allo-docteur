package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.UUID;

/** Port implémenté pour chaque objet payable : dit combien payer, à qui, et dans quel état se trouve la prestation. */
public interface PayableResolver {

    PayableType type();

    /** Les fonds restent en séquestre jusqu'à la prestation (rendez-vous, commande) ; sinon ils sont répartis tout de suite. */
    boolean escrow();

    /** Vérifie que {@code payer} peut payer cet objet maintenant (404 si ce n'est pas le sien, 409 si non payable). */
    PayableInfo resolve(AuthenticatedUser payer, UUID payableId);

    /** État actuel de la prestation, pour décider quoi faire d'un paiement confirmé après coup. */
    State state(UUID payableId);

    enum State {
        /** Prestation à venir ou en cours : les fonds restent en séquestre. */
        OPEN,
        /** Prestation rendue : les fonds sont répartis. */
        DELIVERED,
        /** Annulée ou refusée : les fonds sont remboursés. */
        CLOSED
    }

    record PayableInfo(BigDecimal amount, String currency, PayeeType payeeType, UUID payeeId, String description) {}
}
