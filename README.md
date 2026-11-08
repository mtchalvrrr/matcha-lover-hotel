# [HOTEL NAME] — Hotel Reservation and Management System

A new, independent hotel reservation and management system built on the `new-hotel-system` branch. The `main` branch is kept unchanged.

## Branding

Use only approved hotel identity details. Until approval, customer-facing content uses:
- `[HOTEL NAME]`
- `[HOTEL LOGO]`
- `[HOTEL ADDRESS]`
- `[HOTEL CONTACT INFORMATION]`

Do not use the previous project's Matcha Lover branding in this application.

## Architecture

- Java 21, Spring Boot 3.4, Maven, Thymeleaf, HTML/CSS/JavaScript.
- One shared Spring Boot application service and one shared SQLite database.
- Three separate portal experiences: Customer, Admin, and Super Admin.
- Online Booking Admin and Walk-in Booking Admin are separate accounts with the same `ADMIN` role and permissions.
- There is no Staff Portal or Staff role.

The three portals are separate entry points within one coordinated backend, not three independent application servers or databases.

## Implemented modules

- Database-backed authentication, role checks, BCrypt password hashing, failed-login tracking, temporary lockout, browser security headers, and independent Customer/Admin/Super Admin sessions using separate portal cookies and logout endpoints. Automated integration coverage verifies simultaneous login and logout independence.
- Room photos and private ID uploads validate file signatures and extension/MIME consistency; cleanup prevents stored-file path traversal and removes orphaned ID files when registration cannot complete.
- Customer registration application with ID upload, exact-file duplicate fingerprint checks, email OTP verification with resend cooldown/limits and applicant-email matching for verification/resend, plus Super Admin manual ID review and approval/rejection. Rejection emails are sent when SMTP is configured.
- Customer profile updates, password changes, expiring password-reset tokens, and customer account deactivation requests with Super Admin review and active-session expiry. Admins can manage customer account status and view approved customer ID documents.
- Public room catalog with date-based availability search, guest-capacity checks, filters, sorting, room details, photo carousels/lightbox, and admin room/photo management. Admins can also update cleaning and maintenance status; only active, clean/ready, non-maintenance rooms can be newly booked. Readiness confirmation records the Admin and inspection time.
- Online reservation requests, modification, customer/admin reservation activity and payment history, shared room-date conflict checks, Admin approval/rejection/cancellation, walk-in bookings, check-in (blocked after scheduled checkout), checkout, no-show processing, customer-visible reservation history, customer reservation extension requests with Admin approval/rejection and availability rechecks, and automatic expiration of unreviewed pending requests after 24 hours by default.
- Configurable hotel check-in/check-out times, including same-day time validation.
- 50% down payment and balance tracking, payment due-date guidance, printable receipts, and audited void workflows. Refunds require a second Admin account to authorize the request. The default payment mode is simulated; it does not collect real money. A PayMongo Hosted Checkout test-mode adapter is available when configured; signed webhooks are the source of truth for hosted checkout payments. Approved PayMongo refunds are submitted to the provider after two-Admin authorization and tracked until provider confirmation. Pending checkouts are periodically reconciled through the authenticated PayMongo API, and webhook event IDs are deduplicated.
- Admin reservation search by guest, email, room, or reservation ID, with status, source, and check-in date filters; audited admin cancellation with payment-refund safeguards.
- Authorized Admin reservation editing for pending or approved stays, with room readiness/capacity/date/availability validation, optimistic locking, a strict block when any non-failed/non-voided payment activity exists, status preservation, customer notification, and before/after audit details. Admin audit-log access is separately permission-controlled; Super Admin retains system-wide audit integrity verification and CSV export.
- Super Admin can configure shared Admin capabilities (reservation editing, audit viewing, reports, payment management, customer management, room management, and notification management). Settings apply identically to both Admin accounts and are enforced server-side; new permissions default to enabled for backward compatibility.
- Room revenue reports attribute transactions currently in PAID status to the room on each reservation, separately from room popularity. The room-revenue breakdown is included in Admin and Super Admin views and CSV/PDF exports.
- In-app notifications, optional SMTP email notifications, and duplicate-safe scheduled check-in/check-out reminders.
- Live Customer, Admin, and Super Admin dashboard metrics with recent activity and today's arrivals/departures, plus daily SQLite backups, 14-copy retention, and Super Admin backup downloads; Admin reports for reservation status (including no-shows, check-ins, and checkouts), online versus walk-in bookings, unique/new/returning customers, gross payments by payment date, refunds by refund date, outstanding balances, room popularity ranked from most to least reserved, daily reservation creation and check-in-date trends, scheduled booked room-night capacity, separately calculated actual occupied room nights and actual occupancy, peak booking/stay dates, print/PDF, and CSV export.
- Hotel profile, contact, stay-time, and cancellation-policy settings are editable by Super Admin; Admins have read-only access. Super Admin can send an SMTP test email to verify provider configuration.
- Super Admin account management, customer application review and status tracking, searchable audit/security log with sign-in events, filtered CSV export, and hash-chain integrity verification (set `AUDIT_HMAC_KEY` for keyed HMAC protection), and database backup downloads.
- Customer-facing hotel assistant with FAQ responses, optional Groq AI integration, live room recommendations and date-based availability assistance, public room-page redirection, customer account/payment guidance, and per-session request limiting.

## Configuration

Set secrets as environment variables or Codespaces secrets. Never commit credentials.

Super Admin bootstrap:
- \`HOTEL_SUPER_ADMIN_USERNAME\`
- \`HOTEL_SUPER_ADMIN_EMAIL\`
- \`HOTEL_SUPER_ADMIN_PASSWORD\` — at least 12 characters, including uppercase, lowercase, a number, and a special character.

Public URL:
- \`HOTEL_PUBLIC_BASE_URL\` (default \`http://localhost:8080\`; set to the exact public HTTPS URL for password-reset links and hosted checkout redirects).

SMTP email:
- \`MAIL_HOST\`
- \`MAIL_PORT\` (default 587)
- \`MAIL_USERNAME\`
- \`MAIL_PASSWORD\`
- Super Admin can use **System settings → Send SMTP test email** to send a test message to their own account.

PayMongo test checkout (test mode only; no real money is collected):
- \`PAYMENT_MODE=PAYMONGO_TEST\`
- \`PAYMONGO_SECRET_KEY\` (test secret key beginning \`sk_test_\`)
- \`PAYMONGO_WEBHOOK_SECRET\` (the signing secret for the test webhook endpoint; not the API secret key)
- \`PAYMONGO_RECONCILE_MS\` (optional reconciliation interval; default 300000 milliseconds)
- Register \`https://YOUR_PUBLIC_HOST/webhooks/paymongo\` in the PayMongo dashboard. Subscribe to \`checkout_session.payment.paid\`, \`payment.refund.updated\`, and \`payment.refunded\`; \`payment.failed\` is also handled if delivered. The webhook must be publicly accessible over HTTPS. Localhost is not reachable by PayMongo; use a public HTTPS tunnel for local webhook testing.
- The Super Admin Payment Settings page shows whether the required secrets are configured. It never displays their values.

Google reCAPTCHA v2:
- \`CAPTCHA_ENABLED=true\`
- \`RECAPTCHA_SITE_KEY\`
- \`RECAPTCHA_SECRET_KEY\`

Audit integrity:
- \`AUDIT_HMAC_KEY\` (random secret of at least 32 bytes; enables keyed HMAC-SHA256 audit-chain protection; keep it outside the database and source control).

Optional Groq assistant:
- \`AI_ENABLED=true\`
- \`GROQ_API_KEY\`
- \`GROQ_API_URL\` (defaults to the Groq chat completions endpoint)
- \`GROQ_MODEL\` (defaults to \`llama-3.3-70b-versatile\`)

Without the Groq key and \`AI_ENABLED=true\`, the assistant uses the built-in hotel FAQ responses. The API key remains server-side.

Session cookie:
- \`SESSION_COOKIE_SECURE=true\` when deployed behind HTTPS (default false for local development).

Hotel stay defaults:
- \`HOTEL_CHECK_IN_TIME\` (default \`14:00\`)
- \`HOTEL_CHECK_OUT_TIME\` (default \`12:00\`)

Database and file storage:
- SQLite: \`HOTEL_DB_URL\` (default \`jdbc:sqlite:./data/hotel-reservation-system.db?busy_timeout=5000\`)
- Upload directory: \`HOTEL_STORAGE_LOCATION\` (default \`./data/uploads\`)
- Backup directory: \`HOTEL_BACKUP_LOCATION\` (default \`./data/backups\`)
- Backup schedule: \`HOTEL_BACKUP_CRON\` (default daily at 02:00 Asia/Manila). The 14 newest backups are retained. Super Admins can also download a fresh backup from their dashboard.

## Run locally or in Codespaces

From the repository root:

```bash
mkdir -p data
mvn clean verify
mvn spring-boot:run
```

On a local desktop, the launcher attempts to open the three portal pages in separate browser tabs. In Codespaces, open the forwarded application URL and use the three portal links; a remote Maven process cannot force tabs to open in your local browser.

## Scope exclusions

Do not add:
- Staff Portal, staff accounts, or staff management.
- Separate independent databases for each portal.
- Reservation rating or review system.

## Reservation extension workflow

Customers with approved or checked-in reservations can request a later checkout date. Admins review each request; approval rechecks overlapping room bookings, updates the reservation nights and total, records the reviewer, and notifies the customer. Any added balance is included in the updated reservation amount.

## Remaining completion and release work

The code is implemented and the automated suite passes, but the system is not yet production-ready. Remaining work is primarily external setup and end-to-end verification: configure PayMongo test credentials and register/test the webhook; configure SMTP and verify real inbox delivery; configure reCAPTCHA keys and verify the registered domain; set a strong \`AUDIT_HMAC_KEY\`; complete browser testing of all three portals, session expiration and Back/Forward behavior; test the full reservation/payment/refund workflows against the provider; and verify backups/restores. Sessions currently use an in-memory repository and require shared persistent session storage before running multiple application instances. The PayMongo adapter is test-mode only; live payment mode must not be enabled until test-mode acceptance, provider setup, and security review are complete.

## Verification policy

GitHub Actions runs `mvn clean verify` on branch updates. A passing build confirms compilation and automated tests only; it does not replace browser-based, real database, payment-provider, email-provider, or full security testing. Do not describe the system as bug-free until the complete verification plan has passed.
