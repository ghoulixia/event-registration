# Core Project Rules & Architectural Guidelines — Event Registration System

## 1. Git & Commit Conventions (MANDATORY)

> **CRITICAL RULE**: NEVER execute `git commit` or `git push` without EXPLICIT permission from the user!

When authorized to commit, use the following team commit message conventions:

| Scope Prefix | Description | Example |
| :--- | :--- | :--- |
| `chore:` | Setup, configuration, dependency updates, Docker Compose, build scripts | `chore: configure Flyway` |
| `auth:` | Authentication, authorization, User module, security (Member 1 / TV1) | `auth: handle duplicate email` |
| `event:` | Event management module, CRUD, filters, upload, soft delete (Member 2 / TV2) | `event: add Event entity and migration` |
| `registration:` | Event registration, booking, capacity enforcement (Member 3 / TV3) | `registration: add registration migration` |
| `docs:` | Documentation (README, architecture specs, API contract, Q&A notes) | `docs: update api contract endpoints` |
| `frontend:` | React components, UI pages, client-side routing, styling | `frontend: create event listing page` |

Format: `<scope>: <imperative, concise description in lowercase>`

---

## 2. Strict Architectural Constraints (Phase 1)

1. **Strict Layered Architecture**:
   - `Controller` → `Service` → `Repository` → `PostgreSQL`.
   - Controllers handle HTTP routing, query/body binding, and return HTTP responses.
   - Controllers MUST NOT call Repositories directly.

2. **Clean Service Layer**:
   - `EventService`, `ImageStorageService`, `RegistrationService`, `AuthService` MUST NOT import:
     - Web framework classes: `org.springframework.web.*`, `jakarta.servlet.*`.
     - Direct DB/ORM implementation classes: `jakarta.persistence.EntityManager`, `org.hibernate.*`.
   - Service layer operates strictly on domain entities, DTOs, and Repository interfaces.

3. **No Architecture Sinkhole**:
   - The Service layer must execute real business logic:
     - Real-time calculations: `effectiveStatus`, `remainingSlots`, `canRegister`.
     - Validation: date range consistency, capacity constraints against existing registrations.
     - DTO transformations and business rule validations.
   - Never create passive pass-through service methods that merely forward calls.

4. **DTO Pattern (Data Transfer Object)**:
   - NEVER expose JPA Entities directly via REST API.
   - Requests must use dedicated DTOs with Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Min`, etc.).
   - Responses must use dedicated DTOs.

5. **Code Comments & Quality Standards**:
   - KEEP CODE COMMENTS TO AN ABSOLUTE MINIMUM. Write clean, self-documenting code.
   - Any comment included MUST be in English only.
   - NO AI SLOP: Do not include meta-comments, academic disclaimers, or notes mentioning instructors, exam questions, or user prompts.
   - Store detailed Vietnamese Q&A and exam defense explanations in dedicated documentation files under `docs/` (e.g., `docs/qna-vietnamese-notes.md`), NOT inside Java/SQL source code.

---

## 3. Team Member Task Boundaries

- **TV1 (Member 1)**: User Authentication, JWT, SecurityFilterChain, Flyway V1, Base Error Handling.
- **TV2 (Member 2)**: Event Management (CRUD, Search & Filter, Image Upload, Soft Delete), Flyway V2/V4.
- **TV3 (Member 3)**: Event Registration, Concurrency control, Capacity checking, Flyway V3.

> Do NOT modify or implement code belonging to other members without explicit instructions.

---

## 4. Development Workflow & Planning

- Before writing or editing code for complex tasks, provide a clear, step-by-step action plan.
- Ensure all tests pass (`mvn test`) before marking tasks as complete.
- Verify that soft-deleted entities are always filtered out from public queries (`deleted_at IS NULL`).
