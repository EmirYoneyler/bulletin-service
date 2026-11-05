package com.fonbulteni.bulletinservice.db;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Empty database: Flyway runs V1 and Hibernate's ddl-auto=validate accepts the result. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FlywayFreshDatabaseTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FundRepository fundRepository;

    @Test
    void appliesBaselineMigration() {
        Map<String, Object> v1 = jdbc.queryForMap(
                "select version, type, success from flyway_schema_history where version = '1'");

        assertThat(v1.get("type")).isEqualTo("SQL");
        assertThat(v1.get("success")).isEqualTo(true);
    }

    @Test
    void appliesTefasCodeMigration() {
        Map<String, Object> v2 = jdbc.queryForMap(
                "select type, success from flyway_schema_history where version = '2'");

        assertThat(v2.get("success")).isEqualTo(true);
    }

    @Test
    void tefasCodeIsUniqueButOptional() {
        fundRepository.saveAndFlush(Fund.builder().name("Kodsuz 1").build());
        fundRepository.saveAndFlush(Fund.builder().name("Kodsuz 2").build());
        fundRepository.saveAndFlush(Fund.builder().name("Kodlu").tefasCode("ZZ1").build());

        assertThat(fundRepository.findByTefasCode("ZZ1")).isPresent();
        assertThatThrownBy(() -> fundRepository.saveAndFlush(Fund.builder().name("Kopya").tefasCode("ZZ1").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fundEntityRoundTripsThroughMigratedSchema() {
        Fund saved = fundRepository.save(Fund.builder()
                .name("Örnek Hisse Fonu")
                .category(Fund.Category.HISSE)
                .riskLevel(Fund.RiskLevel.YUKSEK)
                .returnRate(12.5)
                .fundSize(1_000_000.0)
                .period(Fund.Period.YILLIK)
                .isFutureTrade(false)
                .expiryDate(LocalDate.of(2027, 1, 1))
                .contractSize(10.0)
                .build());

        Fund loaded = fundRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Örnek Hisse Fonu");
        assertThat(loaded.getCategory()).isEqualTo(Fund.Category.HISSE);
        assertThat(loaded.getPeriod()).isEqualTo(Fund.Period.YILLIK);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
}
