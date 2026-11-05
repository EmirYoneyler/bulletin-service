package com.fonbulteni.bulletinservice.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceJobScheduleTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Test
    void runsAt1930IstanbulOnWeekdays() throws Exception {
        Scheduled scheduled = DailyPriceJob.class.getMethod("scheduledRun").getAnnotation(Scheduled.class);
        String configured = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("jobs.daily-price.cron");

        assertThat(scheduled.zone()).isEqualTo("Europe/Istanbul");
        assertThat(scheduled.cron()).isEqualTo("${jobs.daily-price.cron:0 30 19 * * MON-FRI}");
        assertThat(configured).isEqualTo("0 30 19 * * MON-FRI");

        CronExpression cron = CronExpression.parse(configured);
        // Wednesday 19:00 → same day 19:30
        assertThat(cron.next(ZonedDateTime.of(2026, 11, 4, 19, 0, 0, 0, ISTANBUL)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 4, 19, 30, 0, 0, ISTANBUL));
        // Friday after the run → Monday 19:30, nothing on the weekend
        assertThat(cron.next(ZonedDateTime.of(2026, 11, 6, 20, 0, 0, 0, ISTANBUL)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 9, 19, 30, 0, 0, ISTANBUL));
    }
}
