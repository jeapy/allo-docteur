package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.application.PaymentProvider;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opérateur simulé (aucun appel réseau). Comportement piloté par la fin de la référence payeur :
 * « 0000 » refuse, « 9999 » reste en attente puis aboutit à l'interrogation ; le virement est toujours en attente
 * (confirmé par la finance). À remplacer par une intégration réelle derrière le même port.
 */
public class FakePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);

    private final String code;
    private final PaymentMethod method;

    public FakePaymentProvider(String code, PaymentMethod method) {
        this.code = code;
        this.method = method;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public PaymentMethod method() {
        return method;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String ref = "FAKE-" + request.paymentId().toString().substring(0, 8);
        if (method == PaymentMethod.BANK_TRANSFER) {
            return ChargeResult.pending(ref);
        }
        String payer = request.payerReference() == null ? "" : request.payerReference().trim();
        if (payer.endsWith("0000")) {
            return ChargeResult.failed(ref, "Paiement refusé par l'opérateur (simulation).");
        }
        if (payer.endsWith("9999")) {
            return ChargeResult.pending(ref);
        }
        return ChargeResult.succeeded(ref);
    }

    @Override
    public ChargeResult status(String providerRef) {
        return method == PaymentMethod.BANK_TRANSFER ? ChargeResult.pending(providerRef) : ChargeResult.succeeded(providerRef);
    }

    @Override
    public void refund(String providerRef, BigDecimal amount, String currency) {
        log.info("[{}] remboursement simulé de {} {} ({})", code, amount, currency, providerRef);
    }

    @Override
    public PayoutResult payout(PayoutRequest request) {
        String ref = "FAKE-PAYOUT-" + request.withdrawalId().toString().substring(0, 8);
        if (request.destination() != null && request.destination().trim().endsWith("0000")) {
            return new PayoutResult(false, ref, "Versement refusé (simulation).");
        }
        log.info("[{}] versement simulé de {} {} vers {}", code, request.amount(), request.currency(), ref);
        return new PayoutResult(true, ref, null);
    }
}
