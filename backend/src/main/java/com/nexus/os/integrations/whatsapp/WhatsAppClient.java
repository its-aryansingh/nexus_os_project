package com.nexus.os.integrations.whatsapp;

/**
 * Channel-agnostic WhatsApp send interface. Real implementations live in
 * {@link TwilioWhatsAppClient} (production) and {@link MockWhatsAppClient}
 * (offline-friendly default).
 *
 * <p>The actual client chosen at runtime is decided in
 * {@link com.nexus.os.integrations.whatsapp.WhatsAppConfig} based on whether
 * {@code TWILIO_ACCOUNT_SID} is set.
 */
public interface WhatsAppClient {

    SendResult send(String toPhone, String body);

    /**
     * Result of a send attempt. {@code mock=true} when the deterministic
     * mock client served the request — surface this in the API response.
     */
    record SendResult(String externalId, boolean delivered, boolean mock, String detail) {}
}
