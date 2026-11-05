package com.fonbulteni.bulletinservice.ingest.tefas;

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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Uses responses recorded live on 2026-11-05 (src/test/resources/fixtures/tefas, ADR 001). No network. */
class TefasClientTest {

    private static final String BASE = "https://tefas.test/api/funds";
    private static final String URL = BASE + "/fonGnlBlgSiraliGetir";
    private static final String UA = "fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)";

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private final List<Duration> sleeps = new ArrayList<>();
    private long fakeNanos;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps.clear();
        fakeNanos = 0;
    }

    private TefasClient client(int pageSize) {
        TefasProperties properties = new TefasProperties(BASE, UA, Duration.ofSeconds(5), Duration.ofSeconds(30),
                Duration.ofMillis(1500), 3, Duration.ofSeconds(2), pageSize);
        // Fake time: sleeping advances the clock instantly, so pacing is observable and tests stay fast.
        return new TefasClient(builder.build(), properties, d -> {
            sleeps.add(d);
            fakeNanos += d.toNanos();
        }, () -> fakeNanos);
    }

    private TefasClient client() {
        return client(100);
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("fixtures/tefas/" + name);
    }

    // --- normal response ---

    @Test
    void sendsExpectedRequestAndMapsRecordedResponse() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, UA))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fonTipi").value("YAT"))
                .andExpect(jsonPath("$.fonKodu").value("AAV"))
                .andExpect(jsonPath("$.basTarih").value("20261027"))
                .andExpect(jsonPath("$.bitTarih").value("20261104"))
                .andExpect(jsonPath("$.basSira").value(1))
                .andExpect(jsonPath("$.bitSira").value(100))
                .andExpect(jsonPath("$.dil").value("TR"))
                .andRespond(withSuccess(fixture("history-aav-normal.json"), MediaType.APPLICATION_JSON));

        List<TefasPriceDto> prices = client().fetchHistory("aav", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 4));

        server.verify();
        assertThat(prices).extracting(TefasPriceDto::date).containsExactly(
                LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4));
        TefasPriceDto first = prices.get(0);
        assertThat(first.fundCode()).isEqualTo("AAV");
        assertThat(first.fundTitle()).startsWith("ATA PORTFÖY İKİNCİ HİSSE SENEDİ");
        // Exact decimal values as published, no double rounding.
        assertThat(first.price()).isEqualTo("56.119693");
        assertThat(first.totalValue()).isEqualTo("211852682.28");
        assertThat(first.sharesOutstanding()).isEqualByComparingTo("3775015");
        assertThat(first.investorCount()).isEqualTo(1725);
        assertThat(prices.get(5).price()).isEqualTo("55.444077");
    }

    @Test
    void sendsPensionFundType() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.fonTipi").value("EMK"))
                .andExpect(jsonPath("$.fonKodu").value("AAJ"))
                .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));

        client().fetchHistory("aaj", TefasFundType.EMK, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        server.verify();
    }

    // --- empty data ---

    @Test
    void recordedEmptyDayIsEmptyListNotError() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));

        assertThat(client().fetchHistory("AAV", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))).isEmpty();
    }

    // --- rejected requests: never retried ---

    @Test
    void recordedRangeErrorIsRejectedWithoutRetry() {
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(fixture("history-range-too-long.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client().fetchHistory("AAV", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)))
                .isInstanceOf(TefasRequestRejectedException.class)
                .hasMessageContaining("Tarih aralığı 1 ayı aşamaz");
        server.verify();
    }

    @Test
    void forbiddenIsRejectedWithoutRetry() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client().fetchHistory("AAV", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)))
                .isInstanceOf(TefasRequestRejectedException.class)
                .hasMessageContaining("HTTP 403");
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    // --- transient failures: retried with backoff ---

    @Test
    void serverErrorThenSuccessIsRetried() {
        server.expect(once(), requestTo(URL)).andRespond(withServerError());
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(fixture("history-aav-normal.json"), MediaType.APPLICATION_JSON));

        List<TefasPriceDto> prices = client().fetchHistory("AAV", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 4));

        server.verify();
        assertThat(prices).hasSize(6);
        assertThat(sleeps).first().isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void timeoutAndTooManyRequestsAreRetried() {
        server.expect(once(), requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(fixture("history-aav-normal.json"), MediaType.APPLICATION_JSON));

        assertThat(client().fetchHistory("AAV", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 4))).hasSize(6);
        server.verify();
    }

    @Test
    void persistentFailureGivesUpAfterThreeAttemptsWithExponentialBackoff() {
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client().fetchHistory("AAV", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)))
                .isInstanceOf(TefasUnavailableException.class)
                .hasMessageContaining("3 denemede");
        server.verify();
        // Backoffs 2 s and 4 s; they already exceed the 1.5 s interval, so no extra pacing pause.
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    // --- chunking and pacing ---

    @Test
    void longRangeIsSplitIntoAtMostOneMonthChunksAndPaced() {
        String[][] chunks = {{"20260815", "20260914"}, {"20260915", "20261014"}, {"20261015", "20261104"}};
        for (String[] chunk : chunks) {
            server.expect(once(), requestTo(URL))
                    .andExpect(jsonPath("$.basTarih").value(chunk[0]))
                    .andExpect(jsonPath("$.bitTarih").value(chunk[1]))
                    .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));
        }

        client().fetchHistory("AAV", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 4));

        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofMillis(1500), Duration.ofMillis(1500));
    }

    @Test
    void monthEndChunksNeverExceedOneMonth() {
        // 31 Jan + 1 month - 1 day = 27 Feb (2026 is not a leap year), still within one month.
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.basTarih").value("20260131"))
                .andExpect(jsonPath("$.bitTarih").value("20260227"))
                .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.basTarih").value("20260228"))
                .andExpect(jsonPath("$.bitTarih").value("20260301"))
                .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));

        client().fetchHistory("AAV", LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1));

        server.verify();
    }

    @Test
    void pacingAlsoAppliesAcrossSeparateCalls() {
        server.expect(times(2), requestTo(URL))
                .andRespond(withSuccess(fixture("history-empty-day.json"), MediaType.APPLICATION_JSON));
        TefasClient client = client();

        client.fetchHistory("AAV", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        fakeNanos += Duration.ofMillis(400).toNanos(); // caller did other work for 0.4 s
        client.fetchHistory("AHU", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertThat(sleeps).containsExactly(Duration.ofMillis(1100));
    }

    // --- pagination (synthetic pages; a one-month single-fund range never exceeds one page live) ---

    @Test
    void followsPaginationUntilTotalIsReached() {
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.basSira").value(1))
                .andExpect(jsonPath("$.bitSira").value(2))
                .andRespond(withSuccess(page(3, row("2026-11-04", "55.444077"), row("2026-11-03", "55.176134")),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.basSira").value(3))
                .andExpect(jsonPath("$.bitSira").value(4))
                .andRespond(withSuccess(page(3, row("2026-11-02", "54.086555")), MediaType.APPLICATION_JSON));

        List<TefasPriceDto> prices = client(2).fetchHistory("AAV", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4));

        server.verify();
        assertThat(prices).extracting(TefasPriceDto::date).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4));
    }

    @Test
    void skipsRowsOfOtherFundsAndWithoutPositivePrice() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(
                page(3, row("2026-11-04", "55.444077"), row("2026-11-03", "0"),
                        "{\"fonKodu\":\"XYZ\",\"tarih\":\"2026-11-02\",\"fiyat\":1.5}"),
                MediaType.APPLICATION_JSON));

        assertThat(client().fetchHistory("AAV", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)))
                .extracting(TefasPriceDto::date).containsExactly(LocalDate.of(2026, 11, 4));
    }

    // --- argument validation ---

    @Test
    void rejectsInvalidArguments() {
        TefasClient client = client();
        assertThatThrownBy(() -> client.fetchHistory(" ", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchHistory("AAV", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String row(String date, String price) {
        return "{\"fonKodu\":\"AAV\",\"fonUnvan\":\"ATA PORTFÖY\",\"tarih\":\"" + date + "\",\"fiyat\":" + price
                + ",\"tedPaySayisi\":3772183,\"kisiSayisi\":1706,\"portfoyBuyukluk\":209145203.7}";
    }

    private static String page(int total, String... rows) {
        return "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":[" + String.join(",", rows)
                + "],\"toplamSayi\":" + total + ",\"toplamSayfa\":2}";
    }
}
