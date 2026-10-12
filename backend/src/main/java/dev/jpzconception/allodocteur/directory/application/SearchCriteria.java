package dev.jpzconception.allodocteur.directory.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Critères de la recherche avancée (cahier des charges §6.3). Tous optionnels. */
public record SearchCriteria(
        String q,
        Integer typeId,
        List<Integer> specialtyIds,
        Integer cityId,
        Integer districtId,
        BigDecimal minFee,
        BigDecimal maxFee,
        Boolean teleconsultation,
        Boolean homeVisit,
        Integer dayOfWeek,
        LocalDate date,
        BigDecimal minRating) {

    /** Jour ISO (1-7) demandé : déduit de la date si elle est fournie. */
    public Integer effectiveDay() {
        return date != null ? date.getDayOfWeek().getValue() : dayOfWeek;
    }
}
