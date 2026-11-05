package com.fonbulteni.bulletinservice.ingest.tefas;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading day of a fund as published by TEFAS. Numbers are kept exactly as received.
 *
 * @param price             unit price (TEFAS {@code fiyat})
 * @param sharesOutstanding units in circulation ({@code tedPaySayisi}); may be null
 * @param investorCount     number of investors ({@code kisiSayisi}); may be null
 * @param totalValue        fund size in TRY ({@code portfoyBuyukluk}); may be null
 */
public record TefasPriceDto(
        String fundCode,
        String fundTitle,
        LocalDate date,
        BigDecimal price,
        BigDecimal sharesOutstanding,
        Integer investorCount,
        BigDecimal totalValue) {
}
