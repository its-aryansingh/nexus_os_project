package com.nexus.os.integrations.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the real Twilio client when {@code TWILIO_ACCOUNT_SID} is set,
 * otherwise falls back to the deterministic mock. Honours the system-wide
 * "boots without keys" rule documented in SYSTEM_DESIGN.md §Design Goals #6.
 */
@Configuration
public class WhatsAppConfig {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean
    public WhatsAppClient whatsAppClient(
            @Value("${nexus.whatsapp.twilio.account-sid:}") String accountSid,
            @Value("${nexus.whatsapp.twilio.auth-token:}") String authToken,
            @Value("${nexus.whatsapp.twilio.from-number:+14155238886}") String fromNumber
    ) {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            log.warn("Twilio credentials not set — using MockWhatsAppClient (offline mode)");
            return new MockWhatsAppClient();
        }
        log.info("Twilio credentials present — using TwilioWhatsAppClient");
        return new TwilioWhatsAppClient(accountSid, authToken, fromNumber);
    }
}
