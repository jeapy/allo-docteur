package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Sélection de l'opérateur : par code explicite, sinon le premier qui gère le moyen de paiement. */
@Component
public class PaymentProviders {

    private final List<PaymentProvider> providers;

    public PaymentProviders(List<PaymentProvider> providers) {
        this.providers = providers;
    }

    public PaymentProvider forMethod(PaymentMethod method, String code) {
        return providers.stream()
                .filter(p -> p.method() == method)
                .filter(p -> code == null || code.isBlank() || p.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("PROVIDER_UNAVAILABLE",
                        "Aucun opérateur disponible pour ce moyen de paiement."));
    }

    public PaymentProvider byCode(String code) {
        return providers.stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("PROVIDER_UNAVAILABLE", "Opérateur inconnu : " + code));
    }
}
