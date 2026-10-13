package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opérateurs simulés par défaut ; une intégration réelle s'ajoute comme un autre bean {@code PaymentProvider}. */
@Configuration
public class PaymentProviderConfig {

    @Bean
    FakePaymentProvider fakeMobileMoney() {
        return new FakePaymentProvider("FAKE_MOBILE_MONEY", PaymentMethod.MOBILE_MONEY);
    }

    @Bean
    FakePaymentProvider fakeCard() {
        return new FakePaymentProvider("FAKE_CARD", PaymentMethod.CARD);
    }

    @Bean
    FakePaymentProvider fakeBank() {
        return new FakePaymentProvider("FAKE_BANK", PaymentMethod.BANK_TRANSFER);
    }
}
