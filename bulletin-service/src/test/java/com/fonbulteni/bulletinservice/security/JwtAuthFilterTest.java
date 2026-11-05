package com.fonbulteni.bulletinservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "admin", "ROLE_ADMIN", "role_admin", " Admin "})
    void normalizesRoleClaimToRoleAuthority(String role) {
        assertThat(JwtAuthFilter.authoritiesFor(role))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingRoleGivesNoAuthorities(String role) {
        assertThat(JwtAuthFilter.authoritiesFor(role)).isEmpty();
    }

    @Test
    void uppercasingDoesNotDependOnTurkishDefaultLocale() {
        // With a tr-TR default, "admin".toUpperCase() is "ADMİN" (dotted İ) and would never match ROLE_ADMIN.
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(JwtAuthFilter.authoritiesFor("admin"))
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
