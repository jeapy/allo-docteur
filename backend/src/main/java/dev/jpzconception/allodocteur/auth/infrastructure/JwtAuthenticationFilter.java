package dev.jpzconception.allodocteur.auth.infrastructure;

import dev.jpzconception.allodocteur.auth.application.JwtService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authentifie la requête à partir de l'en-tête "Authorization: Bearer <jwt d'accès>". */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Seuls ces chemins restent accessibles à un administrateur qui doit encore activer la 2FA. */
    static final String AUTH_PREFIX = "/api/v1/auth/";

    private final JwtService jwt;
    private final boolean requireAdminMfa;

    public JwtAuthenticationFilter(JwtService jwt, boolean requireAdminMfa) {
        this.jwt = jwt;
        this.requireAdminMfa = requireAdminMfa;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var user = jwt.parseAccessToken(header.substring(7));
            if (user.isPresent()) {
                if (requireAdminMfa && user.get().mustEnrollTwoFactor() && !isAuthPath(request)) {
                    rejectUntilEnrolled(response);
                    return;
                }
                authenticate(user.get());
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(AUTH_PREFIX);
    }

    private static void rejectUntilEnrolled(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"code\":\"MFA_ENROLLMENT_REQUIRED\","
                + "\"detail\":\"Activez la double authentification pour accéder à l'administration.\"}");
    }

    private void authenticate(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        if (user.adminLevel() != null) {
            authorities.add(new SimpleGrantedAuthority("LEVEL_" + user.adminLevel().name()));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
}
