# Hotel Reservation and Management System

Independent hotel reservation and management system on the `new-hotel-system` branch. The `main` branch is kept unchanged.

## Technology
- Java 21, Spring Boot 3.4, Maven, Thymeleaf, HTML, CSS, and JavaScript.
- Shared Spring Boot application and SQLite database.
- Separate Customer, Admin, and Super Admin portals.
- No Staff role or Staff portal.

## Main functions
- Customer registration with identity-document upload, email OTP verification, Super Admin application review, and account status management.
- Customer profiles, password changes, password recovery, account deactivation requests, and session invalidation.
- Room browsing, date availability, guest-capacity checks, filters, sorting, room details, photo galleries, and room management.
- Reservation creation, full online payment before Admin review, reservation modification, cancellation, history, and automatic expiry of unpaid holds or stale pending requests. Reservation extension requests are part of the required system scope and are tracked as an outstanding implementation gap.
- Admin reservation approval/rejection, walk-in bookings, check-in, checkout, no-show handling, and room readiness management.
- Xendit hosted checkout in Test Mode, verified webhooks, payment reconciliation, payment receipts, and two-Admin refund authorization.
- Customer support conversations, in-app notifications, optional SMTP email, and scheduled stay reminders.
- Admin and Super Admin dashboards, audit logs, reports, CSV/PDF exports, account/permission management, and SQLite backups.
- Hotel settings, customer-facing hotel assistant, and optional Groq AI integration.

## Configuration

Set configuration as environment variables or Codespaces secrets. Never commit credentials.

### Xendit Test Mode only
- `PAYMENT_MODE=XENDIT_TEST`
- `XENDIT_SECRET_KEY` — development/test API key.
- `XENDIT_WEBHOOK_TOKEN` — callback verification token.
- `XENDIT_RECONCILE_MS` — optional interval; default 60000 milliseconds.
- `HOTEL_PUBLIC_BASE_URL` — public HTTPS application URL.
- Register `https://YOUR_PUBLIC_HOST/webhooks/xendit` in Xendit and enable Payment Session completed/expired and refund succeeded/failed events.
- Use Test Mode credentials only. Do not enable live payments.

The Super Admin Payment Settings page displays readiness without exposing credential values.

### Super Admin bootstrap
- `HOTEL_SUPER_ADMIN_USERNAME`
- `HOTEL_SUPER_ADMIN_EMAIL`
- `HOTEL_SUPER_ADMIN_PASSWORD` — at least 12 characters, including uppercase, lowercase, a number, and a special character.

### Email
- `MAIL_HOST`
- `MAIL_PORT` — default 587.
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

### reCAPTCHA
- `CAPTCHA_ENABLED=true`
- `RECAPTCHA_SITE_KEY`
- `RECAPTCHA_SECRET_KEY`

### Audit integrity
- `AUDIT_HMAC_KEY` — random secret of at least 32 bytes; keep it outside the database and source control.

### Optional Groq assistant
- `AI_ENABLED=true`
- `GROQ_API_KEY`
- `GROQ_API_URL`
- `GROQ_MODEL`

### Session and hotel settings
- `SESSION_COOKIE_SECURE=true` when using HTTPS.
- `HOTEL_CHECK_IN_TIME` — default 14:00.
- `HOTEL_CHECK_OUT_TIME` — default 12:00.

### Database and storage
- `HOTEL_DB_URL` — default `jdbc:sqlite:./data/hotel-reservation-system.db?busy_timeout=5000`.
- `HOTEL_STORAGE_LOCATION` — default `./data/uploads`.
- `HOTEL_BACKUP_LOCATION` — default `./data/backups`.
- `HOTEL_BACKUP_CRON` — default daily at 02:00 Asia/Manila. The 14 newest backups are retained.

## Run locally or in Codespaces

From the repository root:

```bash
mkdir -p data
mvn clean verify
mvn spring-boot:run
```

## Excluded features
- Staff portal, staff accounts, or staff management.
- Separate databases for each portal.
- Reservation ratings and reviews.

## Known specification gaps

- The uploaded Matcha Lover Hotel system cycle requires reservation extension requests, Admin approval/rejection, extension charge calculation, and additional Xendit payment. The current branch has a date-extension method on the reservation entity but does not yet have the complete request, review, payment, webhook, and notification workflow. Do not present reservation extensions as completed until that workflow is implemented and tested.
- The requirements distinguish an Online Booking Admin and a Walk-in Booking Admin. The current account model supports two ADMIN accounts with identical permissions, but does not yet store or display those two operational designations separately.
- The `new-hotel-system` branch uses the explicit `VOID` status for pending reservations that reach check-in without Admin review only after the spec-alignment changes in this branch. `EXPIRED` remains the state for stale pending requests before check-in; `NO_SHOW` remains for approved reservations not checked in after the allowed grace period.

## Remaining verification

The automated test suite does not replace browser-based and provider testing. Before production use, verify Xendit Test Mode checkout, webhook delivery, reconciliation and refunds; SMTP delivery; reCAPTCHA domain configuration; backups and restore; session expiry and Back/Forward behavior; and the full Customer, Admin, and Super Admin workflows. Sessions currently use an in-memory repository, so shared persistent session storage is required before running multiple application instances.

## Verification policy

GitHub Actions runs `mvn clean verify` on branch updates. A passing build confirms compilation and automated tests only; it does not establish that external payment/email services or every browser workflow has been tested.
