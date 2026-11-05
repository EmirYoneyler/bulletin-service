package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.config.SecurityConfig;
import com.fonbulteni.bulletinservice.dto.FundRequest;
import com.fonbulteni.bulletinservice.dto.FundResponse;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.service.FundService;
import com.fonbulteni.bulletinservice.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class FundControllerTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("Europe/Istanbul"));
        }
    }

    @Autowired
    MockMvc mvc;

    // Writes need ROLE_ADMIN since E0-6; the token path itself is covered in SecurityConfigTest.
    private static final RequestPostProcessor ADMIN = user("admin@example.com").roles("ADMIN");

    @MockitoBean
    FundService fundService;

    private static FundResponse sampleFund() {
        return FundResponse.builder()
                .id(1L)
                .name("Örnek Hisse Fonu")
                .category(Fund.Category.HISSE)
                .riskLevel(Fund.RiskLevel.YUKSEK)
                .build();
    }

    // --- Happy paths: existing /api/funds contract is unchanged ---

    @Test
    void listsFunds() throws Exception {
        given(fundService.getAllFunds()).willReturn(List.of(sampleFund()));

        mvc.perform(get("/api/funds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Örnek Hisse Fonu"))
                .andExpect(jsonPath("$[0].category").value("HISSE"));
    }

    @Test
    void returnsFundById() throws Exception {
        given(fundService.getFundById(1L)).willReturn(sampleFund());

        mvc.perform(get("/api/funds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.riskLevel").value("YUKSEK"));
    }

    @Test
    void createsFund() throws Exception {
        given(fundService.createFund(any(FundRequest.class))).willReturn(sampleFund());

        mvc.perform(post("/api/funds").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Örnek Hisse Fonu", "category": "HISSE", "riskLevel": "YUKSEK"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Örnek Hisse Fonu"));
    }

    @Test
    void searchesFundsByName() throws Exception {
        given(fundService.searchFunds("hisse")).willReturn(List.of(sampleFund()));

        mvc.perform(get("/api/funds/search").param("name", "hisse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // --- 404 ---

    @Test
    void unknownFundIs404WithApiError() throws Exception {
        given(fundService.getFundById(99L)).willThrow(new FundNotFoundException(99L));

        mvc.perform(get("/api/funds/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FUND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Fon bulunamadı: 99"))
                .andExpect(jsonPath("$.path").value("/api/funds/99"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-05T12:00:00+03:00"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void unknownPathIs404WithApiError() throws Exception {
        mvc.perform(get("/api/funds/1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- 400 ---

    @Test
    void blankNameIs400WithFieldList() throws Exception {
        mvc.perform(post("/api/funds").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  ", "category": "HISSE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("İstek doğrulanamadı."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Fon adı boş olamaz."));

        verify(fundService, never()).createFund(any());
    }

    @Test
    void tooLongNameIs400() throws Exception {
        String longName = "a".repeat(256);

        mvc.perform(post("/api/funds").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Fon adı en fazla 255 karakter olabilir."));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/funds").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEnumValueInBodyIs400() throws Exception {
        mvc.perform(post("/api/funds").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Fon", "category": "KRIPTO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/funds/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("'id' parametresinin değeri geçersiz."));
    }

    @Test
    void unknownCategoryIs400() throws Exception {
        mvc.perform(get("/api/funds/category/KRIPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void missingSearchParameterIs400() throws Exception {
        mvc.perform(get("/api/funds/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("'name' parametresi zorunlu."));
    }

    // --- Other errors share the same shape ---

    @Test
    void unsupportedMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(delete("/api/funds").with(ADMIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedErrorIs500WithoutLeakingDetails() throws Exception {
        given(fundService.getAllFunds()).willThrow(new IllegalStateException("connection to db-host:5432 refused"));

        mvc.perform(get("/api/funds"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Beklenmeyen bir hata oluştu."))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
