package com.fonbulteni.bulletinservice.ingest.evds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class EvdsClientConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvdsProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class, EvdsClientConfig.class);

    @Test
    void withoutKeyTheBeanExistsButIsDisabledAndWarns(CapturedOutput output) {
        runner.run(context -> {
            EvdsClient client = context.getBean(EvdsClient.class);
            assertThat(client.isEnabled()).isFalse();
        });
        assertThat(output).contains("EVDS_API_KEY is not set");
    }

    @Test
    void keyEnablesTheClient() {
        runner.withPropertyValues("evds.api-key=abc").run(context ->
                assertThat(context.getBean(EvdsClient.class).isEnabled()).isTrue());
    }

    @Test
    void verifiedSeriesCodesAreTheDefaults() {
        runner.run(context -> {
            EvdsProperties properties = context.getBean(EvdsProperties.class);
            assertThat(properties.baseUrl()).isEqualTo("https://evds3.tcmb.gov.tr/igmevdsms-dis");
            assertThat(properties.series().all()).containsExactly(
                    "TP.TUKFIY2025.GENEL", "TP.DK.USD.A.YTL", "TP.MK.KUL.YTL", "TP.TRY.MT06");
        });
    }
}
