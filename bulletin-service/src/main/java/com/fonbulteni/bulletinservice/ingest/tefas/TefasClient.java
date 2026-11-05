package com.fonbulteni.bulletinservice.ingest.tefas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Reads daily fund prices from TEFAS ({@code fonGnlBlgSiraliGetir}, see ADR 001).
 *
 * <p>Politeness (AGENT_PROMPT §6.6): one request at a time ({@code synchronized}), at least
 * {@code tefas.request-interval} between any two requests (also across calls), a descriptive
 * User-Agent, and at most {@code tefas.max-attempts} attempts with exponential backoff for
 * transient failures only.
 */
public class TefasClient {

    /** Pause abstraction so tests can verify waits without sleeping. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final Logger log = LoggerFactory.getLogger(TefasClient.class);
    private static final DateTimeFormatter TEFAS_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    /** TEFAS answers an empty range with this server exception text instead of an empty list (ADR 001). */
    private static final String EMPTY_RESULT_MARKER = "out of bounds";
    private static final int MAX_PAGES_PER_CHUNK = 50;

    private final RestClient restClient;
    private final TefasProperties properties;
    private final Sleeper sleeper;
    private final LongSupplier nanoTime;
    private long lastRequestNanos;
    private boolean anyRequestSent;

    public TefasClient(RestClient restClient, TefasProperties properties, Sleeper sleeper, LongSupplier nanoTime) {
        this.restClient = restClient;
        this.properties = properties;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    /** History of a securities investment fund ({@link TefasFundType#YAT}). */
    public List<TefasPriceDto> fetchHistory(String code, LocalDate from, LocalDate to) {
        return fetchHistory(code, TefasFundType.YAT, from, to);
    }

    /**
     * Prices of one fund for every trading day in {@code [from, to]}, oldest first. Days without
     * trading simply have no entry. The range is split into chunks of at most one calendar month,
     * the longest range TEFAS accepts.
     *
     * @throws TefasUnavailableException      TEFAS unreachable after all retries
     * @throws TefasRequestRejectedException  TEFAS refused the request
     */
    public synchronized List<TefasPriceDto> fetchHistory(String code, TefasFundType type, LocalDate from, LocalDate to) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Fon kodu boş olamaz.");
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Geçersiz tarih aralığı: " + from + " – " + to);
        }
        String fundCode = code.trim().toUpperCase(Locale.ROOT);

        TreeMap<LocalDate, TefasPriceDto> byDate = new TreeMap<>();
        for (LocalDate start = from; !start.isAfter(to); ) {
            LocalDate end = min(to, start.plusMonths(1).minusDays(1));
            for (TefasHistoryApi.Row row : fetchChunk(fundCode, type, start, end)) {
                TefasPriceDto dto = toDto(fundCode, row);
                if (dto != null) {
                    byDate.put(dto.date(), dto);
                }
            }
            start = end.plusDays(1);
        }
        return List.copyOf(byDate.values());
    }

    private List<TefasHistoryApi.Row> fetchChunk(String code, TefasFundType type, LocalDate from, LocalDate to) {
        List<TefasHistoryApi.Row> rows = new ArrayList<>();
        int first = 1;
        for (int page = 0; page < MAX_PAGES_PER_CHUNK; page++) {
            TefasHistoryApi.Request request = TefasHistoryApi.Request.history(type, code,
                    TEFAS_DATE.format(from), TEFAS_DATE.format(to), first, first + properties.pageSize() - 1);
            TefasHistoryApi.Response response = post(request);

            if (response.resultList() == null) {
                String message = response.errorMessage();
                if (message != null && message.contains(EMPTY_RESULT_MARKER)) {
                    return rows; // no trading days in this range
                }
                throw new TefasRequestRejectedException("TEFAS isteği reddetti (" + code + ", " + from + " – " + to
                        + "): " + (message != null ? message : "boş yanıt"));
            }
            rows.addAll(response.resultList());
            Integer total = response.toplamSayi();
            if (response.resultList().isEmpty() || total == null || rows.size() >= total) {
                return rows;
            }
            first += properties.pageSize();
        }
        throw new TefasRequestRejectedException("TEFAS yanıtı " + MAX_PAGES_PER_CHUNK + " sayfayı aştı (" + code + ").");
    }

    private TefasHistoryApi.Response post(TefasHistoryApi.Request request) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            pace();
            try {
                TefasHistoryApi.Response response = restClient.post()
                        .uri(TefasHistoryApi.PATH)
                        .header(HttpHeaders.USER_AGENT, properties.userAgent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(TefasHistoryApi.Response.class);
                if (response == null) {
                    throw new TefasRequestRejectedException("TEFAS boş gövde döndürdü.");
                }
                return response;
            } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests | ResourceAccessException e) {
                lastFailure = e;
                log.warn("TEFAS request failed (attempt {}/{}, {} {}): {}", attempt, properties.maxAttempts(),
                        request.fonKodu(), request.basTarih(), e.getMessage());
                if (attempt < properties.maxAttempts()) {
                    pause(properties.initialBackoff().multipliedBy(1L << (attempt - 1)));
                }
            } catch (HttpClientErrorException e) {
                throw new TefasRequestRejectedException(
                        "TEFAS isteği reddetti: HTTP " + e.getStatusCode().value(), e);
            }
        }
        throw new TefasUnavailableException(
                "TEFAS'a " + properties.maxAttempts() + " denemede ulaşılamadı (" + request.fonKodu() + ").",
                lastFailure);
    }

    /** Waits so that consecutive requests are at least {@code requestInterval} apart. */
    private void pace() {
        if (anyRequestSent) {
            long elapsed = nanoTime.getAsLong() - lastRequestNanos;
            long remaining = properties.requestInterval().toNanos() - elapsed;
            if (remaining > 0) {
                pause(Duration.ofNanos(remaining));
            }
        }
        lastRequestNanos = nanoTime.getAsLong();
        anyRequestSent = true;
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TefasUnavailableException("TEFAS isteği beklerken kesildi.", e);
        }
    }

    private static TefasPriceDto toDto(String requestedCode, TefasHistoryApi.Row row) {
        if (row.fonKodu() != null && !requestedCode.equalsIgnoreCase(row.fonKodu())) {
            log.warn("Skipping TEFAS row for unexpected fund {} (asked for {})", row.fonKodu(), requestedCode);
            return null;
        }
        if (row.fiyat() == null || row.fiyat().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Skipping TEFAS row without a positive price: {} {}", requestedCode, row.tarih());
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(row.tarih());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new TefasRequestRejectedException("TEFAS beklenmeyen tarih biçimi döndürdü: " + row.tarih(), e);
        }
        return new TefasPriceDto(requestedCode, row.fonUnvan(), date, row.fiyat(), row.tedPaySayisi(),
                row.kisiSayisi(), row.portfoyBuyukluk());
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
