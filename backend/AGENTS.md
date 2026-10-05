# Backend Specific Rules (Spring Boot 3 + Java 21)

## Architecture & Code Rules

1. **Strict Layering**:
   - `Controller` (`@RestController`): Accepts DTOs (`@Valid @RequestBody` / `@ModelAttribute`), returns `ResponseEntity<DTO>`.
   - `Service` (`@Service`): Pure Java business logic. Interacts only with domain models, DTOs, and Repository interfaces. No web (`org.springframework.web.*`) or direct JPA session imports.
   - `Repository` (Spring Data `JpaRepository`): Encapsulates data access and JPQL queries.
   - `Entity`: Annotated with JPA, uses `@PrePersist` and `@PreUpdate` for lifecycle timestamps.

2. **Error Handling**:
   - Throw domain-specific exceptions: `ResourceNotFoundException` (404), `BusinessRuleException` (400), `FileStorageException` (400).
   - Global translation is handled exclusively by `@RestControllerAdvice` (`GlobalExceptionHandler`).

3. **Database Migrations**:
   - Flyway scripts under `src/main/resources/db/migration/`.
   - Version prefixes:
     - `V1__...`: User & Auth (TV1)
     - `V2__...`: Events schema & indexes (TV2)
     - `V3__...`: Registrations schema (TV3)
     - `V4__...`: Seed events (TV2)

4. **Testing**:
   - Unit tests use JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
   - Verify business rule edge cases, status transitions, and exception handling.
