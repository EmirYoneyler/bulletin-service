package com.fonbulteni.bulletinservice.ingest.evds;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Data fixtures are hand-made in the documented format (no API key was available, ADR 002); the 403
 * body is a live recording. No network.
 */
class EvdsClientTest {

    private static final String BASE = "https://evds.test/igmevdsms-dis";
    private static final String UA = "fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private EvdsClient client(String apiKey) {
        EvdsProperties properties = new EvdsProperties(BASE, apiKey, UA, Duration.ofSeconds(5), Duration.ofSeconds(30),
                new EvdsProperties.Series("TP.TUKFIY2025.GENEL", "TP.DK.USD.A.YTL", "TP.MK.KUL.YTL", "TP.TRY.MT06", "TP.MK.G.BILESIK"));
        return new EvdsClient(builder.build(), properties, new ObjectMapper());
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("fixtures/evds/" + name);
    }

    private static String url(String code, String from, String to) {
        return BASE + "/series=" + code + "&startDate=" + from + "&endDate=" + to + "&type=json";
    }

    // --- disabled mode ---

    @Test
    void withoutKeyTheClientIsDisabledAndSendsNothing() {
        EvdsClient client = client("  ");

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.fetchSeries("TP.DK.USD.A.YTL", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(EvdsException.Disabled.class)
                .hasMessageContaining("EVDS_API_KEY");
        server.verify(); // no request expected, none made
        assertThat(client(null).isEnabled()).isFalse();
    }

    // --- request shape and parsing ---

    @Test
    void sendsKeyInHeaderAndParsesDailySeries() {
        server.expect(once(), requestTo(url("TP.DK.USD.A.YTL", "02-11-2026", "05-11-2026")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("key", "test-key"))
                .andExpect(header(HttpHeaders.USER_AGENT, UA))
                .andRespond(withSuccess(fixture("usdtry-daily.json"), MediaType.APPLICATION_JSON));

        List<EvdsObservation> values = client("test-key")
                .fetchSeries("TP.DK.USD.A.YTL", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5));

        server.verify();
        // The null value on 4 Nov is "no observation", not zero.
        assertThat(values).extracting(EvdsObservation::date).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));
        assertThat(values.get(1).value()).isEqualTo("10.2500");
        assertThat(values).allSatisfy(v -> assertThat(v.seriesCode()).isEqualTo("TP.DK.USD.A.YTL"));
    }

    @Test
    void monthlyDatesBecomeFirstDayOfMonth() {
        server.expect(requestTo(url("TP.TUKFIY2025.GENEL", "01-08-2026", "05-11-2026")))
                .andRespond(withSuccess(fixture("cpi-monthly.json"), MediaType.APPLICATION_JSON));

        List<EvdsObservation> values = client("k")
                .fetchSeries("TP.TUKFIY2025.GENEL", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 5));

        assertThat(values).extracting(EvdsObservation::date).containsExactly(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        assertThat(values.get(2).value()).isEqualTo("102.01");
    }

    @Test
    void acceptsDecimalComma() {
        server.expect(requestTo(url("TP.TRY.MT06", "23-10-2026", "30-10-2026")))
                .andRespond(withSuccess(fixture("deposit-weekly-comma.json"), MediaType.APPLICATION_JSON));

        assertThat(client("k").fetchSeries("TP.TRY.MT06", LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 30)))
                .extracting(EvdsObservation::value)
                .containsExactly(new java.math.BigDecimal("12.50"), new java.math.BigDecimal("12.75"));
    }

    @Test
    void emptyRangeIsEmptyList() {
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026")))
                .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON));

        assertThat(client("k").fetchSeries("TP.TRY.MT06", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .isEmpty();
    }

    @Test
    void parsesBodyEvenWithoutJsonContentType() {
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026")))
                .andRespond(withSuccess(fixture("empty.json"), MediaType.TEXT_PLAIN));

        assertThat(client("k").fetchSeries("TP.TRY.MT06", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .isEmpty();
    }

    // --- long ranges ---

    @Test
    void longRangeIsSplitIntoThreeYearChunks() {
        server.expect(once(), requestTo(url("TP.DK.USD.A.YTL", "01-01-2020", "31-12-2022")))
                .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(url("TP.DK.USD.A.YTL", "01-01-2023", "31-12-2025")))
                .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(url("TP.DK.USD.A.YTL", "01-01-2026", "05-11-2026")))
                .andRespond(withSuccess(fixture("empty.json"), MediaType.APPLICATION_JSON));

        client("k").fetchSeries("TP.DK.USD.A.YTL", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 11, 5));

        server.verify();
    }

    // --- failures ---

    @Test
    void recordedForbiddenMeansWrongKey() {
        server.expect(requestTo(url("TP.DK.USD.A.YTL", "01-10-2026", "05-11-2026")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body(fixture("error-403-no-key.json"))
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("wrong").fetchSeries("TP.DK.USD.A.YTL",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 5)))
                .isInstanceOf(EvdsException.Rejected.class)
                .hasMessageContaining("EVDS_API_KEY");
    }

    @Test
    void serverErrorsAndTimeoutsAreUnavailable() {
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026"))).andRespond(withServerError());
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026"))).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });
        EvdsClient client = client("k");

        assertThatThrownBy(() -> client.fetchSeries("TP.TRY.MT06", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(EvdsException.Unavailable.class);
        assertThatThrownBy(() -> client.fetchSeries("TP.TRY.MT06", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(EvdsException.Unavailable.class);
    }

    @Test
    void unreadableResponsesAreRejected() {
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026")))
                .andRespond(withSuccess("<html>bakım</html>", MediaType.TEXT_HTML));
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026")))
                .andRespond(withSuccess("{\"items\":[{\"Tarih\":\"2026/11/01\",\"TP_TRY_MT06\":\"1\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(url("TP.TRY.MT06", "01-11-2026", "01-11-2026")))
                .andRespond(withSuccess("{\"totalCount\":0}", MediaType.APPLICATION_JSON));
        EvdsClient client = client("k");
        LocalDate day = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> client.fetchSeries("TP.TRY.MT06", day, day))
                .isInstanceOf(EvdsException.Rejected.class).hasMessageContaining("JSON olmayan");
        assertThatThrownBy(() -> client.fetchSeries("TP.TRY.MT06", day, day))
                .isInstanceOf(EvdsException.Rejected.class).hasMessageContaining("tarih biçimi");
        assertThatThrownBy(() -> client.fetchSeries("TP.TRY.MT06", day, day))
                .isInstanceOf(EvdsException.Rejected.class).hasMessageContaining("items");
    }

    // --- parser details ---

    @Test
    void parserHandlesNumbersBlanksAndBadValues() {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(EvdsResponseParser.parseValue(mapper.getNodeFactory().numberNode(new java.math.BigDecimal("41.6234"))))
                .isEqualTo("41.6234");
        assertThat(EvdsResponseParser.parseValue(mapper.getNodeFactory().textNode(" "))).isNull();
        assertThat(EvdsResponseParser.parseValue(null)).isNull();
        assertThatThrownBy(() -> EvdsResponseParser.parseValue(mapper.getNodeFactory().textNode("ND")))
                .isInstanceOf(EvdsException.Rejected.class);
        assertThat(EvdsResponseParser.parseDate("2026-12")).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThatThrownBy(() -> EvdsResponseParser.parseDate("31-02-2026")).isInstanceOf(EvdsException.Rejected.class);
    }
}
