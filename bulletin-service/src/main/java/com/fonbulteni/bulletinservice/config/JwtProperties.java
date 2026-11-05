package com.fonbulteni.bulletinservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * JWT settings bound from {@code jwt.*}. The secret is validated at startup so that a missing or
 * weak key fails fast instead of surfacing later as a token error.
 *
 * @param secret     HMAC key shared with auth-service, from {@code JWT_SECRET}
 * @param expiration token lifetime in milliseconds
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiration) {

    /** HS256 requires a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new InvalidJwtSecretException(
                    "JWT_SECRET ortam değişkeni boş. Uygulama imzalama anahtarı olmadan başlatılmaz.");
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new InvalidJwtSecretException(
                    "JWT_SECRET en az " + MIN_SECRET_BYTES + " bayt olmalı, şu an " + length + " bayt.");
        }
        if (expiration <= 0) {
            throw new IllegalArgumentException("jwt.expiration pozitif olmalı (milisaniye).");
        }
    }
}
