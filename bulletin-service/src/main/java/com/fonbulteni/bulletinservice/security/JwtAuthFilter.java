package com.fonbulteni.bulletinservice.security;

import com.fonbulteni.bulletinservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Authenticates requests that carry {@code Authorization: Bearer <jwt>} signed with the secret
 * shared with auth-service. The subject (e-mail) becomes the principal and the {@code role} claim
 * a {@code ROLE_*} authority.
 *
 * <p>A missing or invalid token leaves the request anonymous; the authorization rules in
 * {@link com.fonbulteni.bulletinservice.config.SecurityConfig} then decide whether that is enough (public GETs) or a 401.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parse(token)
                    .filter(claims -> claims.email() != null && !claims.email().isBlank())
                    .ifPresent(claims -> authenticate(request, claims));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, JwtService.TokenClaims claims) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                claims.email(), null, authoritiesFor(claims.role()));
        authentication.setDetails(detailsSource.buildDetails(request));

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
    }

    /** {@code "admin"}, {@code "ADMIN"} and {@code "ROLE_ADMIN"} all map to {@code ROLE_ADMIN}. */
    static List<GrantedAuthority> authoritiesFor(String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return List.of(new SimpleGrantedAuthority(normalized));
    }
}
