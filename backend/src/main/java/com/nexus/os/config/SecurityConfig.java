package com.nexus.os.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * v0.1: permissive — tenant identity comes from the {@code X-Tenant-Id}
 * header via {@link com.nexus.os.tenancy.TenantFilter}. CSRF disabled
 * because all endpoints are JSON / API-only (no browser-form posts).
 *
 * <p>v0.2 swaps this for OAuth2 resource-server with JWT issuance via
 * Auth0/Keycloak; tenant id moves from header to JWT claim. The dependency
 * (spring-boot-starter-oauth2-resource-server) is already present.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain v01PermissiveChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/api/integrations/whatsapp/webhook").permitAll()
                        .requestMatchers("/api/**").permitAll()        // tightened in v0.2
                        .requestMatchers("/").permitAll()
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
