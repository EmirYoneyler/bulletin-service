package com.fonbulteni.bulletinservice.config;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Testcontainers(disabledWithoutDocker = true)
class DevFundSeederTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    FundRepository fundRepository;

    @Autowired
    DevFundSeeder seeder;

    @Test
    void seedsFiveFundsOnePerCategoryOnStartup() {
        List<Fund> seeded = DevFundSeeder.SEED.stream()
                .map(seed -> fundRepository.findByTefasCode(seed.tefasCode()).orElseThrow())
                .toList();

        assertThat(seeded).hasSize(5);
        assertThat(seeded).extracting(Fund::getCategory).containsExactlyInAnyOrder(Fund.Category.values());
        assertThat(seeded).allSatisfy(fund -> {
            // Only verified fields are filled; nothing invented.
            assertThat(fund.getReturnRate()).isNull();
            assertThat(fund.getFundSize()).isNull();
            assertThat(fund.getRiskLevel()).isNotNull();
        });
    }

    @Test
    void runningAgainDoesNotDuplicate() {
        long before = fundRepository.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(fundRepository.count()).isEqualTo(before);
    }
}
