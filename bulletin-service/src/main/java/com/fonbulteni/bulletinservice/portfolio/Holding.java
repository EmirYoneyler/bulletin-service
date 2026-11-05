package com.fonbulteni.bulletinservice.portfolio;

import com.fonbulteni.bulletinservice.entity.Fund;

import java.math.BigDecimal;

/** A fund a user follows. {@code units} null means "watch only" (no position size known). */
public record Holding(Fund fund, BigDecimal units) {
}
