# Architecture

## Project isolation
This branch contains a fresh implementation at the repository root. The previous application's files are not part of this branch's working tree. The repository's `main` branch remains unchanged.

## Runtime model
Three separately launchable programs:
- Customer Program
- Admin Program
- Super Admin Program

They use one shared backend service and one SQLite database. The programs must not run separate independent SQLite writers. Shared operations are coordinated through the backend's transaction boundary and service layer.

Each program has a distinct login entry point, role enforcement, session lifecycle, and logout behavior. UI separation is not a substitute for server-side authorization.

## Roles
- CUSTOMER
- ADMIN
- SUPER_ADMIN

Online Booking Admin and Walk-in Booking Admin are two distinct ADMIN accounts with identical ADMIN permissions. Audit events record the acting account identity and its operational designation. No STAFF role exists.

## Core domain areas
- Accounts and registration applications
- Email verification and account approval
- Rooms, amenities, rates, and room photo galleries
- Availability and reservations
- Payments, receipts, and refunds
- Check-in and check-out
- Notifications and email delivery
- Hotel settings and policies
- Reports and analytics
- Audit and security events
- Hotel FAQ and AI assistant

## Critical invariants
1. A room cannot have overlapping confirmed reservations.
2. Online and walk-in bookings use the same availability service.
3. Cancelled and rejected reservations do not occupy inventory.
4. Reservation ownership and permissions are checked server-side.
5. Payment success is verified server-side; browser state is never trusted as proof.
6. Payment and reservation mutations are transactional and idempotent where applicable.
7. Referenced rooms and financial records are deactivated/voided through controlled workflows, not destructively deleted.
8. Private identity documents are never publicly addressable.
9. Audit records identify the exact account responsible for each privileged action.
10. Hotel branding remains placeholder-driven until approval.

## Explicit exclusions
- Staff Portal or staff management
- Reservation extension requests
- Reservation rating and review system
- Housekeeping, cleaning, inspection, readiness, or maintenance modules

## Requirements still to complete

The uploaded Matcha Lover Hotel specification includes a full reservation-extension workflow. The current branch does not yet implement extension request persistence, customer submission/status screens, Admin review, additional-charge Xendit checkout, webhook confirmation, or extension notifications. The entity-level checkout-date helper is not a substitute for that end-to-end workflow.

The specification also distinguishes the two Admin accounts as Online Booking Admin and Walk-in Booking Admin. The current implementation limits the number of ADMIN accounts to two and gives them identical permissions, but still needs an explicit operational designation stored and shown in audit records.

## Verification strategy
- Unit tests for domain rules and validation
- Repository and persistence tests against SQLite
- Integration tests for authentication, authorization, booking, payments, and audit
- Regression tests for time boundaries, midnight, overlap, duplicate requests, and rollback
- Security tests for access control, ownership, CSRF, validation, and private file access
- End-to-end tests for all three programs and shared data visibility
