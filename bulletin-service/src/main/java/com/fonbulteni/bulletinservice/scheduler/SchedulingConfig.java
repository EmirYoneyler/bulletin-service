package com.fonbulteni.bulletinservice.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on cron jobs. Off in tests ({@code jobs.scheduling.enabled=false}); admin triggers still work. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "jobs.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
