package dev.jpzconception.allodocteur.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Adresse IP de la requête en cours (déjà corrigée des en-têtes de proxy par forward-headers-strategy). */
public final class ClientIp {

    private ClientIp() {}

    /** Renvoie null hors requête HTTP (tâches planifiées, tests unitaires). */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
