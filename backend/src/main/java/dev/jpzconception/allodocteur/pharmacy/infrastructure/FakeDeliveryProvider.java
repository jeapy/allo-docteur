package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.application.DeliveryProvider;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Prestataire factice : aucun livreur réel, un code de suivi aléatoire. À remplacer par une vraie intégration. */
@Component
public class FakeDeliveryProvider implements DeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeDeliveryProvider.class);

    private final SecureRandom random = new SecureRandom();

    @Override
    public Dispatch dispatch(Request request) {
        String tracking = "FAKE-" + String.format("%08d", random.nextInt(100_000_000));
        log.info("Livraison factice de la commande {} (suivi {})", request.orderNumber(), tracking);
        return new Dispatch("fake", tracking, "Livreur de démonstration", "+2250000000000");
    }
}
