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
- `HOTEL_MANAGER_EMAIL` — email for the initial manager account (required to seed it)
- `HOTEL_MANAGER_PASSWORD` — initial manager password (required to seed it; use a strong, unique password)
- `HOTEL_MANAGER_NAME` — optional display name for the initial manager (defaults to `Hotel Manager`)

For local development, register `localhost` as an allowed domain in Google reCAPTCHA. Login and registration intentionally fail CAPTCHA verification when valid keys are not configured.

## Manager account provisioning

The application does not contain a default manager email or password in source code.
To create the first manager account, set both `HOTEL_MANAGER_EMAIL` and
`HOTEL_MANAGER_PASSWORD` in your local environment before starting Spring Boot.
You may also set `HOTEL_MANAGER_NAME`. If the email and password are not both
configured, the room inventory is seeded but no manager account is created.

If a manager password was ever committed to a public repository, change it
immediately. Removing it from the current source file does not remove it from
Git history; rotate the credential and do not reuse it.

Passwords are BCrypt-hashed, CSRF protection is enabled, and manager routes
require the manager role. Before real deployment, add rate limiting, audit
logging, email/OTP verification if required, and database backup/restore
procedures. This application currently uses Spring Security form-login sessions,
not JWT authentication.

## Photography credits

See [`PHOTO-SOURCES.md`](PHOTO-SOURCES.md) for remote Pexels image sources and license references. Images are loaded remotely and may change or become unavailable.

## Tests

Run the test suite with:

```bash
mvn test
```

GitHub Actions runs this command on pushes and pull requests. The suite includes CAPTCHA configuration and reservation-service tests.
