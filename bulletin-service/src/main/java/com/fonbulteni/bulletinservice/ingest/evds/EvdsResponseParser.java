package com.fonbulteni.bulletinservice.ingest.evds;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads EVDS JSON: {@code {"items":[{"Tarih":"05-11-2026","TP_DK_USD_A_YTL":"10.5000"}, ...]}}.
 * The value field is the series code with dots replaced by underscores (ADR 002).
 */
final class EvdsResponseParser {

    // STRICT: the default SMART resolver would silently turn 31-02 into 28-02.
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DAY_PATTERN = Pattern.compile("\\d{2}-\\d{2}-\\d{4}");
    private static final Pattern MONTH_PATTERN = Pattern.compile("(\\d{4})-(\\d{1,2})");

    private EvdsResponseParser() {
    }

    static List<EvdsObservation> parse(String seriesCode, JsonNode body) {
        if (body == null || !body.path("items").isArray()) {
            throw new EvdsException.Rejected("EVDS yanıtında 'items' dizisi yok (" + seriesCode + ").", null);
        }
        String valueField = seriesCode.replace('.', '_');
        List<EvdsObservation> observations = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            BigDecimal value = parseValue(item.get(valueField));
            if (value != null) {
                observations.add(new EvdsObservation(seriesCode, parseDate(item.path("Tarih").asText(null)), value));
            }
        }
        return observations;
    }

    /** {@code dd-MM-yyyy} for daily and weekly series, {@code yyyy-M} for monthly (first day of month). */
    static LocalDate parseDate(String text) {
        try {
            if (text != null && DAY_PATTERN.matcher(text).matches()) {
                return LocalDate.parse(text, DAY);
            }
            Matcher month = text == null ? null : MONTH_PATTERN.matcher(text);
            if (month != null && month.matches()) {
                return YearMonth.of(Integer.parseInt(month.group(1)), Integer.parseInt(month.group(2))).atDay(1);
            }
        } catch (DateTimeException e) {
            throw new EvdsException.Rejected("EVDS geçersiz tarih döndürdü: " + text, e);
        }
        throw new EvdsException.Rejected("EVDS beklenmeyen tarih biçimi döndürdü: " + text, null);
    }

    /** Text or number; empty and null mean "no observation". Accepts both '.' and ',' as decimal separator. */
    static BigDecimal parseValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.indexOf(',') >= 0 && text.indexOf('.') < 0) {
            text = text.replace(',', '.');
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new EvdsException.Rejected("EVDS sayı olmayan bir değer döndürdü: " + node.asText(), e);
        }
    }
}
