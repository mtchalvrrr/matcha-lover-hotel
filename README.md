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

- Database-backed authentication, role checks, BCrypt password hashing, failed-login tracking, temporary lockout, browser security headers, and independent Customer/Admin/Super Admin sessions using separate portal cookies and logout endpoints. Automated integration coverage verifies simultaneous login and logout independence.\n- Room photos and private ID uploads validate file signatures and extension/MIME consistency; cleanup prevents stored-file path traversal and removes orphaned ID files when registration cannot complete.
- Customer registration application with ID upload, exact-file duplicate fingerprint checks, email OTP verification with resend cooldown/limits and applicant-email matching for verification/resend, plus Super Admin manual ID review and approval/rejection.
- Customer profile updates, password changes, expiring password-reset tokens, and customer account deactivation requests with Super Admin review and active-session expiry.
- Public room catalog with date-based availability search, guest-capacity checks, filters, sorting, room details, photo carousels/lightbox, and admin room/photo management. Admins can also update cleaning and maintenance status; only active, clean/ready, non-maintenance rooms can be newly booked. Readiness confirmation records the Admin and inspection time.
- Online reservation requests, modification, customer/admin reservation activity and payment history, shared room-date conflict checks, Admin approval/rejection/cancellation, walk-in bookings, check-in (blocked after scheduled checkout), checkout, no-show processing, customer-visible reservation history, and automatic expiration of unreviewed pending requests after 24 hours by default.
- Configurable hotel check-in/check-out times, including same-day time validation.
- 50% down payment and balance tracking, payment due-date guidance, printable receipts, and audited void workflows. Refunds now require a second Admin account to authorize the request. Approval records the refund state in the hotel database but does not itself transfer money; settlement must be completed through the original payment channel. The default payment mode is simulated; it does not collect real money.
- Admin reservation search by guest, email, room, or reservation ID, with status, source, and check-in date filters; audited admin cancellation with payment-refund safeguards.
- In-app notifications, optional SMTP email notifications, and duplicate-safe scheduled check-in/check-out reminders.
- Live Customer, Admin, and Super Admin dashboard metrics with recent activity and today's arrivals/departures, plus daily SQLite backups, 14-copy retention, and Super Admin backup downloads; Admin reports for reservation status (including no-shows, check-ins, and checkouts), online versus walk-in bookings, unique/new/returning customers, gross payments by payment date, refunds by refund date, outstanding balances, room popularity ranked from most to least reserved, daily reservation creation and check-in-date trends, scheduled booked room-night capacity, separately calculated actual occupied room nights and actual occupancy, peak booking/stay dates, print/PDF, and CSV export.
- Hotel profile, contact, stay-time, and cancellation-policy settings.
- Super Admin account management, customer application review and status tracking, searchable audit/security log with sign-in events and filtered CSV export, and database backup downloads.
- Customer-facing hotel FAQ assistant with optional Groq AI integration, public room-page redirection, and per-session request limiting.

## Configuration

Set secrets as environment variables or Codespaces secrets. Never commit credentials.

Super Admin bootstrap:
- `HOTEL_SUPER_ADMIN_USERNAME`
- `HOTEL_SUPER_ADMIN_EMAIL`
- `HOTEL_SUPER_ADMIN_PASSWORD` — at least 12 characters, including uppercase, lowercase, a number, and a special character.

Public URL used in password-reset emails:
- `HOTEL_PUBLIC_BASE_URL` (default `http://localhost:8080`; set to the exact HTTPS Codespaces/deployment URL when not running locally)

SMTP email:
- `MAIL_HOST`
- `MAIL_PORT` (default 587)
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

Optional Groq assistant:
- `AI_ENABLED=true`
- `GROQ_API_KEY`
- `GROQ_API_URL` (defaults to the Groq chat completions endpoint)
- `GROQ_MODEL` (defaults to `llama-3.3-70b-versatile`)

Without the Groq key and `AI_ENABLED=true`, the assistant uses the built-in hotel FAQ responses. The API key remains server-side.

Session cookie:
- `SESSION_COOKIE_SECURE=true` when deployed behind HTTPS (default false for local development).

Hotel stay defaults:
- `HOTEL_CHECK_IN_TIME` (default `14:00`)
- `HOTEL_CHECK_OUT_TIME` (default `12:00`)

Database and file storage:
- SQLite: `HOTEL_DB_URL` (default `jdbc:sqlite:./data/hotel-reservation-system.db?busy_timeout=5000`)
- Upload directory: `HOTEL_STORAGE_LOCATION` (default `./data/uploads`)
- Backup directory: `HOTEL_BACKUP_LOCATION` (default `./data/backups`)
- Backup schedule: `HOTEL_BACKUP_CRON` (default daily at 02:00 Asia/Manila). The 14 newest backups are retained. Super Admins can also download a fresh backup from their dashboard.

To restore a backup, stop the application, preserve the current database file, copy the chosen backup over the configured SQLite database file, and restart the application. Keep backup files in private storage; they contain personal and reservation data.

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
- Reservation extension requests.
- Reservation rating or review system.

## Remaining completion and release work

The modules above are implemented to varying levels and are not yet a release-complete system. Before calling the project complete, continue implementing and verifying the remaining agreed workflows, including PayMongo test-mode checkout and verified webhook handling (not yet integrated), CAPTCHA configuration, stronger cross-instance session storage if the application is scaled beyond one process, complete payment reconciliation and actual refund settlement integration, full email-provider delivery verification, additional security controls, and comprehensive end-to-end/browser testing. Housekeeping, room readiness, actual-occupancy reporting, PDF report export, and independent portal sessions have been added but still need full manual browser verification.

## Verification policy

GitHub Actions runs `mvn clean verify` on branch updates. A passing build confirms compilation and automated tests only; it does not replace browser-based, real database, payment-provider, email-provider, or full security testing. Do not describe the system as bug-free until the complete verification plan has passed.
