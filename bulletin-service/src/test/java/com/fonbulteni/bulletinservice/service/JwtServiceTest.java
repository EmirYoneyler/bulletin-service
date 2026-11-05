package com.fonbulteni.bulletinservice.service;

import com.fonbulteni.bulletinservice.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService service =
            new JwtService(new JwtProperties("test-only-jwt-secret-with-at-least-32-bytes", 60_000));

    @Test
    void tokenRoundTripKeepsSubject() {
        String token = service.generateToken("emir@example.com", "ADMIN");

        assertThat(service.isTokenValid(token)).isTrue();
        assertThat(service.extractEmail(token)).isEqualTo("emir@example.com");
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService(new JwtProperties("another-secret-that-is-also-32-bytes-long", 60_000));
        String foreignToken = other.generateToken("emir@example.com", "ADMIN");

        assertThat(service.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void rejectsGarbage() {
        assertThat(service.isTokenValid("not-a-jwt")).isFalse();
    }
}
