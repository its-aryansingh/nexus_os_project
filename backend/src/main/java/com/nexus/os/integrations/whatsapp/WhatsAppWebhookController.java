package com.nexus.os.integrations.whatsapp;

import com.nexus.os.domain.OutboxEvent;
import com.nexus.os.domain.OutboxRepository;
import com.nexus.os.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Twilio webhook endpoint. v0.1 writes inbound messages to the outbox →
 * Kafka → InboundMessageConsumer pipeline. Twilio signature validation is
 * stubbed (the {@code nexus.whatsapp.twilio.webhook-validation} flag toggles
 * it on for v0.2).
 *
 * <p>Anti-corruption layer (SYSTEM_DESIGN.md §3.16): Twilio's flat form-
 * encoded payload ({@code From}, {@code To}, {@code Body}, {@code MessageSid},
 * {@code MediaUrl0..9}) is translated to a clean Nexus event shape here so
 * Twilio's vocabulary doesn't leak deeper into the domain.
 */
@RestController
@RequestMapping("/api/integrations/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final OutboxRepository outbox;

    public WhatsAppWebhookController(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @PostMapping(value = "/webhook", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> webhook(@RequestParam Map<String, String> form) {
        log.info("WhatsApp webhook: from={} sid={}", form.get("From"), form.get("MessageSid"));

        final var tenantId = TenantContext.get();   // v0.1: from header; v0.2: resolved by To-number → tenant
        if (tenantId == null) {
            log.warn("Webhook received without tenant context — dropping");
            return ResponseEntity.ok().build();
        }

        // Translate Twilio shape → Nexus inbound shape (anti-corruption layer)
        final var nexusPayload = Map.<String, Object>of(
                "channel", "whatsapp",
                "external_id", form.getOrDefault("MessageSid", ""),
                "from", form.getOrDefault("From", ""),
                "to", form.getOrDefault("To", ""),
                "body", form.getOrDefault("Body", ""),
                "raw", form
        );

        final var ev = new OutboxEvent();
        ev.setTenantId(tenantId);
        ev.setAggregateType("inbound_message");
        ev.setAggregateId(form.getOrDefault("MessageSid", ""));
        ev.setTopic("nexus.inbound.messages");
        ev.setPartitionKey(tenantId.toString());
        ev.setPayload(nexusPayload);
        outbox.save(ev);

        // Twilio expects 2xx within 10s; reply immediately so the outbox
        // dispatcher (200ms tick) handles publishing async.
        return ResponseEntity.ok().build();
    }
}
