package dev.jpzconception.allodocteur.professional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.RuleRequest;
import dev.jpzconception.allodocteur.professional.application.AvailabilityService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityRulesTest {

    private static RuleRequest rule(int day, String start, String end) {
        return new RuleRequest(day, LocalTime.parse(start), LocalTime.parse(end), true, false);
    }

    @Test
    void acceptsAdjacentAndDifferentDays() {
        assertThatCode(() -> AvailabilityService.validateRules(List.of(
                rule(1, "08:00", "12:00"), rule(1, "12:00", "16:00"), rule(2, "08:00", "12:00")))).doesNotThrowAnyException();
    }

    @Test
    void rejectsOverlapOnSameDay() {
        assertThatThrownBy(() -> AvailabilityService.validateRules(List.of(rule(3, "08:00", "12:00"), rule(3, "11:00", "14:00"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("OVERLAPPING_RULES"));
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> AvailabilityService.validateRules(List.of(rule(1, "12:00", "08:00"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_TIME_RANGE"));
    }

    @Test
    void rejectsRuleWithoutMode() {
        RuleRequest none = new RuleRequest(1, LocalTime.of(8, 0), LocalTime.of(9, 0), false, false);
        assertThatThrownBy(() -> AvailabilityService.validateRules(List.of(none))).isInstanceOf(ApiException.class);
    }
}
