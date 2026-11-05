package com.fonbulteni.bulletinservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Starts a minimal Spring application that loads the real application*.properties files,
 * to prove the startup behaviour without needing a database.
 */
class JwtSecretStartupTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtOnlyConfig {
    }

    private static SpringApplicationBuilder app() {
        return new SpringApplicationBuilder(JwtOnlyConfig.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false);
    }

    /** A real JWT_SECRET in the environment wins via relaxed binding, so these tests need it unset. */
    private static void assumeJwtSecretNotInEnvironment() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "JWT_SECRET is set in this environment");
    }

    @Test
    void refusesToStartWithoutJwtSecret() {
        assumeJwtSecretNotInEnvironment();

        Throwable failure = catchThrowable(() -> app().run().close());

        assertThat(failure).isNotNull();
        assertThat(failure).rootCause()
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesToStartWithExplicitlyEmptySecret() {
        Throwable failure = catchThrowable(() -> app().run("--jwt.secret=").close());

        assertThat(failure).rootCause().isInstanceOf(InvalidJwtSecretException.class);
    }

    @Test
    void devProfileStartsWithFixedDevelopmentKey() {
        assumeJwtSecretNotInEnvironment();

        try (ConfigurableApplicationContext context = app().profiles("dev").run()) {
            assertThat(context.getBean(JwtProperties.class).secret()).startsWith("dev-only-");
        }
    }

    @Test
    void startsWhenJwtSecretIsProvided() {
        assumeJwtSecretNotInEnvironment();
        String secret = "a-real-looking-secret-that-is-long-enough-123";

        try (ConfigurableApplicationContext context = app().run("--JWT_SECRET=" + secret)) {
            assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo(secret);
        }
    }

    @Test
    void failureAnalyzerExplainsTheFix() {
        Throwable failure = catchThrowable(() -> app().run("--jwt.secret=").close());

        FailureAnalysis analysis = new InvalidJwtSecretFailureAnalyzer().analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("JWT_SECRET ortam değişkeni boş");
        assertThat(analysis.getAction())
                .contains("openssl rand -base64 48")
                .contains("SPRING_PROFILES_ACTIVE=dev");
    }

    @Test
    void failureAnalyzerIsRegistered() throws IOException {
        var factories = PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories"));
        assertThat(factories.getProperty("org.springframework.boot.diagnostics.FailureAnalyzer"))
                .contains(InvalidJwtSecretFailureAnalyzer.class.getName());
    }
}
