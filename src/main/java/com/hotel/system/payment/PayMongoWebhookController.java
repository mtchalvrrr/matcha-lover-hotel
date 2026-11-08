package com.hotel.system.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PayMongoWebhookController {
    private final PayMongoService payMongo;

    public PayMongoWebhookController(PayMongoService payMongo) {
        this.payMongo = payMongo;
    }

    @PostMapping(value = "/webhooks/paymongo", consumes = "application/json")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader(name = "Paymongo-Signature", required = false) String signature) {
        try {
            payMongo.handleWebhook(rawBody, signature);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
