package com.fonbulteni.bulletinservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.common.TimeConfig;
import com.fonbulteni.bulletinservice.config.SecurityConfig;
import com.fonbulteni.bulletinservice.service.JwtService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevTokenControllerTest {

    @Nested
    @WebMvcTest(DevTokenController.class)
    @Import({SecurityConfig.class, JwtService.class, TimeConfig.class})
    @ActiveProfiles({"dev", "test"})
    class InDevProfile {

        @Autowired
        MockMvc mvc;

        @Autowired
        JwtService jwtService;

        @Autowired
        ObjectMapper objectMapper;

        @Test
        void issuesVerifiableAdminTokenWithoutAuthentication() throws Exception {
            String body = mvc.perform(post("/api/dev/token")
                            .param("email", "dev@example.com")
                            .param("role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("dev@example.com"))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.expiresInMs").isNumber())
                    .andReturn().getResponse().getContentAsString();

            JsonNode json = objectMapper.readTree(body);
            assertThat(jwtService.parse(json.get("token").asText()))
                    .hasValue(new JwtService.TokenClaims("dev@example.com", "ADMIN"));
        }

        @Test
        void defaultsToUserRole() throws Exception {
            mvc.perform(post("/api/dev/token").param("email", "dev@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        void unknownRoleIs400WithFieldError() throws Exception {
            mvc.perform(post("/api/dev/token").param("email", "dev@example.com").param("role", "ROOT"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("role"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Rol USER veya ADMIN olmalı."));
        }

        @Test
        void invalidEmailIs400() throws Exception {
            mvc.perform(post("/api/dev/token").param("email", "not-an-email"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Geçerli bir e-posta adresi girin."));
        }

        @Test
        void missingEmailIs400() throws Exception {
            mvc.perform(post("/api/dev/token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        }
    }

    @Nested
    @WebMvcTest(DevTokenController.class)
    @Import({SecurityConfig.class, JwtService.class, TimeConfig.class})
    @ActiveProfiles("test")
    class OutsideDevProfile {

        @Autowired
        MockMvc mvc;

        @Test
        void endpointDoesNotExist() throws Exception {
            mvc.perform(post("/api/dev/token").param("email", "dev@example.com").param("role", "ADMIN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }
}
