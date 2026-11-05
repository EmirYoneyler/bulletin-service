package com.fonbulteni.bulletinservice.repository;

import com.fonbulteni.bulletinservice.entity.MacroPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MacroPointRepository extends JpaRepository<MacroPoint, Long> {

    /** Observations in {@code [from, to]}, oldest first. */
    List<MacroPoint> findBySeriesCodeAndObsDateBetweenOrderByObsDateAsc(String seriesCode, LocalDate from, LocalDate to);

    /** Latest observation on or before {@code date}, e.g. "the CPI month in force on that day". */
    Optional<MacroPoint> findFirstBySeriesCodeAndObsDateLessThanEqualOrderByObsDateDesc(String seriesCode, LocalDate date);

    /** Insert or update; returns 1 if a row was inserted or its value changed, 0 if identical. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into macro_point (series_code, obs_date, value, fetched_at)
            values (:seriesCode, :obsDate, :value, :fetchedAt)
            on conflict (series_code, obs_date) do update set
                value      = excluded.value,
                fetched_at = excluded.fetched_at
            where macro_point.value is distinct from excluded.value
            """)
    int upsert(@Param("seriesCode") String seriesCode,
               @Param("obsDate") LocalDate obsDate,
               @Param("value") BigDecimal value,
               @Param("fetchedAt") Instant fetchedAt);
}
