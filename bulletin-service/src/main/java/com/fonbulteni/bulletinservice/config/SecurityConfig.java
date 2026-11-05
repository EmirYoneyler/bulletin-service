package com.fonbulteni.bulletinservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.security.JwtAuthFilter;
import com.fonbulteni.bulletinservice.security.SecurityErrorResponder;
import com.fonbulteni.bulletinservice.service.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * Stateless JWT security. Authorization matrix (AGENT_PROMPT §7.7):
 * <ul>
 *   <li>{@code GET /api/funds/**}: public (unchanged behaviour)</li>
 *   <li>other methods on {@code /api/funds/**} and everything under {@code /api/admin/**}: {@code ROLE_ADMIN}</li>
 *   <li>{@code /api/me/**}: any authenticated user</li>
 *   <li>{@code /api/dev/**}: public, but the controller only exists in the {@code dev} profile</li>
 *   <li>anything else: authenticated (deny by default)</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityErrorResponder securityErrorResponder(ObjectMapper objectMapper, Clock clock) {
        return new SecurityErrorResponder(objectMapper, clock);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           SecurityErrorResponder errorResponder) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Uses the controllers' @CrossOrigin settings, so browser preflights are answered before auth.
            .cors(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorResponder)
                .accessDeniedHandler(errorResponder))
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/funds", "/api/funds/**").permitAll()
                .requestMatchers("/api/funds", "/api/funds/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/me/**").authenticated()
                .requestMatchers("/api/dev/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
