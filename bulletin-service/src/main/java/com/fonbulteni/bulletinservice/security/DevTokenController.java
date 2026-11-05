package com.fonbulteni.bulletinservice.security;

import com.fonbulteni.bulletinservice.service.JwtService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-development helper that mints tokens without auth-service. Exists only in the
 * {@code dev} profile; in any other profile the path is a plain 404.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevTokenController {

    private static final Logger log = LoggerFactory.getLogger(DevTokenController.class);

    public record DevTokenResponse(String token, String email, String role, long expiresInMs) {
    }

    private final JwtService jwtService;

    public DevTokenController(JwtService jwtService) {
        this.jwtService = jwtService;
        log.warn("'dev' profile is active: POST /api/dev/token issues tokens to anyone. Never use it in production.");
    }

    @PostMapping("/token")
    public DevTokenResponse token(
            @RequestParam
            @NotBlank(message = "E-posta boş olamaz.")
            @Email(message = "Geçerli bir e-posta adresi girin.")
            String email,
            @RequestParam(defaultValue = "USER")
            @Pattern(regexp = "USER|ADMIN", message = "Rol USER veya ADMIN olmalı.")
            String role) {
        return new DevTokenResponse(jwtService.generateToken(email, role), email, role, jwtService.getExpirationMs());
    }
}
