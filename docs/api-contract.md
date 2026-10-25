# API Contract — Event Registration System

## Event Endpoints

### Public Endpoints

#### `GET /api/events` — List Events

Filter active events with pagination support.

| Param    | Type    | Required | Description                                        |
|----------|---------|----------|----------------------------------------------------|
| keyword  | String  | Optional | Search in event title                              |
| city     | String  | Optional | `HANOI` or `HCMC`                                  |
| category | String  | Optional | `TECHNOLOGY`, `SPORTS`, `EDUCATION`, `MUSIC`       |
| page     | int     | Optional | Page index (default: 0)                            |
| size     | int     | Optional | Items per page (default: 12)                       |
| sort     | String  | Optional | `startTime`, `title`, `createdAt`, `capacity`      |

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "title": "AI Summit 2026",
      "description": "Annual AI Conference",
      "organizerName": "Tech Corp",
      "city": "HANOI",
      "category": "TECHNOLOGY",
      "startTime": "2026-11-15T09:00:00",
      "endTime": "2026-11-15T17:00:00",
      "capacity": 200,
      "registeredCount": 45,
      "remainingSlots": 155,
      "effectiveStatus": "OPEN",
      "canRegister": true,
      "thumbnailUrl": "/api/uploads/thumbnails/abc-123.jpg",
      "createdAt": "2026-09-20T10:00:00",
      "updatedAt": "2026-09-20T10:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 12
}
```

---

#### `GET /api/events/{id}` — Get Event Detail

**Response 200:** Returns `EventResponse` object.

**Response 404:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Event not found with ID: 99",
  "timestamp": "2026-09-27T10:00:00"
}
```

---

### Admin Endpoints (Requires `Authorization: Bearer <JWT>`)

#### `POST /api/admin/events` — Create Event

**Request Body:**
```json
{
  "title": "AI Summit 2026",
  "description": "Annual AI Conference",
  "organizerName": "Tech Corp",
  "city": "HANOI",
  "category": "TECHNOLOGY",
  "startTime": "2026-11-15T09:00:00",
  "endTime": "2026-11-15T17:00:00",
  "capacity": 200
}
```

| Field         | Type          | Validation Constraints              |
|---------------|---------------|-------------------------------------|
| title         | String        | @NotBlank, max 255 chars            |
| description   | String        | Nullable                            |
| organizerName | String        | @NotBlank, max 255 chars            |
| city          | String        | @NotBlank                           |
| category      | String        | @NotBlank                           |
| startTime     | LocalDateTime | @NotNull                            |
| endTime       | LocalDateTime | @NotNull, must be after startTime   |
| capacity      | Integer       | @NotNull, @Min(1)                   |

**Response 201:** Returns created `EventResponse`.
**Response 400:** Validation failure details.

---

#### `PUT /api/admin/events/{id}` — Update Event

**Request Body:** Same as `POST`.

**Response 200:** Returns updated `EventResponse`.
**Response 400:** Validation or business rule failure (e.g., reducing capacity below current registrations).
**Response 404:** Event not found.

---

#### `PATCH /api/admin/events/{id}/status` — Toggle Event Status

**Request Body:**
```json
{
  "adminStatus": "CLOSED"
}
```

**Response 200:** Returns updated `EventResponse`.

---

#### `DELETE /api/admin/events/{id}` — Soft Delete Event

**Response 204:** No Content.
**Response 404:** Event not found.

---

#### `POST /api/admin/events/{id}/thumbnail` — Upload Thumbnail

**Request:** `multipart/form-data` with `file` field. (Max 5MB, JPG/PNG/WebP).

**Response 200:** Returns updated `EventResponse`.
**Response 400:** Invalid format or oversized file.

---

#### `GET /api/admin/events/{id}` — Get Event Detail (Admin)

Returns `EventResponse`, including soft-deleted records for auditing.

---

## Auth Endpoints
*(To be implemented)*

---

## Registration Endpoints

All registration endpoints require an authenticated user:

```http
Authorization: Bearer <access-token>
```

Registration statuses are `CONFIRMED` and `CANCELLED`. Cancelled registrations remain in the user's registration history and can be confirmed again by registering for the same event, subject to the event's availability.

### `POST /api/events/{id}/registrations` — Register for an Event

Register the authenticated user for the event identified by `{id}`. This endpoint does not require a request body.

**Response 200:**
```json
{
  "id": 42,
  "status": "CONFIRMED",
  "eventId": 7,
  "eventTitle": "AI Summit 2026",
  "registeredAt": "2026-10-25T08:20:00"
}
```

| Status | Meaning |
|--------|---------|
| 200 | Registration created or a previous cancelled registration was confirmed again |
| 400 | Event is closed or has already started |
| 404 | Authenticated user or event was not found |
| 409 | Event is full or the user is already registered for the event |

**Example:**
```bash
curl -X POST http://localhost:8080/api/events/7/registrations \
  -H "Authorization: Bearer <access-token>"
```

---

### `GET /api/me/registrations` — Get My Registrations

Return all registrations belonging to the authenticated user, including both confirmed and cancelled registrations.

**Response 200:**
```json
[
  {
    "id": 42,
    "status": "CONFIRMED",
    "eventId": 7,
    "eventTitle": "AI Summit 2026",
    "registeredAt": "2026-10-25T08:20:00"
  },
  {
    "id": 35,
    "status": "CANCELLED",
    "eventId": 3,
    "eventTitle": "Community Workshop",
    "registeredAt": "2026-10-20T14:30:00"
  }
]
```

The response is an empty array when the authenticated user has no registrations.

| Status | Meaning |
|--------|---------|
| 200 | Registrations retrieved successfully |
| 404 | Authenticated user was not found |

**Example:**
```bash
curl http://localhost:8080/api/me/registrations \
  -H "Authorization: Bearer <access-token>"
```

---

### `DELETE /api/registrations/{id}` — Cancel Registration

Cancel the authenticated user's registration identified by `{id}` and release the event slot. This endpoint does not require a request body. The registration is retained with `CANCELLED` status.

**Response 204:** No Content.

Cancelling an already cancelled registration is idempotent and also returns `204 No Content`.

| Status | Meaning |
|--------|---------|
| 204 | Registration cancelled |
| 400 | Event has already started or cancellation is not allowed |
| 404 | Registration does not exist or belongs to another user |

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/registrations/42 \
  -H "Authorization: Bearer <access-token>"
```
