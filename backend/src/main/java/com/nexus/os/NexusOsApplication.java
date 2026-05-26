package com.nexus.os;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nexus OS — AI Digital Workforce Management System.
 *
 * <p>Entry point for the Spring Boot application. Virtual threads are enabled
 * via {@code application.yml} ({@code spring.threads.virtual.enabled: true}).
 * {@link EnableScheduling} drives the outbox dispatcher; {@link EnableCaching}
 * exposes Caffeine-backed {@code @Cacheable} caches alongside the explicit
 * {@code PromptCache} multi-tier path.
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@EnableCaching
public class NexusOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusOsApplication.class, args);
    }
}
