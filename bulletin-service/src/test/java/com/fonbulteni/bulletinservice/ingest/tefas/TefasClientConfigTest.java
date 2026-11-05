package com.fonbulteni.bulletinservice.ingest.tefas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TefasClientConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TefasProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class, TefasClientConfig.class);

    @Test
    void defaultsFollowPolitenessRules() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TefasClient.class);
            TefasProperties properties = context.getBean(TefasProperties.class);
            assertThat(properties.baseUrl()).isEqualTo("https://www.tefas.gov.tr/api/funds");
            assertThat(properties.userAgent())
                    .isEqualTo("fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)");
            assertThat(properties.requestInterval()).isGreaterThanOrEqualTo(Duration.ofMillis(1500));
            assertThat(properties.maxAttempts()).isEqualTo(3);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void settingsCanBeOverridden() {
        runner.withPropertyValues("tefas.request-interval=3s", "tefas.max-attempts=5")
                .run(context -> {
                    TefasProperties properties = context.getBean(TefasProperties.class);
                    assertThat(properties.requestInterval()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.maxAttempts()).isEqualTo(5);
                });
    }

    @Test
    void rejectsZeroAttempts() {
        runner.withPropertyValues("tefas.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
