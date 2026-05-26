package com.nexus.os.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the Micrometer meter registry with global tags + sane defaults.
 * Spring Boot auto-configures the Prometheus registry from
 * {@code micrometer-registry-prometheus} on the classpath.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:nexus-os}") String app,
            @Value("${OTEL_SERVICE_NAME:nexus-os}") String otelService
    ) {
        return registry -> registry.config()
                .commonTags("application", app, "service", otelService)
                .meterFilter(MeterFilter.acceptNameStartsWith("nexus"))
                .meterFilter(MeterFilter.acceptNameStartsWith("http"))
                .meterFilter(MeterFilter.acceptNameStartsWith("jvm"))
                .meterFilter(MeterFilter.acceptNameStartsWith("resilience4j"))
                .meterFilter(MeterFilter.acceptNameStartsWith("kafka"))
                .meterFilter(MeterFilter.acceptNameStartsWith("hikaricp"));
    }
}
