package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** OpenAI chat-completions format, no network. */
class OpenAiCompatibleLlmClientTest {

    private static final String BASE = "https://llm.test/v1";
    private static final String URL = BASE + "/chat/completions";
    private static final String OK_BODY = """
            {"id":"x","object":"chat.completion","model":"qwen2.5:7b-instruct",
             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"summary\\":\\"Hafta {{portfolio.return.1w}}.\\"}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":812,"completion_tokens":143,"total_tokens":955}}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiCompatibleLlmClient client(String apiKey, boolean jsonFormat) {
        LlmProperties properties = new LlmProperties(LlmProperties.Provider.OPENAI_COMPATIBLE, BASE, "test-model",
                apiKey, Duration.ofSeconds(30), jsonFormat);
        return new OpenAiCompatibleLlmClient(builder.build(), mapper, "openai-compatible", properties);
    }

    private static LlmRequest request() {
        return new LlmRequest("Sen bir bülten editörüsün.", "Bülteni yaz.", true, 600, 0.2);
    }

    @Test
    void sendsChatCompletionAndReadsTextTokensAndModel() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Sen bir bülten editörüsün."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.max_tokens").value(600))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        LlmResponse response = client("secret-key", true).complete(request());

        server.verify();
        assertThat(response.text()).isEqualTo("{\"summary\":\"Hafta {{portfolio.return.1w}}.\"}");
        assertThat(response.inputTokens()).isEqualTo(812);
        assertThat(response.outputTokens()).isEqualTo(143);
        assertThat(response.model()).isEqualTo("qwen2.5:7b-instruct");
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void withoutKeyNoAuthorizationHeaderLikeOllama() {
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        client("  ", true).complete(request());

        server.verify();
    }

    @Test
    void responseFormatCanBeTurnedOff() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        client(null, false).complete(request());

        server.verify();
    }

    @Test
    void rateLimitIs429Kind() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"message\":\"Rate limit reached\"}}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("k", true).complete(request()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("429")
                .extracting("kind").isEqualTo(LlmException.Kind.RATE_LIMITED);
    }

    @Test
    void timeoutIsTimeoutKind() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client("k", true).complete(request()))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("zaman aşımı")
                .extracting("kind").isEqualTo(LlmException.Kind.TIMEOUT);
    }

    @Test
    void serverAndClientErrorsHaveTheirKinds() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        OpenAiCompatibleLlmClient client = client("k", true);

        assertThatThrownBy(() -> client.complete(request()))
                .extracting("kind").isEqualTo(LlmException.Kind.UNAVAILABLE);
        assertThatThrownBy(() -> client.complete(request()))
                .extracting("kind").isEqualTo(LlmException.Kind.BAD_REQUEST);
    }

    @Test
    void answersWithoutTextAreBadResponses() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  \"}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>hata</html>", MediaType.TEXT_HTML));
        OpenAiCompatibleLlmClient client = client("k", true);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.complete(request()))
                    .extracting("kind").isEqualTo(LlmException.Kind.BAD_RESPONSE);
        }
    }

    @Test
    void missingUsageCountsAsZeroTokens() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"metin\"}}]}", MediaType.APPLICATION_JSON));

        LlmResponse response = client("k", true).complete(request());

        assertThat(response.inputTokens()).isZero();
        assertThat(response.outputTokens()).isZero();
        assertThat(response.model()).isEqualTo("test-model");
    }
}
