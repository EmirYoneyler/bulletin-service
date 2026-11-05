package com.fonbulteni.bulletinservice.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Single source of "now" for the application, so time-dependent code is testable. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    public static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Bean
    public Clock clock() {
        return Clock.system(ISTANBUL);
    }
}
