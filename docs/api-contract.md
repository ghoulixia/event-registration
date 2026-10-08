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
*(To be implemented)*
