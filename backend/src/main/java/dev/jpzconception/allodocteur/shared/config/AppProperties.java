package dev.jpzconception.allodocteur.shared.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        Jwt jwt,
        Crypto crypto,
        Cors cors,
        Cookie cookie,
        Admin admin,
        RateLimit rateLimit) {

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl, Duration mfaTtl) {}

    /** Clé AES-256 encodée en Base64 (32 octets). */
    public record Crypto(String key) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Cookie(boolean secure) {}

    public record Admin(String email, String password) {}

    public record RateLimit(int authPerMinute, int publicPerMinute) {}
}
