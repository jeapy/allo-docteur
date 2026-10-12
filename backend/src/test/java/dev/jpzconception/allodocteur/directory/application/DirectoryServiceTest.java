package dev.jpzconception.allodocteur.directory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DirectoryServiceTest {

    @Test
    void defaultSortIsRatingThenIdTieBreaker() {
        var sort = DirectoryService.sortFor(null);
        assertThat(sort.stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("ratingAvg:DESC", "ratingCount:DESC", "id:ASC");
    }

    @Test
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> DirectoryService.sortFor("passwordHash")).isInstanceOf(ApiException.class);
    }

    @Test
    void dateOverridesDayOfWeek() {
        // 2026-10-12 est un lundi
        var c = new SearchCriteria(null, null, null, null, null, null, null, null, null, 5, LocalDate.of(2026, 10, 12), null);
        assertThat(c.effectiveDay()).isEqualTo(1);
        var d = new SearchCriteria(null, null, null, null, null, null, null, null, null, 5, null, null);
        assertThat(d.effectiveDay()).isEqualTo(5);
    }
}
