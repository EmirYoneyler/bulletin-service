package com.fonbulteni.bulletinservice.repository;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.entity.FundPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FundPriceRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Thu 29 Oct, Fri 30 Oct, (weekend 31 Oct – 1 Nov), Mon 2 Nov, Tue 3 Nov 2026
    private static final LocalDate THU = LocalDate.of(2026, 10, 29);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 30);
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUE = LocalDate.of(2026, 11, 3);

    @Test
    void weekdayConstantsAreRight() {
        assertThat(List.of(THU, FRI, MON, TUE)).extracting(LocalDate::getDayOfWeek).containsExactly(
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }
    private static final Instant FETCHED = Instant.parse("2026-11-04T16:30:00Z");

    @Autowired
    FundPriceRepository prices;

    @Autowired
    TestEntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private Fund fund;
    private Fund otherFund;

    @BeforeEach
    void createFunds() {
        fund = em.persistAndFlush(Fund.builder().name("Test Fonu").tefasCode("TST").build());
        otherFund = em.persistAndFlush(Fund.builder().name("Diğer Fon").tefasCode("OTR").build());
    }

    private int upsert(Fund target, LocalDate date, String price) {
        return prices.upsert(target.getId(), date, new BigDecimal(price), new BigDecimal("1000000.00"), 1200,
                FundPrice.Source.TEFAS.name(), FETCHED);
    }

    private List<FundPrice> reload(Fund target) {
        em.clear();
        // LocalDate.MIN/MAX are outside PostgreSQL's date range.
        return prices.findByFundIdAndPriceDateBetweenOrderByPriceDateAsc(
                target.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));
    }

    // --- schema ---

    @Test
    void migrationCreatesPriceDateIndex() {
        Integer count = jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename = 'fund_price' and indexname = 'idx_fund_price_price_date'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void oneRowPerFundPerDay() {
        em.persistAndFlush(FundPrice.builder().fund(fund).priceDate(FRI).price(BigDecimal.ONE)
                .source(FundPrice.Source.TEFAS).fetchedAt(FETCHED).build());

        assertThatThrownBy(() -> prices.saveAndFlush(FundPrice.builder().fund(fund).priceDate(FRI)
                .price(BigDecimal.TEN).source(FundPrice.Source.TEFAS).fetchedAt(FETCHED).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> upsert(fund, FRI, "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingFundDeletesItsPrices() {
        upsert(fund, FRI, "1.5");

        jdbc.update("delete from funds where id = ?", fund.getId());

        assertThat(jdbc.queryForObject("select count(*) from fund_price", Integer.class)).isZero();
    }

    // --- upsert ---

    @Test
    void upsertInsertsNewRowWithFullPrecision() {
        assertThat(upsert(fund, FRI, "12.345678")).isEqualTo(1);

        assertThat(reload(fund)).singleElement().satisfies(row -> {
            assertThat(row.getPrice()).isEqualByComparingTo("12.345678");
            assertThat(row.getTotalValue()).isEqualByComparingTo("1000000.00");
            assertThat(row.getInvestorCount()).isEqualTo(1200);
            assertThat(row.getSource()).isEqualTo(FundPrice.Source.TEFAS);
            assertThat(row.getFetchedAt()).isEqualTo(FETCHED);
        });
    }

    @Test
    void upsertUpdatesExistingDayInPlace() {
        upsert(fund, FRI, "1.000000");

        assertThat(upsert(fund, FRI, "1.250000")).isEqualTo(1);

        assertThat(reload(fund)).singleElement()
                .satisfies(row -> assertThat(row.getPrice()).isEqualByComparingTo("1.25"));
    }

    @Test
    void upsertWithIdenticalValuesChangesNothing() {
        upsert(fund, FRI, "1.000000");

        assertThat(upsert(fund, FRI, "1.000000")).isZero();
    }

    @Test
    void upsertAcceptsMissingOptionalColumns() {
        int written = prices.upsert(fund.getId(), FRI, new BigDecimal("2.5"), null, null,
                FundPrice.Source.TEFAS.name(), FETCHED);

        assertThat(written).isEqualTo(1);
        assertThat(reload(fund)).singleElement().satisfies(row -> {
            assertThat(row.getTotalValue()).isNull();
            assertThat(row.getInvestorCount()).isNull();
        });
    }

    @Test
    void sameDayForDifferentFundsDoesNotConflict() {
        assertThat(upsert(fund, FRI, "1.0")).isEqualTo(1);
        assertThat(upsert(otherFund, FRI, "9.0")).isEqualTo(1);

        assertThat(reload(fund)).singleElement()
                .satisfies(row -> assertThat(row.getPrice()).isEqualByComparingTo("1.0"));
    }

    // --- range query ---

    @Test
    void rangeIsInclusiveSortedAndScopedToFund() {
        upsert(fund, TUE, "1.04");
        upsert(fund, THU, "1.01");
        upsert(fund, MON, "1.03");
        upsert(fund, FRI, "1.02");
        upsert(otherFund, FRI, "5.00");
        em.clear();

        List<FundPrice> range = prices.findByFundIdAndPriceDateBetweenOrderByPriceDateAsc(fund.getId(), FRI, MON);

        assertThat(range).extracting(FundPrice::getPriceDate).containsExactly(FRI, MON);
    }

    // --- last price before a date ---

    @Test
    void lastPriceBeforeSkipsWeekend() {
        upsert(fund, THU, "1.01");
        upsert(fund, FRI, "1.02");
        upsert(fund, MON, "1.03");
        em.clear();

        // Period starting Monday: P_first is Friday's close.
        assertThat(prices.findFirstByFundIdAndPriceDateBeforeOrderByPriceDateDesc(fund.getId(), MON))
                .get().extracting(FundPrice::getPriceDate).isEqualTo(FRI);
        // Sunday has no row either; still Friday.
        assertThat(prices.findFirstByFundIdAndPriceDateBeforeOrderByPriceDateDesc(fund.getId(), MON.minusDays(1)))
                .get().extracting(FundPrice::getPriceDate).isEqualTo(FRI);
    }

    @Test
    void lastPriceBeforeIsStrictlyBefore() {
        upsert(fund, THU, "1.01");
        upsert(fund, FRI, "1.02");
        em.clear();

        assertThat(prices.findFirstByFundIdAndPriceDateBeforeOrderByPriceDateDesc(fund.getId(), FRI))
                .get().extracting(FundPrice::getPriceDate).isEqualTo(THU);
    }

    @Test
    void lastPriceBeforeIsEmptyWithoutEarlierData() {
        upsert(fund, FRI, "1.02");
        upsert(otherFund, THU, "5.00");
        em.clear();

        assertThat(prices.findFirstByFundIdAndPriceDateBeforeOrderByPriceDateDesc(fund.getId(), FRI)).isEmpty();
    }
}
