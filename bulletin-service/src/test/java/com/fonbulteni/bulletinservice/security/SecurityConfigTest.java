package com.fonbulteni.bulletinservice.security;

import com.fonbulteni.bulletinservice.config.JwtProperties;
import com.fonbulteni.bulletinservice.config.SecurityConfig;
import com.fonbulteni.bulletinservice.controller.FundController;
import com.fonbulteni.bulletinservice.dto.FundRequest;
import com.fonbulteni.bulletinservice.dto.FundResponse;
import com.fonbulteni.bulletinservice.service.FundService;
import com.fonbulteni.bulletinservice.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authorization matrix of AGENT_PROMPT §7.7, exercised with real signed tokens. */
@WebMvcTest(FundController.class)
@Import({SecurityConfig.class, JwtService.class, SecurityConfigTest.ProbeController.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    /** Same value as jwt.secret in application-test.properties. */
    private static final String TEST_SECRET = "test-only-jwt-secret-with-at-least-32-bytes";

    /** Stand-ins for endpoints that later cards add under /api/me and /api/admin. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/me/ping")
        String me(Authentication authentication) {
            return authentication.getName();
        }

        @GetMapping("/api/admin/ping")
        String admin() {
            return "ok";
        }

        @GetMapping("/api/other")
        String other() {
            return "ok";
        }
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    FundService fundService;

    private String bearer(String email, String role) {
        return "Bearer " + jwtService.generateToken(email, role);
    }

    private ResultActions createFund(String authorization) throws Exception {
        given(fundService.createFund(any(FundRequest.class)))
                .willReturn(FundResponse.builder().id(1L).name("Fon").build());
        var request = post("/api/funds").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fon\"}");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request);
    }

    // --- /api/funds: reads stay public ---

    @Test
    void anonymousCanReadFunds() throws Exception {
        given(fundService.getAllFunds()).willReturn(List.of());

        mvc.perform(get("/api/funds")).andExpect(status().isOk());
    }

    @Test
    void nonBearerAuthorizationHeaderIsIgnoredOnPublicRead() throws Exception {
        given(fundService.getAllFunds()).willReturn(List.of());

        mvc.perform(get("/api/funds").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isOk());
    }

    // --- /api/funds: writes need ADMIN ---

    @Test
    void anonymousWriteIs401WithApiError() throws Exception {
        createFund(null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Bu işlem için geçerli bir token gerekiyor."))
                .andExpect(jsonPath("$.path").value("/api/funds"));
    }

    @Test
    void userWriteIs403WithApiError() throws Exception {
        createFund(bearer("user@example.com", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Bu işlem için yetkiniz yok."));
    }

    @Test
    void adminWriteIs200() throws Exception {
        createFund(bearer("admin@example.com", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void roleClaimWithPrefixOrLowercaseIsAccepted() throws Exception {
        createFund(bearer("admin@example.com", "ROLE_ADMIN")).andExpect(status().isOk());
        createFund(bearer("admin@example.com", "admin")).andExpect(status().isOk());
    }

    @Test
    void lowercaseBearerSchemeIsAccepted() throws Exception {
        createFund("bearer " + jwtService.generateToken("admin@example.com", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String expired = Jwts.builder()
                .subject("admin@example.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        createFund("Bearer " + expired).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIs401() throws Exception {
        JwtService foreign = new JwtService(new JwtProperties("another-secret-that-is-also-32-bytes-long", 60_000));

        createFund("Bearer " + foreign.generateToken("admin@example.com", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIs401() throws Exception {
        createFund("Bearer not.a.jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutRoleIsAuthenticatedButNotAdmin() throws Exception {
        createFund(bearer("someone@example.com", null)).andExpect(status().isForbidden());
    }

    // --- /api/me/** and /api/admin/** ---

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void meExposesTokenSubjectAsPrincipal() throws Exception {
        mvc.perform(get("/api/me/ping").header(HttpHeaders.AUTHORIZATION, bearer("emir@example.com", "USER")))
                .andExpect(status().isOk())
                .andExpect(content().string("emir@example.com"));
    }

    @Test
    void adminAreaRejectsUsers() throws Exception {
        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer("user@example.com", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAreaAllowsAdmins() throws Exception {
        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer("admin@example.com", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unlistedEndpointsAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/other")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/other").header(HttpHeaders.AUTHORIZATION, bearer("user@example.com", "USER")))
                .andExpect(status().isOk());
    }

    // --- Browser clients ---

    @Test
    void corsPreflightFromFrontendIsAnsweredWithoutToken() throws Exception {
        mvc.perform(options("/api/funds")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }
}
