package com.fonbulteni.bulletinservice.analytics;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.entity.FundPrice;
import com.fonbulteni.bulletinservice.repository.FundPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Computes {@link FundStats} from stored daily prices ({@code fund_price}). Read-only, deterministic. */
@Service
public class FundStatsService {

    private final FundPriceRepository prices;

    public FundStatsService(FundPriceRepository prices) {
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public FundStats compute(Fund fund, LocalDate asOf) {
        // The one-year period starts earliest; a valid start price may be up to MAX_START_GAP_DAYS older.
        LocalDate from = Periods.oneYear(asOf).minusDays(ReturnMath.MAX_START_GAP_DAYS + 1L);
        List<PricePoint> series = ReturnMath.normalize(
                prices.findByFundIdAndPriceDateBetweenOrderByPriceDateAsc(fund.getId(), from, asOf).stream()
                        .map(FundStatsService::toPoint)
                        .toList());

        PricePoint last = series.isEmpty() ? null : series.get(series.size() - 1);
        return new FundStats(
                fund.getId(),
                fund.getTefasCode(),
                asOf,
                last == null ? null : last.date(),
                last == null ? null : last.price(),
                ReturnMath.periodReturn(series, Periods.week(asOf), asOf),
                ReturnMath.periodReturn(series, Periods.monthToDate(asOf), asOf),
                ReturnMath.periodReturn(series, Periods.yearToDate(asOf), asOf),
                ReturnMath.periodReturn(series, Periods.oneYear(asOf), asOf),
                ReturnMath.annualizedVolatility(series, asOf),
                ReturnMath.maxDrawdown(series, Periods.oneYear(asOf), asOf));
    }

    private static PricePoint toPoint(FundPrice price) {
        return new PricePoint(price.getPriceDate(), price.getPrice());
    }
}
