# Matcha Lover Hotel

A local hotel reservation and management starter built with Java 21, Spring Boot, Maven, SQLite, Thymeleaf, HTML, CSS, and vanilla JavaScript.

## Requirements

- JDK 21
- Maven 3.9 or newer
- Internet access on the first build so Maven can download dependencies

## Run locally

From the repository root:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. The local SQLite database is created at `./data/mtchlvr.db`.

## Main functionality

- Room browsing and room availability
- Customer registration and shared login/logout
- Server-validated Google reCAPTCHA v2 checkbox
- Customer reservation list and cancellation requests
- Manager dashboard and room inventory management
- Manager approval checks for overlapping active reservations
- Seeded inventory of 12 rooms (3 each: Standard, Deluxe, Suite, Family)
- Room availability schedule popup and reservation history views
- Groq-powered hotel chatbot integration (requires an API key)
- Responsive green-and-white UI, accessible focus states, and reduced-motion support

## Environment configuration

The application reads secrets from environment variables; never commit real credentials or API keys.

- `RECAPTCHA_SITE_KEY` — public reCAPTCHA v2 checkbox site key
- `RECAPTCHA_SECRET_KEY` — private reCAPTCHA verification key
- `GROQ_API_KEY` — Groq API key for the chatbot

For local development, register `localhost` as an allowed domain in Google reCAPTCHA. Login and registration intentionally fail CAPTCHA verification when valid keys are not configured.

## Demo account and security warning

The current `DataSeeder` creates a demo manager account with credentials defined in source code. This is for local capstone testing only. **Keep this repository private until those demo credentials are moved to environment-based configuration and the default password is changed.** Do not expose this app to the internet or use real customer data as-is.

Passwords are BCrypt-hashed, CSRF protection is enabled, manager routes require the manager role, and server-side input validation is used. Before real deployment, add rate limiting, safer manager account provisioning, audit logging, robust email/OTP abuse controls, and database backup/restore procedures.

## Photography credits

See [`PHOTO-SOURCES.md`](PHOTO-SOURCES.md) for remote Pexels image sources and license references. Images are loaded remotely and may change or become unavailable.

## Tests

Run the test suite with:

```bash
mvn test
```

GitHub Actions runs this command on pushes and pull requests. The suite includes CAPTCHA configuration and reservation-service tests.
