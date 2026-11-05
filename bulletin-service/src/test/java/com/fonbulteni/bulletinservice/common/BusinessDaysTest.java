package com.fonbulteni.bulletinservice.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessDaysTest {

    @ParameterizedTest(name = "{0} with {1} weekday(s) starts {2}")
    @CsvSource({
            "2026-11-04, 5, 2026-10-29", // Wed → Thu of previous week
            "2026-11-06, 5, 2026-11-02", // Fri → Mon of same week
            "2026-11-02, 5, 2026-10-27", // Mon → Tue of previous week
            "2026-11-07, 5, 2026-11-02", // Sat → counts back from Fri
            "2026-11-08, 5, 2026-11-02", // Sun → counts back from Fri
            "2026-11-04, 1, 2026-11-04", // weekday itself
            "2026-11-08, 1, 2026-11-06"  // weekend → previous Fri
    })
    void startOfLastWeekdays(LocalDate end, int count, LocalDate expected) {
        assertThat(BusinessDays.startOfLastWeekdays(end, count)).isEqualTo(expected);
    }

    @Test
    void weekendIsNotAWeekday() {
        assertThat(BusinessDays.isWeekday(LocalDate.of(2026, 11, 7))).isFalse();
        assertThat(BusinessDays.isWeekday(LocalDate.of(2026, 11, 6))).isTrue();
    }

    @Test
    void rejectsNonPositiveCount() {
        assertThatThrownBy(() -> BusinessDays.startOfLastWeekdays(LocalDate.of(2026, 11, 4), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
