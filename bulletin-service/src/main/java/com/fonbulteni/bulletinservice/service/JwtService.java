package com.fonbulteni.bulletinservice.service;

import com.fonbulteni.bulletinservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    /** Identity carried by a verified token: subject (e-mail) and the {@code role} claim. */
    public record TokenClaims(String email, String role) {
    }

    private final SecretKey key;
    private final long expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expiration;
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return parseClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies signature and expiry. Returns empty for any invalid, expired or malformed token,
     * so callers never have to deal with jjwt exceptions.
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = parseClaims(token);
            return Optional.of(new TokenClaims(claims.getSubject(), claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
