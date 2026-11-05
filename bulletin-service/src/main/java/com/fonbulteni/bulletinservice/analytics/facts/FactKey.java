package com.fonbulteni.bulletinservice.analytics.facts;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catalog of fact keys (AGENT_PROMPT §7.4, {@code docs/facts-keys.md}). {@code {KOD}} stands for a TEFAS
 * fund code. The Turkish description is what the writer prompt shows next to each placeholder; the writer
 * never sees values.
 */
public enum FactKey {

    FUND_RETURN_1W("fund.{KOD}.return.1w", Fact.Unit.PERCENT, Fact.Source.TEFAS, "return.simple",
            "{KOD} fonunun haftalık getirisi (önceki cuma → bu cuma)"),
    FUND_RETURN_MTD("fund.{KOD}.return.mtd", Fact.Unit.PERCENT, Fact.Source.TEFAS, "return.simple",
            "{KOD} fonunun ay başından bu yana getirisi"),
    FUND_RETURN_YTD("fund.{KOD}.return.ytd", Fact.Unit.PERCENT, Fact.Source.TEFAS, "return.simple",
            "{KOD} fonunun yılbaşından bu yana getirisi"),
    FUND_RETURN_1Y("fund.{KOD}.return.1y", Fact.Unit.PERCENT, Fact.Source.TEFAS, "return.simple",
            "{KOD} fonunun son bir yıllık getirisi"),
    FUND_REAL_YTD("fund.{KOD}.real.ytd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "return.real.cpi",
            "{KOD} fonunun yılbaşından bu yana enflasyondan arındırılmış (reel) getirisi"),
    FUND_VOL_63D("fund.{KOD}.vol.63d", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "volatility.log.63d.annualized",
            "{KOD} fonunun yıllıklandırılmış oynaklığı (son 63 işlem günü)"),
    FUND_DRAWDOWN_1Y("fund.{KOD}.drawdown.1y", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "drawdown.max",
            "{KOD} fonunun son bir yıldaki en büyük düşüşü (zirveden)"),

    PORTFOLIO_RETURN_1W("portfolio.return.1w", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "portfolio.equal_weight",
            "İzlenen fonların haftalık ortalama getirisi"),
    PORTFOLIO_RETURN_MTD("portfolio.return.mtd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "portfolio.equal_weight",
            "İzlenen fonların ay başından bu yana ortalama getirisi"),
    PORTFOLIO_RETURN_YTD("portfolio.return.ytd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "portfolio.equal_weight",
            "İzlenen fonların yılbaşından bu yana ortalama getirisi"),
    PORTFOLIO_REAL_1W("portfolio.real.1w", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "return.real.cpi",
            "İzlenen fonların haftalık reel getirisi (aylık TÜFE haftaya orantılanarak)"),

    BENCH_USDTRY_1W("bench.USDTRY.return.1w", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "Dolar/TL kurunun haftalık değişimi"),
    BENCH_USDTRY_MTD("bench.USDTRY.return.mtd", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "Dolar/TL kurunun ay başından bu yana değişimi"),
    BENCH_USDTRY_YTD("bench.USDTRY.return.ytd", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "Dolar/TL kurunun yılbaşından bu yana değişimi"),
    BENCH_BIST100_1W("bench.BIST100.return.1w", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "BIST 100 getiri endeksinin haftalık değişimi"),
    BENCH_BIST100_MTD("bench.BIST100.return.mtd", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "BIST 100 getiri endeksinin ay başından bu yana değişimi"),
    BENCH_BIST100_YTD("bench.BIST100.return.ytd", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.simple",
            "BIST 100 getiri endeksinin yılbaşından bu yana değişimi"),
    BENCH_DEPOSIT_1W("bench.DEPOSIT.return.1w", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "deposit.compound",
            "Güncel TL mevduat faiziyle bir haftalık getiri"),
    BENCH_DEPOSIT_MTD("bench.DEPOSIT.return.mtd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "deposit.compound",
            "Güncel TL mevduat faiziyle ay başından bu yana getiri"),
    BENCH_DEPOSIT_YTD("bench.DEPOSIT.return.ytd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "deposit.compound",
            "Güncel TL mevduat faiziyle yılbaşından bu yana getiri"),
    BENCH_GOLD_MOM("bench.GOLD.return.mom", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.monthly",
            "Külçe altın fiyatının son tamamlanan aydaki aylık değişimi"),
    BENCH_GOLD_YTD("bench.GOLD.return.ytd", Fact.Unit.PERCENT, Fact.Source.EVDS, "return.monthly",
            "Külçe altın fiyatının yılbaşından son tamamlanan aya kadarki değişimi"),
    BENCH_CPI_MONTH("bench.CPI.month", Fact.Unit.PERCENT, Fact.Source.EVDS, "cpi.monthly",
            "Son açıklanan aylık enflasyon (TÜFE)"),
    BENCH_CPI_YTD("bench.CPI.ytd", Fact.Unit.PERCENT, Fact.Source.COMPUTED, "cpi.ratio",
            "Yılbaşından bu yana enflasyon (TÜFE)");

    public static final String CODE = "{KOD}";

    private final String pattern;
    private final Fact.Unit unit;
    private final Fact.Source source;
    private final String method;
    private final String description;
    private final Pattern regex;

    FactKey(String pattern, Fact.Unit unit, Fact.Source source, String method, String description) {
        this.pattern = pattern;
        this.unit = unit;
        this.source = source;
        this.method = method;
        this.description = description;
        this.regex = Pattern.compile(Pattern.quote(pattern).replace(CODE, "\\E([A-Z0-9]{3,10})\\Q"));
    }

    public String pattern() {
        return pattern;
    }

    public Fact.Unit unit() {
        return unit;
    }

    public Fact.Source source() {
        return source;
    }

    public String method() {
        return method;
    }

    public boolean perFund() {
        return pattern.contains(CODE);
    }

    /** The concrete key, e.g. {@code fund.AAV.return.1w}. */
    public String key(String fundCode) {
        if (!perFund()) {
            throw new IllegalStateException(pattern + " is not a per-fund key");
        }
        return pattern.replace(CODE, fundCode);
    }

    public String key() {
        if (perFund()) {
            throw new IllegalStateException(pattern + " needs a fund code");
        }
        return pattern;
    }

    /** Turkish description of a concrete key, e.g. "AAV fonunun haftalık getirisi (…)". */
    public static Optional<String> describe(String key) {
        return Arrays.stream(values())
                .map(k -> {
                    var m = k.regex.matcher(key);
                    return m.matches() ? (k.perFund() ? k.description.replace(CODE, m.group(1)) : k.description) : null;
                })
                .filter(d -> d != null)
                .findFirst();
    }
}
