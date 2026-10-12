package dev.jpzconception.allodocteur.auth.application;

import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.auth.api.AuthDtos.AuthResponse;
import dev.jpzconception.allodocteur.auth.api.AuthDtos.RegisterRequest;
import dev.jpzconception.allodocteur.auth.api.AuthDtos.TokenResponse;
import dev.jpzconception.allodocteur.auth.domain.PasswordResetToken;
import dev.jpzconception.allodocteur.auth.domain.RecoveryCode;
import dev.jpzconception.allodocteur.auth.domain.RefreshToken;
import dev.jpzconception.allodocteur.auth.infrastructure.PasswordResetTokenRepository;
import dev.jpzconception.allodocteur.auth.infrastructure.RecoveryCodeRepository;
import dev.jpzconception.allodocteur.auth.infrastructure.RefreshTokenRepository;
import dev.jpzconception.allodocteur.notification.application.EmailSender;
import dev.jpzconception.allodocteur.shared.config.AppProperties;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.user.api.UserMapper;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage d'authentification. noRollbackFor : les compteurs d'échecs doivent être
 * persistés même quand on lève une ApiException.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final RecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TotpService totp;
    private final EmailSender email;
    private final AuditService audit;
    private final UserMapper mapper;
    private final AppProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, RecoveryCodeRepository recoveryCodes,
                       PasswordEncoder encoder, JwtService jwt, TotpService totp, EmailSender email,
                       AuditService audit, UserMapper mapper, AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.recoveryCodes = recoveryCodes;
        this.encoder = encoder;
        this.jwt = jwt;
        this.totp = totp;
        this.email = email;
        this.audit = audit;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        // Hash factice pour que "e-mail inconnu" coûte autant de temps que "mauvais mot de passe".
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    /** Résultat : corps de réponse + refresh token brut (jamais dans le corps pour le web). */
    public record AuthResult(AuthResponse body, String refreshToken) {}

    public record RefreshResult(TokenResponse body, String refreshToken) {}

    // ---------------------------------------------------------------- inscription

    public User register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.badRequest("INVALID_ROLE", "Rôle non autorisé à l'inscription.");
        }
        String mail = normalize(req.email());
        if (users.existsByEmailIgnoreCase(mail)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Cette adresse e-mail est déjà utilisée.");
        }
        if (req.phone() != null && users.existsByPhone(req.phone())) {
            throw ApiException.conflict("PHONE_ALREADY_USED", "Ce numéro de téléphone est déjà utilisé.");
        }
        User user = new User();
        user.setEmail(mail);
        user.setPhone(req.phone());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setRole(req.role());
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId().toString(), user.getRole().name());
        return user;
    }

    // ---------------------------------------------------------------- connexion

    public AuthResult login(String rawEmail, String password, String ip, boolean mobile) {
        Instant now = Instant.now(clock);
        User user = users.findByEmailIgnoreCase(normalize(rawEmail)).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            audit.record(null, "LOGIN_FAILED", "USER", null, ip, "unknown email");
            throw invalidCredentials();
        }
        ensureCanAuthenticate(user, now);
        if (!encoder.matches(password, user.getPasswordHash())) {
            registerFailure(user, now, ip);
            throw invalidCredentials();
        }
        if (user.isTwoFactorEnabled()) {
            return new AuthResult(new AuthResponse("MFA_REQUIRED", null, null, null, jwt.createMfaToken(user), null), null);
        }
        return completeLogin(user, now, ip, mobile);
    }

    public AuthResult verifyMfa(String mfaToken, String code, String ip, boolean mobile) {
        Instant now = Instant.now(clock);
        UUID userId = jwt.parseMfaToken(mfaToken);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_MFA_TOKEN", "Jeton de vérification invalide."));
        ensureCanAuthenticate(user, now);
        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) {
            throw ApiException.unauthorized("INVALID_MFA_TOKEN", "Jeton de vérification invalide.");
        }
        boolean ok = totp.verify(user.getTwoFactorSecret(), code.trim(), now) || consumeRecoveryCode(user, code, now);
        if (!ok) {
            registerFailure(user, now, ip);
            throw ApiException.unauthorized("INVALID_CODE", "Code de vérification invalide.");
        }
        return completeLogin(user, now, ip, mobile);
    }

    private AuthResult completeLogin(User user, Instant now, String ip, boolean mobile) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        String refresh = issueRefreshToken(user, UUID.randomUUID(), now);
        audit.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId().toString(), ip, null);
        AuthResponse body = new AuthResponse("AUTHENTICATED", jwt.createAccessToken(user), jwt.accessTtlSeconds(),
                mapper.toDto(user), null, mobile ? refresh : null);
        return new AuthResult(body, refresh);
    }

    private void ensureCanAuthenticate(User user, Instant now) {
        if (user.isLocked(now)) {
            throw new ApiException(org.springframework.http.HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Compte temporairement verrouillé suite à trop de tentatives.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Ce compte est suspendu.");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw ApiException.forbidden("ACCOUNT_PENDING", "Ce compte est en attente de validation.");
        }
    }

    private void registerFailure(User user, Instant now, String ip) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plus(LOCK_DURATION));
            user.setFailedLoginAttempts(0);
        }
        audit.record(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(), ip, null);
    }

    // ---------------------------------------------------------------- refresh / logout

    public RefreshResult refresh(String rawToken, boolean mobile) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expirée.");
        }
        Instant now = Instant.now(clock);
        RefreshToken stored = refreshTokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expirée."));
        if (stored.getRevokedAt() != null) {
            // Réutilisation d'un jeton déjà tourné : on suspecte un vol et on invalide toute la famille.
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            audit.record(stored.getUserId(), "REFRESH_TOKEN_REUSE", "USER", stored.getUserId().toString(), null);
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Session invalide, veuillez vous reconnecter.");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expirée.");
        }
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expirée."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Ce compte n'est plus actif.");
        }
        stored.setRevokedAt(now);
        String next = issueRefreshToken(user, stored.getFamilyId(), now);
        return new RefreshResult(
                new TokenResponse(jwt.createAccessToken(user), jwt.accessTtlSeconds(), mobile ? next : null), next);
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    private String issueRefreshToken(User user, UUID familyId, Instant now) {
        String raw = TokenHasher.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(TokenHasher.sha256(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(props.jwt().refreshTtl()));
        refreshTokens.save(token);
        return raw;
    }

    // ---------------------------------------------------------------- mot de passe

    /** Répond toujours de la même façon pour ne pas révéler l'existence d'un compte. */
    public void forgotPassword(String rawEmail) {
        users.findByEmailIgnoreCase(normalize(rawEmail)).ifPresent(user -> {
            Instant now = Instant.now(clock);
            String raw = TokenHasher.randomToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(TokenHasher.sha256(raw));
            token.setCreatedAt(now);
            token.setExpiresAt(now.plus(RESET_TTL));
            resetTokens.save(token);
            email.send(user.getEmail(), "Réinitialisation de votre mot de passe",
                    "Pour choisir un nouveau mot de passe, ouvrez : "
                            + props.frontendUrl() + "/reset-password?token=" + raw
                            + "\nCe lien expire dans 1 heure.");
            audit.record(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString(), null);
        });
    }

    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = resetTokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "Lien invalide ou expiré."));
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "Lien invalide ou expiré."));
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        token.setUsedAt(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record(user.getId(), "PASSWORD_RESET_DONE", "USER", user.getId().toString(), null);
    }

    // ---------------------------------------------------------------- 2FA (TOTP)

    @Transactional(readOnly = true)
    public User currentUser(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Non authentifié."));
    }

    public String[] beginTotpSetup(UUID userId) {
        User user = currentUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw ApiException.conflict("MFA_ALREADY_ENABLED", "La double authentification est déjà activée.");
        }
        String secret = totp.generateSecret();
        user.setTwoFactorSecret(secret);
        return new String[] {secret, totp.otpauthUri("Allo Docteur", user.getEmail(), secret)};
    }

    public List<String> enableTotp(UUID userId, String code) {
        User user = currentUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw ApiException.conflict("MFA_ALREADY_ENABLED", "La double authentification est déjà activée.");
        }
        if (user.getTwoFactorSecret() == null) {
            throw ApiException.badRequest("MFA_SETUP_REQUIRED", "Démarrez d'abord la configuration 2FA.");
        }
        if (!totp.verify(user.getTwoFactorSecret(), code.trim(), Instant.now(clock))) {
            throw ApiException.badRequest("INVALID_CODE", "Code de vérification invalide.");
        }
        user.setTwoFactorEnabled(true);
        List<String> plain = generateRecoveryCodes(user);
        audit.record(userId, "MFA_ENABLED", "USER", userId.toString(), null);
        return plain;
    }

    public void disableTotp(UUID userId, String password, String code) {
        User user = currentUser(userId);
        Instant now = Instant.now(clock);
        if (!user.isTwoFactorEnabled()) {
            throw ApiException.badRequest("MFA_NOT_ENABLED", "La double authentification n'est pas activée.");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Mot de passe incorrect.");
        }
        if (!totp.verify(user.getTwoFactorSecret(), code.trim(), now)) {
            throw ApiException.unauthorized("INVALID_CODE", "Code de vérification invalide.");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        recoveryCodes.deleteAllForUser(userId);
        audit.record(userId, "MFA_DISABLED", "USER", userId.toString(), null);
    }

    private List<String> generateRecoveryCodes(User user) {
        recoveryCodes.deleteAllForUser(user.getId());
        List<String> plain = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (int i = 0; i < 8; i++) {
            String code = randomRecoveryChunk() + "-" + randomRecoveryChunk();
            plain.add(code);
            RecoveryCode entity = new RecoveryCode();
            entity.setUserId(user.getId());
            entity.setCodeHash(TokenHasher.sha256(code));
            entity.setCreatedAt(now);
            recoveryCodes.save(entity);
        }
        return plain;
    }

    private boolean consumeRecoveryCode(User user, String rawCode, Instant now) {
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), TokenHasher.sha256(rawCode.trim().toLowerCase()))
                .map(c -> {
                    c.setUsedAt(now);
                    return true;
                }).orElse(false);
    }

    private static String randomRecoveryChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        return sb.toString();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Identifiants invalides.");
    }
}
