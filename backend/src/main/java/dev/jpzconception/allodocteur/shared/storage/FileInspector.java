package dev.jpzconception.allodocteur.shared.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Contrôles communs aux fichiers téléversés : type réel par signature, nom assaini, empreinte. */
public final class FileInspector {

    private FileInspector() {}

    /** Détection par octets magiques (PDF, JPEG, PNG, WebP) : on ne fait pas confiance au Content-Type du client. */
    public static String detectContentType(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    public static String sanitizeFilename(String name) {
        String base = name == null ? "document" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        if (base.isEmpty()) base = "document";
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
