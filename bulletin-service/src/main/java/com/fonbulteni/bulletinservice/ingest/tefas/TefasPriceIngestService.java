package com.fonbulteni.bulletinservice.ingest.tefas;

import com.fonbulteni.bulletinservice.common.BusinessDays;
import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.common.InvalidRequestException;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.entity.FundPrice;
import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.repository.FundPriceRepository;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import com.fonbulteni.bulletinservice.repository.IngestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Copies TEFAS prices into {@code fund_price}. Each fund is handled on its own: a failure is
 * recorded in the run and the remaining funds still go through. Every run leaves an
 * {@code ingest_run} row with its counters.
 */
@Service
public class TefasPriceIngestService {

    /** The daily job re-reads this many weekdays, so a missed day fills itself in (AGENT_PROMPT §7.1). */
    public static final int RECENT_WEEKDAYS = 5;
    /** Upper bound for a manual backfill: 24 one-month requests, about 40 s at the polite pace. */
    public static final long MAX_BACKFILL_DAYS = 731;

    private static final Logger log = LoggerFactory.getLogger(TefasPriceIngestService.class);

    private final FundRepository funds;
    private final FundPriceRepository prices;
    private final IngestRunRepository runs;
    private final TefasClient tefas;
    private final Clock clock;

    public TefasPriceIngestService(FundRepository funds, FundPriceRepository prices, IngestRunRepository runs,
                                   TefasClient tefas, Clock clock) {
        this.funds = funds;
        this.prices = prices;
        this.runs = runs;
        this.tefas = tefas;
        this.clock = clock;
    }

    /** Last {@value #RECENT_WEEKDAYS} weekdays for every fund that has a TEFAS code. */
    public IngestRun ingestRecent(IngestRun.Trigger trigger) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = BusinessDays.startOfLastWeekdays(today, RECENT_WEEKDAYS);
        return run(trigger, funds.findByTefasCodeIsNotNullOrderByTefasCodeAsc(), from, today);
    }

    /** History of one fund over {@code [from, to]}. */
    public IngestRun backfill(String code, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        if (from.isAfter(to)) {
            throw new InvalidRequestException("INVALID_RANGE", "Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
        if (to.isAfter(today)) {
            throw new InvalidRequestException("INVALID_RANGE", "Bitiş tarihi bugünden sonra olamaz.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_BACKFILL_DAYS) {
            throw new InvalidRequestException("RANGE_TOO_LONG",
                    "Tek seferde en fazla " + MAX_BACKFILL_DAYS + " günlük geçmiş doldurulabilir.");
        }
        String tefasCode = code.trim().toUpperCase(Locale.ROOT);
        Fund fund = funds.findByTefasCode(tefasCode).orElseThrow(() -> FundNotFoundException.byTefasCode(tefasCode));
        return run(IngestRun.Trigger.BACKFILL, List.of(fund), from, to);
    }

    static TefasFundType fundTypeOf(Fund fund) {
        return fund.getCategory() == Fund.Category.EMEKLILIK ? TefasFundType.EMK : TefasFundType.YAT;
    }

    private IngestRun run(IngestRun.Trigger trigger, List<Fund> targets, LocalDate from, LocalDate to) {
        IngestRun run = runs.save(IngestRun.started(IngestRun.Source.TEFAS, trigger, from, to, targets.size(),
                Instant.now(clock)));

        int written = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        for (Fund fund : targets) {
            String code = fund.getTefasCode();
            try {
                Instant fetchedAt = Instant.now(clock);
                for (TefasPriceDto price : tefas.fetchHistory(code, fundTypeOf(fund), from, to)) {
                    int changed = prices.upsert(fund.getId(), price.date(), price.price(), price.totalValue(),
                            price.investorCount(), FundPrice.Source.TEFAS.name(), fetchedAt);
                    if (changed > 0) {
                        written++;
                    } else {
                        unchanged++;
                    }
                }
            } catch (TefasException e) {
                log.warn("TEFAS ingest skipped {} ({} – {}): {}", code, from, to, e.getMessage());
                errors.add(code + ": " + e.getMessage());
            } catch (RuntimeException e) {
                log.error("TEFAS ingest failed unexpectedly for {}", code, e);
                errors.add(code + ": beklenmeyen hata (" + e.getClass().getSimpleName() + ")");
            }
        }

        run.finish(errors.size(), written, unchanged, errors.isEmpty() ? null : String.join("\n", errors),
                Instant.now(clock));
        IngestRun saved = runs.save(run);
        log.info("TEFAS ingest #{} {} {} – {}: {} fund(s), {} row(s) written, {} unchanged, {} fund(s) failed → {}",
                saved.getId(), trigger, from, to, targets.size(), written, unchanged, errors.size(), saved.getStatus());
        return saved;
    }
}
