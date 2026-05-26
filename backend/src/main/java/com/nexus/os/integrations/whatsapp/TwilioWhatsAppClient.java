package com.nexus.os.integrations.whatsapp;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real Twilio implementation. v0.1 ships the wiring + Resilience4j
 * decoration; the actual HTTP call is a thin sketch (Twilio Java SDK is
 * added in v0.2 — we keep the SDK off the v0.1 dependency tree to stay
 * mock-friendly).
 *
 * <p>The {@code twilio} circuit breaker config in application.yml opens
 * after 50% failure rate over a sliding window of 10 calls.
 */
public class TwilioWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppClient.class);

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioWhatsAppClient(String accountSid, String authToken, String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    @CircuitBreaker(name = "twilio", fallbackMethod = "fallback")
    @Retry(name = "openai")  // reuse the openai retry policy until we add a 'twilio' one
    public SendResult send(String toPhone, String body) {
        log.info("[twilio-stub] would POST to https://api.twilio.com/2010-04-01/Accounts/{}/Messages.json", accountSid);
        // v0.2: real HTTP POST with Basic Auth (sid:token), form-encoded From/To/Body.
        return new SendResult("SM_stub_" + System.currentTimeMillis(), true, false, "twilio stub (v0.1)");
    }

    @SuppressWarnings("unused")
    private SendResult fallback(String toPhone, String body, Throwable t) {
        log.warn("Twilio circuit open or call failed ({}); falling back", t.getMessage());
        return new SendResult(null, false, false, "twilio unavailable: " + t.getMessage());
    }
}
