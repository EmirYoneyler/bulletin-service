package com.fonbulteni.bulletinservice.bulletin.llm;

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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class, LlmConfig.class);

    @Test
    void defaultIsTheTemplateWriterWithNoKey() {
        runner.run(context -> {
            LlmClient client = context.getBean(LlmClient.class);
            assertThat(client).isInstanceOf(TemplateLlmClient.class);
            assertThat(client.name()).isEqualTo("template");
            assertThat(context.getBean(TemplateLlmClient.class)).isSameAs(client);
        });
    }

    @Test
    void ollamaUsesLocalDefaults() {
        runner.withPropertyValues("llm.provider=ollama").run(context -> {
            OpenAiCompatibleLlmClient client = (OpenAiCompatibleLlmClient) context.getBean(LlmClient.class);
            assertThat(client.name()).isEqualTo("ollama");
            assertThat(client.baseUrl()).isEqualTo("http://ollama:11434/v1");
            assertThat(client.model()).isEqualTo("qwen2.5:7b-instruct");
            assertThat(client.timeout()).isEqualTo(Duration.ofSeconds(120));
            // the template writer stays available as the fallback
            assertThat(context.getBean(TemplateLlmClient.class)).isNotNull();
        });
    }

    @Test
    void remoteProviderWithDocumentedExampleSettings(CapturedOutput output) {
        runner.withPropertyValues("llm.provider=openai-compatible",
                        "llm.base-url=https://api.groq.com/openai/v1/", "llm.model=some-model", "llm.api-key=very-secret")
                .run(context -> {
                    OpenAiCompatibleLlmClient client = (OpenAiCompatibleLlmClient) context.getBean(LlmClient.class);
                    assertThat(client.name()).isEqualTo("openai-compatible");
                    assertThat(client.baseUrl()).isEqualTo("https://api.groq.com/openai/v1"); // trailing slash dropped
                    assertThat(client.timeout()).isEqualTo(Duration.ofSeconds(30));
                });
        assertThat(output).contains("API key set").doesNotContain("very-secret");
    }

    @Test
    void remoteProviderWithoutAddressRefusesToStart() {
        runner.withPropertyValues("llm.provider=openai-compatible", "llm.model=some-model").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("LLM_BASE_URL ve LLM_MODEL");
        });
    }

    @Test
    void timeoutCanBeOverridden() {
        runner.withPropertyValues("llm.provider=ollama", "llm.timeout=45s").run(context ->
                assertThat(((OpenAiCompatibleLlmClient) context.getBean(LlmClient.class)).timeout())
                        .isEqualTo(Duration.ofSeconds(45)));
    }
}
