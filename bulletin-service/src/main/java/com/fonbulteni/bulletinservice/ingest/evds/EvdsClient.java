package com.fonbulteni.bulletinservice.ingest.evds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads series from TCMB EVDS (ADR 002). The API key travels in the {@code key} header. Each series is
 * requested on its own so EVDS does not convert frequencies, and ranges are split into chunks of at
 * most three years to stay under the 1000-observation limit.
 */
public class EvdsClient {

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static final int MAX_CHUNK_YEARS = 3;

    private final RestClient restClient;
    private final EvdsProperties properties;
    private final ObjectMapper objectMapper;

    public EvdsClient(RestClient restClient, EvdsProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public EvdsProperties.Series series() {
        return properties.series();
    }

    /**
     * Observations of one series in {@code [from, to]}, oldest first; empty values are left out.
     *
     * @throws EvdsException.Disabled    no API key configured
     * @throws EvdsException.Rejected    wrong key (403), other 4xx, or an unreadable response
     * @throws EvdsException.Unavailable EVDS unreachable
     */
    public List<EvdsObservation> fetchSeries(String seriesCode, LocalDate from, LocalDate to) {
        if (!isEnabled()) {
            throw new EvdsException.Disabled();
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Geçersiz tarih aralığı: " + from + " – " + to);
        }
        List<EvdsObservation> all = new ArrayList<>();
        for (LocalDate start = from; !start.isAfter(to); ) {
            LocalDate end = to.isBefore(start.plusYears(MAX_CHUNK_YEARS)) ? to : start.plusYears(MAX_CHUNK_YEARS).minusDays(1);
            all.addAll(EvdsResponseParser.parse(seriesCode, get(seriesCode, start, end)));
            start = end.plusDays(1);
        }
        return all;
    }

    private JsonNode get(String seriesCode, LocalDate from, LocalDate to) {
        try {
            // EVDS puts the "query" in the path: /series=CODE&startDate=..&endDate=..&type=json
            // Read as text: the response Content-Type is not verified yet (ADR 002), so do not rely on it.
            String body = restClient.get()
                    .uri("/series={code}&startDate={from}&endDate={to}&type=json",
                            seriesCode, EVDS_DATE.format(from), EVDS_DATE.format(to))
                    .header("key", properties.apiKey())
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return body == null ? null : objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new EvdsException.Rejected("EVDS JSON olmayan bir yanıt döndürdü (" + seriesCode + ").", e);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new EvdsException.Rejected("EVDS anahtarı reddetti (403). EVDS_API_KEY değerini kontrol edin.", e);
            }
            throw new EvdsException.Rejected("EVDS isteği reddetti: HTTP " + e.getStatusCode().value()
                    + " (" + seriesCode + ")", e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new EvdsException.Unavailable("EVDS'ye ulaşılamadı (" + seriesCode + "): " + e.getMessage(), e);
        }
    }
}
