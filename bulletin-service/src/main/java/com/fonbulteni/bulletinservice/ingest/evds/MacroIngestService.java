package com.fonbulteni.bulletinservice.ingest.evds;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.repository.IngestRunRepository;
import com.fonbulteni.bulletinservice.repository.MacroPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the configured EVDS series into {@code macro_point}. Each run re-reads the last
 * {@value #LOOKBACK_DAYS} days, which covers year-to-date and one-year comparisons and lets a missed
 * week fill itself in. Without an API key the run is recorded as SKIPPED and nothing is requested.
 */
@Service
public class MacroIngestService {

    public static final int LOOKBACK_DAYS = 400;
    static final String SKIP_REASON = "EVDS_API_KEY tanımlı değil; makro veri çekilmedi.";

    private static final Logger log = LoggerFactory.getLogger(MacroIngestService.class);

    private final EvdsClient evds;
    private final MacroPointRepository points;
    private final IngestRunRepository runs;
    private final Clock clock;

    public MacroIngestService(EvdsClient evds, MacroPointRepository points, IngestRunRepository runs, Clock clock) {
        this.evds = evds;
        this.points = points;
        this.runs = runs;
        this.clock = clock;
    }

    public IngestRun ingestRecent(IngestRun.Trigger trigger) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        List<String> seriesCodes = evds.series().all();
        IngestRun run = IngestRun.started(IngestRun.Source.EVDS, trigger, from, today, seriesCodes.size(),
                Instant.now(clock));

        if (!evds.isEnabled()) {
            run.skip(SKIP_REASON, Instant.now(clock));
            IngestRun saved = runs.save(run);
            log.warn("EVDS ingest #{} {} skipped: no EVDS_API_KEY", saved.getId(), trigger);
            return saved;
        }
        run = runs.save(run);

        int written = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        for (String code : seriesCodes) {
            try {
                Instant fetchedAt = Instant.now(clock);
                for (EvdsObservation observation : evds.fetchSeries(code, from, today)) {
                    if (points.upsert(code, observation.date(), observation.value(), fetchedAt) > 0) {
                        written++;
                    } else {
                        unchanged++;
                    }
                }
            } catch (EvdsException e) {
                log.warn("EVDS ingest skipped {} ({} – {}): {}", code, from, today, e.getMessage());
                errors.add(code + ": " + e.getMessage());
            } catch (RuntimeException e) {
                log.error("EVDS ingest failed unexpectedly for {}", code, e);
                errors.add(code + ": beklenmeyen hata (" + e.getClass().getSimpleName() + ")");
            }
        }

        run.finish(errors.size(), written, unchanged, errors.isEmpty() ? null : String.join("\n", errors),
                Instant.now(clock));
        IngestRun saved = runs.save(run);
        log.info("EVDS ingest #{} {} {} – {}: {} series, {} row(s) written, {} unchanged, {} series failed → {}",
                saved.getId(), trigger, from, today, seriesCodes.size(), written, unchanged, errors.size(),
                saved.getStatus());
        return saved;
    }
}
