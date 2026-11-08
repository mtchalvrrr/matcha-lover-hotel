package com.hotel.system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class CaptchaService {
    private final RestClient client;
    private final boolean enabled;
    private final String secret;

    public CaptchaService(RestClient.Builder builder,
            @Value("${captcha.enabled:false}") boolean enabled,
            @Value("${captcha.secret-key:}") String secret) {
        this.client = builder.baseUrl("https://www.google.com").build();
        this.enabled = enabled;
        this.secret = secret == null ? "" : secret.trim();
    }

    public boolean isEnabled() { return enabled; }

    public boolean verify(String token, String remoteAddress) {
        if (!enabled) return true;
        if (secret.isBlank() || token == null || token.isBlank() || token.length() > 4096) return false;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secret);
            form.add("response", token);
            if (remoteAddress != null && !remoteAddress.isBlank()) form.add("remoteip", remoteAddress);
            Map<?, ?> response = client.post().uri("/recaptcha/api/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
