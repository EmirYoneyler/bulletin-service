package com.fonbulteni.bulletinservice.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class FundTest {

    private TimeZone originalDefault;

    @BeforeEach
    void simulateContainerTimezone() {
        originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    @AfterEach
    void restoreTimezone() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    void createdAtIsIstanbulWallClockEvenWhenJvmDefaultIsUtc() {
        Fund fund = Fund.builder().name("Fon").build();

        fund.prePersist();

        LocalDateTime istanbulNow = LocalDateTime.now(ZoneId.of("Europe/Istanbul"));
        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(Duration.between(fund.getCreatedAt(), istanbulNow).abs()).isLessThan(Duration.ofSeconds(5));
        // Turkey has been UTC+3 all year since 2016, so a UTC value would be ~3 hours off.
        assertThat(Duration.between(utcNow, fund.getCreatedAt())).isGreaterThan(Duration.ofHours(2));
    }
}
