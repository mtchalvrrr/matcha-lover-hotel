package com.hotel.system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CaptchaModelAdvice {
    private final boolean enabled;
    private final String siteKey;

    public CaptchaModelAdvice(@Value("${captcha.enabled:false}") boolean enabled,
                              @Value("${captcha.site-key:}") String siteKey) {
        this.enabled = enabled;
        this.siteKey = siteKey == null ? "" : siteKey;
    }

    @ModelAttribute("captchaEnabled")
    public boolean captchaEnabled() { return enabled; }

    @ModelAttribute("captchaSiteKey")
    public String captchaSiteKey() { return siteKey; }
}
