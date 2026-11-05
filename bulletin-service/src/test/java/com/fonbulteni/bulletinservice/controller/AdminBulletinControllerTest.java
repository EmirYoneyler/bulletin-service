package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinWeek;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactsAssembler;
import com.fonbulteni.bulletinservice.config.SecurityConfig;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBulletinController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class AdminBulletinControllerTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.com").roles("ADMIN");
    private static final RequestPostProcessor USER = user("okur@example.com").roles("USER");
    /** Monday 9 Nov 2026, 07:30 Istanbul: the latest completed week ended Friday 6 Nov. */
    private static final Instant NOW = Instant.parse("2026-11-09T04:30:00Z");
    private static final BulletinWeek WEEK = BulletinWeek.containing(LocalDate.of(2026, 11, 6));

    @TestConfiguration
    static class Clocks {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Istanbul"));
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FactsAssembler assembler;

    private static BulletinFacts sample() {
        LocalDate fri = WEEK.friday();
        return new BulletinFacts(WEEK.monday(), fri,
                List.of(Fact.of("fund.AAV.return.1w", new BigDecimal("0.0321"), Fact.Unit.PERCENT, fri.minusDays(6), fri,
                                Fact.Source.TEFAS, "return.simple"),
                        Fact.of("fund.AAV.return.ytd", null, Fact.Unit.PERCENT, LocalDate.of(2026, 1, 1), fri,
                                Fact.Source.TEFAS, "return.simple"),
                        Fact.of("bench.CPI.month", new BigDecimal("0.00000001"), Fact.Unit.PERCENT, null, null,
                                Fact.Source.EVDS, "cpi.monthly")),
                List.of("AAV"), List.of("AAV"), null, null, true,
                List.of(new BulletinFacts.SourceNote("TEFAS", "fon fiyatları, 06.11.2026")),
                List.of("Ekim 2026 TÜFE'si kullanıldı."));
    }

    @Test
    void adminSeesRawFormattedAndDescribedFacts() throws Exception {
        given(assembler.assemble(eq("okur@example.com"), eq(WEEK))).willReturn(sample());

        mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN)
                        .param("email", "okur@example.com").param("week", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-06"))
                .andExpect(jsonPath("$.facts[0].key").value("fund.AAV.return.1w"))
                .andExpect(jsonPath("$.facts[0].formatted").value("%3,2"))
                .andExpect(jsonPath("$.facts[0].direction").value("UP"))
                .andExpect(jsonPath("$.facts[0].description").value("AAV fonunun haftalık getirisi (önceki cuma → bu cuma)"))
                .andExpect(jsonPath("$.facts[1].value").doesNotExist())
                .andExpect(jsonPath("$.facts[1].formatted").value("veri yok"))
                .andExpect(jsonPath("$.equalWeighted").value(true))
                .andExpect(jsonPath("$.sources[0].name").value("TEFAS"))
                .andExpect(jsonPath("$.notes[0]").value("Ekim 2026 TÜFE'si kullanıldı."));
    }

    @Test
    void smallValuesAreWrittenAsPlainNumbers() throws Exception {
        given(assembler.assemble(any(), any())).willReturn(sample());

        String body = mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN).param("email", "okur@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("0.00000001").doesNotContain("1E-8");
    }

    @Test
    void withoutWeekTheLatestCompletedWeekIsUsed() throws Exception {
        given(assembler.assemble(any(), any())).willReturn(sample());

        mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN).param("email", "okur@example.com"))
                .andExpect(status().isOk());

        verify(assembler).assemble("okur@example.com", WEEK);
    }

    @Test
    void unfinishedWeekIsRejected() throws Exception {
        mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN)
                        .param("email", "okur@example.com").param("week", "2026-11-09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEEK"));
        verifyNoInteractions(assembler);
    }

    @Test
    void validatesParametersAndRole() throws Exception {
        mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mvc.perform(get("/api/admin/bulletins/facts/preview").with(ADMIN).param("email", "değil-eposta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        mvc.perform(get("/api/admin/bulletins/facts/preview").with(USER).param("email", "okur@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bulletins/facts/preview").param("email", "okur@example.com"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assembler);
    }
}
