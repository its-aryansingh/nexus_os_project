package com.nexus.os.integrations.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Deterministic mock for offline development. Returns a fake Twilio-style
 * external id; logs the body so devs can verify "sends" via stdout.
 */
public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    @Override
    public SendResult send(String toPhone, String body) {
        final var externalId = "SM_mock_" + UUID.randomUUID();
        log.info("[mock-whatsapp] to={} body={}", toPhone, body);
        return new SendResult(externalId, true, true, "delivered via mock");
    }
}
