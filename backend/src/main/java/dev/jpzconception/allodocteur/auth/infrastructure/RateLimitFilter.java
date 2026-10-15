package dev.jpzconception.allodocteur.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite par IP : les POST sur /api/v1/auth/** (anti brute-force) et tous les appels sur /api/v1/public/**
 * (endpoints sans authentification : candidature professionnelle, vérification du numéro d'ordre).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String PUBLIC_PREFIX = "/api/v1/public/";

    private final RateLimiter authLimiter;
    private final RateLimiter publicLimiter;

    public RateLimitFilter(RateLimiter authLimiter, RateLimiter publicLimiter) {
        this.authLimiter = authLimiter;
        this.publicLimiter = publicLimiter;
    }

    private RateLimiter limiterFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith(PUBLIC_PREFIX) && !"OPTIONS".equals(request.getMethod())) return publicLimiter;
        if ("POST".equals(request.getMethod()) && uri.startsWith(AUTH_PREFIX)) return authLimiter;
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limiterFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (limiterFor(request).tryAcquire(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\","
                + "\"detail\":\"Trop de tentatives. Réessayez dans une minute.\",\"code\":\"RATE_LIMITED\"}");
    }
}
