package com.hotel.system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableSpringHttpSession
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    MapSessionRepository sessionRepository() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(20));
        return repository;
    }

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        return new PortalSessionIdResolver(secure);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LoginAttemptAuthenticationProvider loginAttemptAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttempts,
            CaptchaService captcha) {
        return new LoginAttemptAuthenticationProvider(userDetailsService, passwordEncoder, loginAttempts, captcha);
    }

    @Bean
    @Order(1)
    SecurityFilterChain customerSecurity(HttpSecurity http,
                                         PortalAuthenticationSuccessHandler successHandler,
                                         PortalAuthenticationFailureHandler failureHandler,
                                         SessionRegistry sessionRegistry,
                                         LoginAttemptAuthenticationProvider authenticationProvider) throws Exception {
        http.securityMatcher("/customer/**")
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/customer", "/customer/login").permitAll()
                .anyRequest().hasRole("CUSTOMER"))
            .formLogin(form -> form
                .loginPage("/customer/login")
                .loginProcessingUrl("/customer/authenticate")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/customer/logout")
                .logoutSuccessUrl("/customer/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/customer/login")))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(3).sessionRegistry(sessionRegistry));
        applySecurityHeaders(http);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminSecurity(HttpSecurity http,
                                      PortalAuthenticationSuccessHandler successHandler,
                                      PortalAuthenticationFailureHandler failureHandler,
                                      SessionRegistry sessionRegistry,
                                      LoginAttemptAuthenticationProvider authenticationProvider) throws Exception {
        http.securityMatcher("/admin/**")
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin", "/admin/login").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/authenticate")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/admin/login")))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(3).sessionRegistry(sessionRegistry));
        applySecurityHeaders(http);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain superAdminSecurity(HttpSecurity http,
                                           PortalAuthenticationSuccessHandler successHandler,
                                           PortalAuthenticationFailureHandler failureHandler,
                                           SessionRegistry sessionRegistry,
                                           LoginAttemptAuthenticationProvider authenticationProvider) throws Exception {
        http.securityMatcher("/super-admin/**")
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/super-admin", "/super-admin/login").permitAll()
                .anyRequest().hasRole("SUPER_ADMIN"))
            .formLogin(form -> form
                .loginPage("/super-admin/login")
                .loginProcessingUrl("/super-admin/authenticate")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/super-admin/logout")
                .logoutSuccessUrl("/super-admin/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/super-admin/login")))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(3).sessionRegistry(sessionRegistry));
        applySecurityHeaders(http);
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain publicSecurity(HttpSecurity http,
                                       LoginAttemptAuthenticationProvider authenticationProvider) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/xendit"))
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/customer", "/admin", "/super-admin",
                        "/api/chat", "/register", "/register/**", "/registration/status",
                        "/forgot-password", "/reset-password", "/rooms", "/rooms/**",
                        "/css/**", "/js/**", "/images/**", "/room-images/**", "/webhooks/paymongo", "/webhooks/xendit", "/favicon.ico", "/error")
                .permitAll()
                .anyRequest().authenticated());
        applySecurityHeaders(http);
        return http.build();
    }

    private void applySecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .frameOptions(frame -> frame.deny())
            .contentTypeOptions(Customizer.withDefaults())
            .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .cacheControl(Customizer.withDefaults())
            .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=()"))
            .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
                    + "font-src 'self' data:; connect-src 'self' https://api.groq.com https://www.google.com/recaptcha/ "
                    + "https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ "
                    + "https://recaptcha.google.com/recaptcha/; object-src 'none'; base-uri 'self'; "
                    + "form-action 'self'; frame-ancestors 'none'"))
            .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)));
    }
}
