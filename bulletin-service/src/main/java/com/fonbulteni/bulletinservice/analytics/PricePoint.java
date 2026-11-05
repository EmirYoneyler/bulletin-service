package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One closing price on one trading day. */
public record PricePoint(LocalDate date, BigDecimal price) {

    public PricePoint {
        if (date == null || price == null) {
            throw new IllegalArgumentException("date and price are required");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive: " + price);
        }
    }
}
