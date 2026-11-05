package com.fonbulteni.bulletinservice.repository;

import com.fonbulteni.bulletinservice.entity.FundPrice;
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

public interface FundPriceRepository extends JpaRepository<FundPrice, Long> {

    /** Prices of one fund in {@code [from, to]} (both inclusive), oldest first. */
    List<FundPrice> findByFundIdAndPriceDateBetweenOrderByPriceDateAsc(Long fundId, LocalDate from, LocalDate to);

    /**
     * Last price strictly before {@code date}. This is P_first of a period starting on {@code date}
     * (AGENT_PROMPT §7.3): weekends and holidays are skipped naturally because they have no row.
     */
    Optional<FundPrice> findFirstByFundIdAndPriceDateBeforeOrderByPriceDateDesc(Long fundId, LocalDate date);

    /**
     * Inserts the price, or updates the existing row for the same fund and day. An identical re-fetch
     * is a no-op, so {@code fetched_at} keeps the time the current values were first seen.
     * The return value lets ingest jobs count rows actually written.
     *
     * @return 1 if a row was inserted or its values changed, 0 if an identical row already existed
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into fund_price (fund_id, price_date, price, total_value, investor_count, source, fetched_at)
            values (:fundId, :priceDate, :price, cast(:totalValue as numeric), cast(:investorCount as integer),
                    :source, :fetchedAt)
            on conflict (fund_id, price_date) do update set
                price          = excluded.price,
                total_value    = excluded.total_value,
                investor_count = excluded.investor_count,
                source         = excluded.source,
                fetched_at     = excluded.fetched_at
            where (fund_price.price, fund_price.total_value, fund_price.investor_count, fund_price.source)
                  is distinct from (excluded.price, excluded.total_value, excluded.investor_count, excluded.source)
            """)
    int upsert(@Param("fundId") Long fundId,
               @Param("priceDate") LocalDate priceDate,
               @Param("price") BigDecimal price,
               @Param("totalValue") BigDecimal totalValue,
               @Param("investorCount") Integer investorCount,
               @Param("source") String source,
               @Param("fetchedAt") Instant fetchedAt);
}
