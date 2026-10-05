# Core Project Rules & Architectural Guidelines — Event Registration System

See `AGENTS.md` for the full reference.

## Critical Rules Summary

1. **Commit Convention**:
   - `chore:` setup/config/dependency/docker
   - `auth:` TV1 (Authentication & User)
   - `event:` TV2 (Event Module)
   - `registration:` TV3 (Registration Module)
   - `docs:` Documentation & API specs
   - `frontend:` React / UI code
   - **NEVER** commit or push without explicit user permission.

2. **Architecture**:
   - Strict Layered Architecture: Controller -> Service -> Repository -> PostgreSQL.
   - Service layer MUST NOT import `org.springframework.web.*`, `jakarta.servlet.*`, `org.hibernate.*`, or `jakarta.persistence.EntityManager`.
   - Anti-Sinkhole: Business logic and real-time state calculations belong in Service layer.
   - DTO Pattern: Never return or accept raw Entities.

3. **Code Style**:
   - Clean, minimal English comments only.
   - No AI slop or meta-comments referencing assignments/lecturers in source code.
