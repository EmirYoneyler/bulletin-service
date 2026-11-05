package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.common.InvalidRequestException;
import com.fonbulteni.bulletinservice.config.SecurityConfig;
import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.scheduler.DailyPriceJob;
import com.fonbulteni.bulletinservice.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminIngestController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class AdminIngestControllerTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.com").roles("ADMIN");
    private static final RequestPostProcessor USER = user("okur@example.com").roles("USER");
    private static final Instant NOW = Instant.parse("2026-11-04T17:00:00Z");

    @TestConfiguration
    static class Clocks {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, java.time.ZoneId.of("Europe/Istanbul"));
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DailyPriceJob job;

    private static IngestRun partialRun(IngestRun.Trigger trigger, LocalDate from, LocalDate to) {
        IngestRun run = IngestRun.started(IngestRun.Source.TEFAS, trigger, from, to, 2, NOW);
        run.finish(1, 4, 1, "AAV: TEFAS'a 3 denemede ulaşılamadı (AAV).", NOW);
        return run;
    }

    // --- daily job trigger ---

    @Test
    void adminCanRunDailyJob() throws Exception {
        given(job.run(IngestRun.Trigger.MANUAL)).willReturn(Optional.of(
                partialRun(IngestRun.Trigger.MANUAL, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 4))));

        mvc.perform(post("/api/admin/jobs/daily-price/run").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.rangeFrom").value("2026-10-29"))
                .andExpect(jsonPath("$.fundCount").value(2))
                .andExpect(jsonPath("$.fundsFailed").value(1))
                .andExpect(jsonPath("$.rowsWritten").value(4))
                .andExpect(jsonPath("$.rowsUnchanged").value(1))
                .andExpect(jsonPath("$.errors[0]").value("AAV: TEFAS'a 3 denemede ulaşılamadı (AAV)."));
    }

    @Test
    void busyLockIs409() throws Exception {
        given(job.run(IngestRun.Trigger.MANUAL)).willReturn(Optional.empty());

        mvc.perform(post("/api/admin/jobs/daily-price/run").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_ALREADY_RUNNING"));
    }

    @Test
    void onlyAdminsCanTrigger() throws Exception {
        mvc.perform(post("/api/admin/jobs/daily-price/run")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/jobs/daily-price/run").with(USER)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/ingest/tefas").with(USER)
                        .param("code", "AAV").param("from", "2026-10-01").param("to", "2026-11-04"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(job);
    }

    // --- backfill ---

    @Test
    void adminCanBackfillOneFund() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 11, 4);
        given(job.backfill("AAV", from, to)).willReturn(Optional.of(partialRun(IngestRun.Trigger.BACKFILL, from, to)));

        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN)
                        .param("code", "AAV").param("from", "2026-10-01").param("to", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger").value("BACKFILL"))
                .andExpect(jsonPath("$.rangeTo").value("2026-11-04"));
    }

    @Test
    void backfillValidatesParameters() throws Exception {
        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN).param("from", "2026-10-01").param("to", "2026-11-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN)
                        .param("code", "AAV").param("from", "01.10.2026").param("to", "2026-11-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(job);
    }

    @Test
    void backfillServiceErrorsKeepTheirStatus() throws Exception {
        given(job.backfill(any(), any(), any()))
                .willThrow(new InvalidRequestException("INVALID_RANGE", "Bitiş tarihi bugünden sonra olamaz."))
                .willThrow(FundNotFoundException.byTefasCode("XYZ"))
                .willReturn(Optional.empty());

        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN)
                        .param("code", "AAV").param("from", "2026-10-01").param("to", "2026-12-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN)
                        .param("code", "XYZ").param("from", "2026-10-01").param("to", "2026-11-04"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FUND_NOT_FOUND"));
        mvc.perform(post("/api/admin/ingest/tefas").with(ADMIN)
                        .param("code", "AAV").param("from", "2026-10-01").param("to", "2026-11-04"))
                .andExpect(status().isConflict());
    }
}
