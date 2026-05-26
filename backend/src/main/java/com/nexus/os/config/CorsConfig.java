package com.nexus.os.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.List;

/**
 * Browser allowlist. The frontend BFF (Next.js route handlers) is the
 * primary caller in production; this filter exists for direct dev-time
 * fetches (Swagger, Postman in browser, the dashboard's SSE stream
 * crossing :3000 → :8081).
 *
 * <p>v0.1 is permissive on localhost. v0.2 reads
 * {@code nexus.cors.allowed-origins} from per-tenant config.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${nexus.cors.allowed-origins:http://localhost:3000,http://localhost:3001}") String allowedOrigins
    ) {
        final var config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Tenant-Id", "X-Event-Id",
                "Accept", "Origin", "traceparent", "tracestate"
        ));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Trace-Id"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setMaxAge(Duration.ofHours(1));

        final var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/.well-known/**", config);
        source.registerCorsConfiguration("/actuator/**", config);
        return new CorsFilter(source);
    }
}
