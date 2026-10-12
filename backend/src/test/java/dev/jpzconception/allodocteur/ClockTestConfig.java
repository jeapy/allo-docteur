package dev.jpzconception.allodocteur;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** À importer (@Import) dans les tests qui doivent déplacer l'horloge de l'application. */
@TestConfiguration
public class ClockTestConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
