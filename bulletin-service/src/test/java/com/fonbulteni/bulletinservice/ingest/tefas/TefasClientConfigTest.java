package com.fonbulteni.bulletinservice.ingest.tefas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Regression: TEFAS's proxy answers a streamed (chunked) body with 500 "Proxy request failed".
     * MockRestServiceServer bypasses the request factory, so this checks the real wire format
     * against a loopback server (no external network).
     */
    @Test
    void sendsBodyWithContentLengthNotChunked() throws Exception {
        AtomicReference<Headers> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        byte[] emptyDay = new ClassPathResource("fixtures/tefas/history-empty-day.json").getContentAsByteArray();
        server.createContext("/api/funds/fonGnlBlgSiraliGetir", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, emptyDay.length);
            exchange.getResponseBody().write(emptyDay);
            exchange.close();
        });
        server.start();
        try {
            runner.withPropertyValues("tefas.base-url=http://localhost:" + server.getAddress().getPort() + "/api/funds")
                    .run(context -> context.getBean(TefasClient.class)
                            .fetchHistory("AAV", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)));
        } finally {
            server.stop(0);
        }

        assertThat(received.get()).isNotNull();
        assertThat(received.get().getFirst("Content-Length")).isNotNull();
        assertThat(received.get().getFirst("Transfer-Encoding")).isNull();
        assertThat(received.get().getFirst("User-Agent"))
                .isEqualTo("fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)");
    }

    @Test
    void rejectsZeroAttempts() {
        runner.withPropertyValues("tefas.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
