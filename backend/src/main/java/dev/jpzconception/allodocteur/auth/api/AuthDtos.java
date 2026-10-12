package dev.jpzconception.allodocteur.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jpzconception.allodocteur.user.api.UserDto;
import dev.jpzconception.allodocteur.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Format E.164 attendu (ex. +2250700000000)") String phone,
            @NotNull Role role) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank @Size(max = 32) String code) {}

    public record RefreshRequest(String refreshToken) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    public record EnableTotpRequest(@NotBlank String code) {}

    public record DisableTotpRequest(@NotBlank String password, @NotBlank String code) {}

    public record TotpSetupResponse(String secret, String otpauthUri) {}

    public record RecoveryCodesResponse(List<String> recoveryCodes) {}

    /** status = AUTHENTICATED (jetons + user) ou MFA_REQUIRED (mfaToken). refreshToken seulement pour le mobile. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(
            String status,
            String accessToken,
            Long expiresIn,
            UserDto user,
            String mfaToken,
            String refreshToken) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(String accessToken, long expiresIn, String refreshToken) {}
}
