package dev.jpzconception.allodocteur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlloDocteurApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlloDocteurApplication.class, args);
    }
}
