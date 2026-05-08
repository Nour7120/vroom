# Notification System — Module README

## Overview

A production-ready, DB-driven, fan-out notification system that broadcasts push notifications
to all users via Firebase Cloud Messaging (FCM), with full retry support, idempotent processing,
and cursor-based batch iteration.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  REST API                                                        │
│  POST /api/v1/admin/notification-jobs  → creates NotificationJob │
│  GET  /api/v1/notifications            → user's notification ctr │
│  POST /api/v1/devices                  → register FCM token      │
└─────────────────────────────┬────────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │  notification_jobs  │  Fan-out controller
                   │  status, cursor_id  │
                   └──────────┬──────────┘
                              │  SKIP LOCKED (one batch per tick)
                   ┌──────────▼──────────────────────────────────┐
                   │  NotificationJobWorker (@Scheduled)         │
                   │  ┌─────────────────────────────────────┐    │
                   │  │ TX-1: create notifications rows     │    │
                   │  │       advance cursor, commit        │    │
                   │  └──────────────────┬──────────────────┘    │
                   │                     │ after commit          │
                   │  ┌──────────────────▼──────────────────┐    │
                   │  │ Push: FCM send per device + TX-2    │    │
                   │  │       record result, update status  │    │
                   │  └─────────────────────────────────────┘    │
                   └─────────────────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │  notifications      │  User notification center
                   │  push_status        │  PENDING→SENT|FAILED|SKIPPED
                   └──────────┬──────────┘
                              │  push_status=FAILED AND next_retry_at<=now
                   ┌──────────▼──────────────────────────────────┐
                   │  NotificationRetryWorker (@Scheduled)       │
                   │  same 2-phase TX pattern as above           │
                   └─────────────────────────────────────────────┘
```

### Tables

| Table | Purpose |
|---|---|
| `notification_jobs` | Fan-out controller — one row per broadcast campaign |
| `notifications` | Per-user notification center entry (job × user) |
| `user_devices` | FCM registration tokens per user |
| `notification_attempts` | Audit trail of every push attempt |

---

## Full Flow: Job + Cursor

### 1. Admin creates a job
```
POST /api/v1/admin/notification-jobs
{
  "title": "Summer Sale!",
  "body": "20% off all listings until Sunday.",
  "data": { "screen": "MARKETPLACE" }
}
```
A `notification_jobs` row is created with `status=PENDING`, `cursor_id=0`.

### 2. Worker tick (every 10s by default)

```
NotificationJobWorker.processJobs()
  │
  ├─ processNextJobBatch() [TX-1]
  │    ├─ SELECT * FROM notification_jobs WHERE status IN ('PENDING','IN_PROGRESS')
  │    │  AND scheduled_at <= NOW() LIMIT 1 FOR UPDATE SKIP LOCKED
  │    │                              ↑ atomic job claim
  │    ├─ Fetch: SELECT id FROM user_profile WHERE id > cursor_id ORDER BY id LIMIT 100
  │    │                                              ↑ cursor-based batch
  │    ├─ INSERT INTO notifications (job_id, user_id, ...) for each user
  │    │  (idempotent: existsByJobIdAndUserId check + DB UNIQUE constraint)
  │    ├─ UPDATE notification_jobs SET cursor_id = lastUserId, processed_users += 100
  │    └─ COMMIT
  │
  └─ pushBatchAndRecord(notificationIds) [outside TX-1]
       ├─ Load PENDING notifications (read TX)
       ├─ For each notification:
       │    ├─ Find active devices for user
       │    ├─ FCM send() for each device  ← outside any TX
       │    └─ TX-2: save NotificationAttempt, update push_status (SENT/FAILED/SKIPPED)
       └─ Done
```

### 3. Job completion
When `SELECT id FROM user_profile WHERE id > cursor_id` returns empty,
the job is marked `COMPLETED` and `total_users = processed_users`.

### 4. Retry tick (every 30s by default)
```
NotificationRetryWorker.retryFailed()
  │
  ├─ claimRetryBatch() [TX-1]
  │    ├─ SELECT notifications WHERE push_status='FAILED'
  │    │    AND attempt_count < maxAttempts AND next_retry_at <= NOW()
  │    ├─ SET push_status='PENDING', next_retry_at=NULL
  │    └─ COMMIT
  │
  └─ pushBatchAndRecord(ids) [same logic as above]
```

---

## Failure Scenarios

### Pod crash between TX-1 and push
- Notifications are `PENDING` in DB.
- On restart: the job worker ignores them (already PENDING, not pushed yet).
- The retry worker also won't pick them up (push_status ≠ FAILED).
- **Fix**: a separate `PENDING` cleanup sweep (optional enhancement) or a short
  `next_retry_at` set on PENDING rows after a timeout. For most cases, the admin
  can re-trigger a job or set `notification.push-enabled=false` → `true`.

### Partial batch failure (some devices succeed, some fail)
- `anySuccess = true` → `push_status = SENT` (at least one device got it).
- Every device attempt is recorded in `notification_attempts` regardless.

### All devices fail
- `push_status = FAILED`, `next_retry_at = now + backoffDelays[attemptCount - 1]`.
- Retry worker picks up after the backoff window.

### FCM token expired (`UNREGISTERED` error)
- The attempt is recorded as `FAILED` with `error_code = UNREGISTERED`.
- Consider adding a cleanup job that deactivates tokens with `UNREGISTERED` errors.

### Job stuck IN_PROGRESS after crash
- On pod restart, the scheduler immediately finds the stuck job (SKIP LOCKED picks it up).
- The cursor is still set to the last committed batch — processing resumes from there.
- Already-created notification rows are skipped by `existsByJobIdAndUserId`.

---

## Concurrency Handling

| Scenario | Mechanism |
|---|---|
| Two workers on the same pod | Sequential processing per tick (`for i` loop); SKIP LOCKED is defense-in-depth |
| Two instances on different pods | `FOR UPDATE SKIP LOCKED` — each picks a different job |
| Two instances, one job | Instance A grabs the job; Instance B skips it this tick |
| Duplicate notification creation | `UNIQUE(job_id, user_id)` DB constraint + `existsByJobIdAndUserId` fast path |
| Race on device upsert | `findByTokenAndIsDeletedFalse` + update; no unique violation possible |

---

## Functional Requirements (FR)

| # | Requirement | Implementation |
|---|---|---|
| FR-1 | Broadcast to all users | `target_type=ALL`, cursor iterates all `user_profile` |
| FR-2 | No duplicate notifications per user per job | `UNIQUE(job_id, user_id)` + existence check |
| FR-3 | Push via FCM | `FcmServiceImpl` using Firebase Admin SDK |
| FR-4 | Notification center (read/unread) | `notifications` table, markAsRead / markAllAsRead |
| FR-5 | Device token management | `user_devices` with upsert on token |
| FR-6 | Retry failed pushes | `NotificationRetryWorker` with configurable backoff |
| FR-7 | Audit trail | `notification_attempts` per push attempt |
| FR-8 | Job scheduling (deferred start) | `scheduled_at` column on `notification_jobs` |
| FR-9 | Job cancellation | `cancelJob()` sets status to CANCELLED |
| FR-10 | Mock mode (no real FCM) | `notification.fcm.mock-enabled=true` |

## Non-Functional Requirements (NFR)

| # | Requirement | Implementation |
|---|---|---|
| NFR-1 | ~10k users | 100 users/batch × 100 batches = 1 full fan-out per job |
| NFR-2 | No external queue | `@Scheduled` workers + PostgreSQL for coordination |
| NFR-3 | Horizontal scaling | `FOR UPDATE SKIP LOCKED` prevents double-processing |
| NFR-4 | DB first, push after commit | Two-phase design: TX-1 creates rows, then push outside TX |
| NFR-5 | Partial batch success | Per-notification TX-2; one failure doesn't roll back others |
| NFR-6 | Externalized config | `@ConfigurationProperties(prefix = "notification")` |
| NFR-7 | Observable | Structured logging with `[FanOut]`, `[Push]`, `[Retry]` prefixes |

---

## Configuration Reference

All properties can be overridden via environment variables using Spring Boot's relaxed binding
(e.g. `notification.batch-size` → `NOTIFICATION_BATCH_SIZE`).

```properties
# ── Batch & worker ───────────────────────────────────────────────────────────
notification.batch-size=100                  # users per fan-out batch
notification.worker-pool-size=2              # batches attempted per scheduler tick
notification.job-processor-interval-ms=10000 # ms between job worker ticks
notification.retry-processor-interval-ms=30000 # ms between retry worker ticks

# ── Retry ────────────────────────────────────────────────────────────────────
notification.max-attempts=3                  # max total FCM attempts (initial + retries)
notification.backoff-delays-ms=60000,300000,900000  # backoff schedule: 1m, 5m, 15m

# ── Feature toggles ──────────────────────────────────────────────────────────
notification.push-enabled=true               # false = write to DB only, no FCM
notification.retry-enabled=true              # false = retry worker tick skips

# ── FCM ─────────────────────────────────────────────────────────────────────
notification.fcm.mock-enabled=false          # true = synthetic success, no real push
notification.fcm.credentials-path=/secrets/firebase-service-account.json
notification.fcm.connect-timeout-ms=10000
notification.fcm.read-timeout-ms=30000

# ── Logging ──────────────────────────────────────────────────────────────────
logging.level.com.county_cars.vroom.modules.notification=DEBUG
```

---

## Testing with Postman + FCM Test Tokens

### Step 1: Enable mock mode (no real FCM needed)
```properties
notification.fcm.mock-enabled=true
notification.push-enabled=true
```

### Step 2: Register a fake device token
```
POST /api/v1/devices
Authorization: Bearer <jwt>
{
  "token": "test-token-user-123",
  "platform": "ANDROID"
}
```

### Step 3: Create a notification job
```
POST /api/v1/admin/notification-jobs
Authorization: Bearer <jwt>
{
  "title": "Test Push",
  "body": "Hello from VROOM!",
  "data": { "screen": "HOME" }
}
```

### Step 4: Watch the logs
Within 10 seconds (default tick interval) you will see:
```
[JobWorker] tick — attempting up to 2 batch(es)
[FanOut] Job id=1 cursor=0 batchSize=3
[FanOut] Job id=1 batch committed — created=3 skipped=0 cursor=7 totalProcessed=3
[Push] Pushing 3 notification(s)
[FCM MOCK] Push sent — token=test-token... title='Test Push' mockMessageId=mock-1714567890123
[FanOut] Job id=1 COMPLETED — total_processed=3
```

### Step 5: Check notification center
```
GET /api/v1/notifications
Authorization: Bearer <jwt>
```

### Step 6: Use a real FCM token (production)
1. Set `notification.fcm.mock-enabled=false`
2. Place Firebase service account JSON at the configured path
3. Call `POST /api/v1/devices` with a real FCM registration token obtained from the Firebase SDK
4. Create a job — watch the logs for real FCM message IDs

---

## Log Reference

| Prefix | Meaning |
|---|---|
| `[JobWorker]` | Scheduler tick events |
| `[FanOut]` | Batch processing — cursor advancement, row creation |
| `[Push]` | FCM delivery — per-notification push events |
| `[FCM MOCK]` | Simulated push (mock mode only) |
| `[FCM]` | Real FCM delivery or SDK errors |
| `[Retry]` | Retry worker claim and push events |
| `[Device]` | Device token registration / deregistration |
| `[NotifJob]` | Job lifecycle events (created, completed, cancelled) |

---

## API Reference

### User APIs

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/notifications` | Paginated notification center (newest first) |
| `GET` | `/api/v1/notifications/unread-count` | Badge counter |
| `PUT` | `/api/v1/notifications/{id}/read` | Mark one notification as read |
| `PUT` | `/api/v1/notifications/read-all` | Mark all as read |
| `POST` | `/api/v1/devices` | Register / refresh FCM token |
| `DELETE` | `/api/v1/devices/{id}` | Unregister a device |
| `GET` | `/api/v1/devices` | List current user's devices |

### Admin APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/admin/notification-jobs` | Create a broadcast notification job |
| `GET` | `/api/v1/admin/notification-jobs` | List all jobs (paginated) |
| `GET` | `/api/v1/admin/notification-jobs/{id}` | Get job details + progress |
| `PUT` | `/api/v1/admin/notification-jobs/{id}/cancel` | Cancel a job |

All endpoints require a valid Keycloak JWT. Admin endpoints should be secured
with `@PreAuthorize` or a gateway policy once RBAC is production-ready.

