---
name: verify-and-commit
description: Use this skill to verify strict layered architecture compliance, run unit tests, and prepare standardized commit messages matching team conventions.
---

# Architecture Verification and Commit Runbook

This skill outlines the standard verification process before committing code to the `event-registration` repository.

## Step 1: Architectural Compliance Check

Verify that the Service layer strictly adheres to the Clean Architecture rules:
1. Scan all service classes in `backend/src/main/java/com/team/eventregistration/service/`:
   - Ensure NO imports of `org.springframework.web.*`.
   - Ensure NO imports of `jakarta.servlet.*`.
   - Ensure NO direct imports of `jakarta.persistence.EntityManager` or `org.hibernate.*`.
2. Confirm all request/response objects going through controllers use DTOs rather than raw JPA entities.
3. Confirm soft-delete checks (`deleted_at IS NULL`) are applied on public queries.

## Step 2: Test Suite Execution

Run Maven tests from the `backend/` directory:
```bash
mvn test
```
All unit tests in `EventServiceTest`, `ImageStorageServiceTest`, etc. must pass cleanly.

## Step 3: Git Status & Convention Verification

Check modified files:
```bash
git status --short
```

Determine the proper commit prefix based on the module:
- `chore:` — Configuration, Docker, Flyway setup, dependencies
- `auth:` — TV1 (User, Auth, Security)
- `event:` — TV2 (Event CRUD, Search, Upload, Soft Delete)
- `registration:` — TV3 (Registration, Concurrency)
- `docs:` — Documentation, API specs
- `frontend:` — React / UI

> **CRITICAL**: Never run `git commit` or `git push` without asking the user for explicit permission first!
