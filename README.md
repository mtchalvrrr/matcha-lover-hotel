# [HOTEL NAME] — Hotel Reservation and Management System

A new, independent hotel reservation and management system built from scratch.

## Branding placeholders
Replace these values only after the hotel approves its identity:
- Hotel name: [HOTEL NAME]
- Logo: [HOTEL LOGO]
- Address: [HOTEL ADDRESS]
- Contact: [HOTEL CONTACT INFORMATION]

Do not use the previous project's Matcha Lover branding in customer-facing pages, assets, metadata, emails, or generated documents.

## Programs
The system provides three separately launchable programs backed by one application service and one shared SQLite database:
1. Customer Program
2. Admin Program
3. Super Admin Program

The two Admin accounts (Online Booking Admin and Walk-in Booking Admin) use the same ADMIN role and permissions. Their identities remain distinguishable in audit records. There is no Staff role or Staff Portal.

## Technology baseline
- Java 21
- Spring Boot
- Maven
- Thymeleaf, HTML, CSS, JavaScript
- SQLite
- Spring Security
- JPA / Hibernate

## Development and verification
This repository root is the new application's project root on the `new-hotel-system` branch. The old application files were removed from this branch only. The `main` branch remains unchanged.

Build the complete agreed scope on this branch. Run focused tests during development, then a complete build, automated test suite, database checks, security checks, and end-to-end workflow verification.

Do not claim the system is bug-free unless the relevant checks have actually passed. Record any unresolved issues clearly.

## Local development
From the repository root:
```bash
mvn clean verify
mvn spring-boot:run
```

The exact launch commands for each program will be documented as separate entry points are implemented.
