# Matcha Lover Hotel — system-cycle traceability

This checklist is based on the uploaded **MATCHA LOVER HOTEL — COMPLETE SYSTEM CYCLE AND FUNCTIONS** specification. Statuses below describe code found in the `new-hotel-system` branch after the spec-alignment fixes; a page or class alone is not treated as proof that a complete workflow is delivered.

## Portal and account requirements

| Requirement | Status | Evidence / remaining work |
|---|---|---|
| Customer, Admin, Super Admin portals | Implemented in code | Separate role model, login routes, security chains, dashboards and controllers. Browser E2E testing remains. |
| Two Admin accounts with identical permissions | Partial | Account service caps ADMIN accounts at two. It does not distinguish Online Booking Admin from Walk-in Booking Admin in the account model/audit display. |
| Customer email OTP | Implemented in code | Registration OTP generation, expiry, verification and resend flow. SMTP credentials/domain still need provider testing. |
| Customer ID front/back upload | Implemented in code | Private document storage, file validation and protected review endpoints. Test with real upload formats and permissions. |
| Super Admin registration approval/rejection | Implemented in code | Pending application review and account creation on approval. |
| Profile, forgot password and reset | Implemented in code | Profile/password and password recovery controllers/services. Verify email delivery and session invalidation in browser. |
| Admin account create/activate/deactivate/reset | Implemented in code | Super Admin account management services and controllers. Two-role designation remains partial. |

## Reservation and payment cycles

| Requirement | Status | Evidence / remaining work |
|---|---|---|
| Browse/search/filter room catalogue and details | Implemented in code | Room browser, details, capacity, amenities, pricing and gallery modules. Browser test filters and gallery controls. |
| Shared availability for online and walk-in bookings | Implemented in code | Both paths use ReservationService and the same overlap query. Concurrency integration testing remains important. |
| Online reservation then 100% full payment through Xendit | Implemented in code after this branch's payment guard | Standard Xendit checkout now accepts only PAYMENT_PENDING reservations and always charges the complete reservation total. |
| Payment webhook and reconciliation | Implemented in code | Xendit callback token validation, amount/session checks, idempotency record and reconciliation scheduler. Must test with Xendit Test Mode. |
| Payment failure and retry | Implemented in code | Expired/failed checkout can be retried; verify provider callbacks and UI behavior. |
| Admin approval/rejection after payment | Implemented in code | Verified payment moves the reservation to PENDING for Admin review; rejection creates a separately authorized refund request. |
| Walk-in reservation and full onsite payment | Implemented in code | WalkInBookingService creates reservation and payment transactionally. |
| Check-in/check-out | Implemented in code | Admin actions verify approval and full payment and record timestamps. |
| Cancellation and refund | Implemented in code | Cancellation and two-Admin refund authorization workflow. Provider refund result is webhook-tracked. |
| Reservation extension request/review/additional payment | **Missing** | The Reservation entity has an extension-date helper, but no complete request entity, customer flow, Admin review, extension-specific Xendit payment, webhook completion or notification flow exists. |
| Pending reservation VOID at check-in | Implemented in this branch | A PENDING reservation becomes VOID at its scheduled check-in time if Admin has not reviewed it. Stale requests before check-in remain EXPIRED; approved no-shows remain NO_SHOW. Regression test added. |

## Administration and shared services

| Requirement | Status | Evidence / remaining work |
|---|---|---|
| Room add/edit/activate/deactivate, rates, capacity, amenities | Implemented in code | RoomService and Admin room pages. |
| Room photo upload/gallery | Implemented in code | Private storage and room photo controllers/services. |
| Customer account management and ID review | Implemented in code | Admin customer controller and protected ID document retrieval. |
| Reports and analytics | Implemented in code | Date-range reports, reservation/payment/occupancy metrics, CSV and PDF exports. VOID count added to reports. |
| Notifications | Implemented in code | In-app notifications, optional SMTP delivery, scheduled reminders. Test SMTP externally. |
| Audit logs and integrity checks | Implemented in code | AuditService with hash chain and optional HMAC key; verify configured key and review permissions. |
| AI hotel assistant | Implemented in code | Hotel assistant and optional Groq integration; verify key, prompt boundaries and customer-facing responses. |
| Shared database and portal permissions | Implemented in code | One Spring Boot backend/SQLite database and role-specific security chains. Multi-instance session persistence is not implemented; current in-memory sessions are documented. |

## Security and verification caveats

- Xendit is **Test Mode only**. Set `PAYMENT_MODE=XENDIT_TEST`, a development API key, webhook token and public HTTPS base URL. Do not use live credentials for this project test.
- CAPTCHA is configuration-dependent; it is disabled by default unless `CAPTCHA_ENABLED=true` and valid keys are supplied.
- Sessions are held in an in-memory repository; multi-instance deployment needs persistent shared session storage.
- GitHub Actions passing `mvn clean verify` confirms compilation and automated tests, not external Xendit/SMTP delivery or every browser button.
- Browser verification still needs the full three-portal workflow, refresh, Back/Forward, session expiry, permissions, file upload, payment callbacks, duplicate webhooks, refund outcomes, and SQLite restart/backup tests.

## Current priority order

1. Complete the extension request + approval + additional Xendit payment workflow.
2. Add explicit Online Booking Admin / Walk-in Booking Admin designations and include them in audit displays.
3. Run and record Xendit Test Mode and SMTP provider tests.
4. Run end-to-end browser tests for every form, action, navigation control and role boundary.
