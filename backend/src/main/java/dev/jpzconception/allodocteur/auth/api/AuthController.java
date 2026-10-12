package dev.jpzconception.allodocteur.auth.api;

import dev.jpzconception.allodocteur.auth.api.AuthDtos.*;
import dev.jpzconception.allodocteur.auth.application.AuthService;
import dev.jpzconception.allodocteur.auth.application.AuthService.AuthResult;
import dev.jpzconception.allodocteur.auth.application.AuthService.RefreshResult;
import dev.jpzconception.allodocteur.shared.config.AppProperties;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.user.api.UserDto;
import dev.jpzconception.allodocteur.user.api.UserMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web (admin) : le refresh token voyage dans un cookie httpOnly.
 * Mobile (en-tête X-Client-Type: mobile) : il est renvoyé dans le corps et attendu dans le corps.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification")
public class AuthController {

    static final String COOKIE = "refresh_token";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService service;
    private final UserMapper mapper;
    private final AppProperties props;

    public AuthController(AuthService service, UserMapper mapper, AppProperties props) {
        this.service = service;
        this.mapper = mapper;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request) {
        return mapper.toDto(service.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        boolean mobile = isMobile(http);
        AuthResult result = service.login(request.email(), request.password(), http.getRemoteAddr(), mobile);
        return withCookie(result, mobile);
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http) {
        boolean mobile = isMobile(http);
        return withCookie(service.verifyMfa(request.mfaToken(), request.code(), http.getRemoteAddr(), mobile), mobile);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest body, HttpServletRequest http) {
        boolean mobile = isMobile(http);
        String raw = mobile && body != null ? body.refreshToken() : cookieValue(http);
        RefreshResult result = service.refresh(raw, mobile);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (!mobile) builder.header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), props.jwt().refreshTtl()).toString());
        return builder.body(result.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest body, HttpServletRequest http) {
        boolean mobile = isMobile(http);
        service.logout(mobile && body != null ? body.refreshToken() : cookieValue(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return mapper.toDto(service.currentUser(principal.id()));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        service.forgotPassword(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/2fa/setup")
    public TotpSetupResponse setupTotp(@AuthenticationPrincipal AuthenticatedUser principal) {
        String[] r = service.beginTotpSetup(principal.id());
        return new TotpSetupResponse(r[0], r[1]);
    }

    @PostMapping("/2fa/enable")
    public RecoveryCodesResponse enableTotp(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody EnableTotpRequest request) {
        return new RecoveryCodesResponse(service.enableTotp(principal.id(), request.code()));
    }

    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTotp(@AuthenticationPrincipal AuthenticatedUser principal,
                            @Valid @RequestBody DisableTotpRequest request) {
        service.disableTotp(principal.id(), request.password(), request.code());
    }

    private ResponseEntity<AuthResponse> withCookie(AuthResult result, boolean mobile) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (!mobile && result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), props.jwt().refreshTtl()).toString());
        }
        return builder.body(result.body());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static boolean isMobile(HttpServletRequest request) {
        return "mobile".equalsIgnoreCase(request.getHeader("X-Client-Type"));
    }
}
