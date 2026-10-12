package dev.jpzconception.allodocteur.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Enveloppe de pagination stable (indépendante de la sérialisation interne de Spring Data). */
public record PageResponse<T>(List<T> content, long totalElements, int number, int size) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
