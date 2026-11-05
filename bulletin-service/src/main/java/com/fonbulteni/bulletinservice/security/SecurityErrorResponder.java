package com.fonbulteni.bulletinservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;

/** Writes 401/403 responses from the security filter chain in the same {@link ApiError} shape as the API. */
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityErrorResponder(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Bu işlem için geçerli bir token gerekiyor.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String code, String message) throws IOException {
        ApiError body = new ApiError(OffsetDateTime.now(clock), status.value(), code, message,
                request.getRequestURI(), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
