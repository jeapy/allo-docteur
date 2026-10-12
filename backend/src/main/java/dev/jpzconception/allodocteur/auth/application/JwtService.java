package dev.jpzconception.allodocteur.auth.application;

import dev.jpzconception.allodocteur.shared.config.AppProperties;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** JWT HS256 : jeton d'accès (courte durée) et jeton intermédiaire "mfa" (étape 2FA). */
@Service
public class JwtService {

    private static final String TYPE = "typ";
    private static final String ACCESS = "access";
    private static final String MFA = "mfa";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration mfaTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET doit contenir au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = props.jwt().issuer();
        this.accessTtl = props.jwt().accessTtl();
        this.mfaTtl = props.jwt().mfaTtl();
        this.clock = clock;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now(clock);
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(TYPE, ACCESS)
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)));
        if (user.getAdminLevel() != null) {
            builder.claim("level", user.getAdminLevel().name());
        }
        return builder.signWith(key).compact();
    }

    public String createMfaToken(User user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(TYPE, MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(mfaTtl)))
                .signWith(key)
                .compact();
    }

    /** Vide si le jeton est invalide, expiré ou n'est pas un jeton d'accès. */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            Claims claims = parse(token);
            if (!ACCESS.equals(claims.get(TYPE, String.class))) return Optional.empty();
            String level = claims.get("level", String.class);
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.get("role", String.class)),
                    level == null ? null : AdminLevel.valueOf(level)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public UUID parseMfaToken(String token) {
        try {
            Claims claims = parse(token);
            if (!MFA.equals(claims.get(TYPE, String.class))) {
                throw ApiException.unauthorized("INVALID_MFA_TOKEN", "Jeton de vérification invalide.");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_MFA_TOKEN", "Jeton de vérification invalide ou expiré.");
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
