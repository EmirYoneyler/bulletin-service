package com.fonbulteni.bulletinservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef"; // exactly 32 bytes

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsMissingSecret(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, 1000))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("boş");
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET.substring(1), 1000))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("en az 32 bayt")
                .hasMessageContaining("31 bayt");
    }

    @Test
    void countsBytesNotCharacters() {
        // 16 Turkish characters, each 2 bytes in UTF-8 → 32 bytes, accepted.
        String turkish = "ğüşıöçĞÜŞİÖÇğüşı";
        assertThat(turkish).hasSize(16);
        assertThat(new JwtProperties(turkish, 1000).secret()).isEqualTo(turkish);
    }

    @Test
    void acceptsSecretOfExactly32Bytes() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, 86_400_000);
        assertThat(properties.secret()).isEqualTo(VALID_SECRET);
        assertThat(properties.expiration()).isEqualTo(86_400_000);
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt.expiration");
    }
}
