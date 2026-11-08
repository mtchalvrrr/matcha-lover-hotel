package com.hotel.system.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PayMongoWebhookController {
    private final PayMongoService payMongo;

    public PayMongoWebhookController(PayMongoService payMongo) {
        this.payMongo = payMongo;
    }

    @PostMapping(value = "/webhooks/paymongo", consumes = "application/json")
    public ResponseEntity<Map<String, String>> receive(@RequestBody String rawBody,
            @RequestHeader(name = "Paymongo-Signature", required = false) String signature) {
        try {
            payMongo.handleWebhook(rawBody, signature);
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
