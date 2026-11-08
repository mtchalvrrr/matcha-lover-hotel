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

- Database-backed authentication, role checks, BCrypt password hashing, session invalidation, failed-login tracking, and temporary lockout.
- Customer registration application with ID upload, email OTP verification, and Super Admin approval/rejection.
- Customer profile updates, password changes, expiring password-reset tokens, and customer account deactivation requests with Super Admin review and active-session expiry.
- Public room catalog with date-based availability search, guest-capacity checks, filters, sorting, room details, photo carousels/lightbox, and admin room/photo management.
- Online reservation requests and modification with shared room-date conflict checks, Admin approval/rejection, walk-in bookings, check-in, checkout, and no-show processing.
- Configurable hotel check-in/check-out times, including same-day time validation.
- 50% down payment and balance tracking, printable payment receipts, and audited refund/void status workflows. The default payment mode is simulated; it does not collect real money.
- Admin reservation search by guest, email, room, or reservation ID, with status, source, and check-in date filters; audited admin cancellation with payment-refund safeguards.
- In-app notifications and optional SMTP email notifications.
- Admin reports for reservation status, online versus walk-in bookings, paid revenue, bookings by room, daily check-in trends, booked room-night capacity, print/PDF, and CSV export.
- Hotel profile, contact, stay-time, and cancellation-policy settings.
- Super Admin account management, customer application review, and audit log.
- Customer-facing hotel FAQ assistant with optional Groq AI integration, public room-page redirection, and per-session request limiting.

## Configuration

Set secrets as environment variables or Codespaces secrets. Never commit credentials.

Super Admin bootstrap:
- `HOTEL_SUPER_ADMIN_USERNAME`
- `HOTEL_SUPER_ADMIN_EMAIL`
- `HOTEL_SUPER_ADMIN_PASSWORD` — at least 12 characters, including uppercase, lowercase, a number, and a special character.

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

Hotel stay defaults:
- `HOTEL_CHECK_IN_TIME` (default `14:00`)
- `HOTEL_CHECK_OUT_TIME` (default `12:00`)

Database and file storage:
- SQLite: `HOTEL_DB_URL` (default `jdbc:sqlite:./data/hotel-reservation-system.db`)
- Upload directory: `HOTEL_STORAGE_LOCATION` (default `./data/uploads`)

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
- Housekeeping, room cleaning, inspection, readiness, or maintenance modules.

## Remaining completion and release work

The modules above are implemented to varying levels and are not yet a release-complete system. Before calling the project complete, continue implementing and verifying the remaining agreed workflows, including fuller reservation history policies, advanced payment reconciliation and real gateway integration (if enabled), complete operational dashboards, full email delivery verification, and comprehensive end-to-end/security testing.

## Verification policy

GitHub Actions runs `mvn clean verify` on branch updates. A passing build confirms compilation and automated tests only; it does not replace browser-based, real database, payment-provider, email-provider, or full security testing. Do not describe the system as bug-free until the complete verification plan has passed.
