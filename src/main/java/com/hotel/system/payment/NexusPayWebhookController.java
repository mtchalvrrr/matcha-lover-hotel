package com.hotel.system.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Receives and verifies signed NexusPay sandbox payment events. */
@RestController
public class NexusPayWebhookController {
    private final NexusPayService nexusPay;

    public NexusPayWebhookController(NexusPayService nexusPay) {
        this.nexusPay = nexusPay;
    }

    @PostMapping(value = "/webhooks/nexuspay", consumes = "application/json")
    public ResponseEntity<Map<String, String>> receive(@RequestBody byte[] rawBody,
            @RequestHeader(name = "X-NexusPay-Signature", required = false) String signature) {
        try {
            nexusPay.handleWebhook(rawBody, signature);
            return ResponseEntity.ok(Map.of("received", "true"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("received", "false", "error", "invalid_webhook"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("received", "false", "error", "webhook_unavailable"));
        }
    }
}
