# VROOM — Backend API

> **Spring Boot 3.5.11 · Java 21 · PostgreSQL · Keycloak · WebSockets**

VROOM is a full-featured vehicle marketplace and garage management platform.  
This document covers every feature the backend exposes, all REST + WebSocket endpoints, and the end-to-end flows for each module.

---

## Table of Contents

1. [Tech Stack](#1-tech-stack)
2. [Architecture Overview](#2-architecture-overview)
3. [Module Map](#3-module-map)
4. [Registration & Email Verification](#4-registration--email-verification)
5. [Authentication](#5-authentication)
6. [Two-Factor Authentication (2FA)](#6-two-factor-authentication-2fa)
7. [User Profiles & Locations](#7-user-profiles--locations)
8. [Authorization — Groups & Permissions](#8-authorization--groups--permissions)
9. [Vehicles](#9-vehicles)
10. [Vehicle Media](#10-vehicle-media)
11. [Vehicle Documents](#11-vehicle-documents)
12. [Digital Garage](#12-digital-garage)
13. [Vehicle Passport](#13-vehicle-passport)
14. [Marketplace (Listings)](#14-marketplace-listings)
15. [Real-Time Chat](#15-real-time-chat)
16. [Verifications](#16-verifications)
17. [Attachments](#17-attachments)
18. [Keycloak Integration & Webhooks](#18-keycloak-integration--webhooks)
19. [Audit Logging](#19-audit-logging)
20. [Monitoring & Observability](#20-monitoring--observability)
21. [Security Model](#21-security-model)
22. [Configuration Reference](#22-configuration-reference)
23. [Running Locally](#23-running-locally)

---

## 1. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.11 |
| Database | PostgreSQL 15 |
| Migrations | Liquibase |
| IAM | Keycloak 26 (Admin Client 26.0.7) |
| Security | Spring Security OAuth2 Resource Server (JWT) |
| Real-time | Spring WebSocket (raw WS, not STOMP) |
| ORM | Spring Data JPA / Hibernate |
| Mapping | MapStruct 1.5.5.Final |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI (springdoc 2.3.0) |
| Metrics | Micrometer + Prometheus |
| Monitoring | Spring Boot Admin 3.5.7, Grafana |
| File Storage | Local filesystem **or** AWS S3 v2 (switchable via `attachment.storage.provider`) |
| MIME Validation | Apache Tika 3.2.2 |
| Build | Maven (Spring Boot Maven Plugin with Buildpacks) |

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                          CLIENT                              │
│   REST (HTTPS)         WebSocket (WSS)                       │
└────────────┬───────────────────┬─────────────────────────────┘
             │                   │
             ▼                   ▼
┌────────────────────────────────────────────────────────────┐
│                    VROOM SPRING BOOT API                   │
│                                                            │
│  SecurityConfig (JWT validation via Keycloak JWKS)         │
│                                                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ Auth /   │ │ Garage / │ │Market-   │ │  Chat        │   │
│  │ Reg      │ │ Vehicle  │ │ place    │ │  (WS + REST) │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ User     │ │ Verif-   │ │ Attach-  │ │ Authorization│   │
│  │ Profile  │ │ ication  │ │ ments    │ │ (Groups/Perm)│   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│                                                            │
│  AOP Audit Layer  │  Micrometer Metrics  │  Actuator       │
└──────────┬─────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────┐    ┌───────────────────────┐
│   PostgreSQL 15      │    │   Keycloak 26         │
│   (JPA + Liquibase)  │    │   (IAM, OIDC,Webhooks)│
└──────────────────────┘    └───────────────────────┘
```

---

## 3. Module Map

```
com.county_cars.vroom
├── audit/                     AOP audit logging + AuditLog entity
├── common/                    BaseEntity, exceptions, shared utils
├── config/                    CORS, Swagger, Auditor
├── security/                  SecurityConfig (JWT resource server)
└── modules/
    ├── auth/                  AuthController, Auth2FAController
    ├── registration/          RegistrationController
    ├── user_profile/          UserProfileController, UserLocation
    ├── authorization/         GroupController, PermissionController, UserGroupController
    ├── garage/                VehicleController, GarageController, VehiclePassportController,
    │                          VehicleMediaController, VehicleDocumentController
    ├── marketplace/           ListingController
    ├── chat/                  ChatController, ChatWebSocketHandler (+ WS infra)
    ├── verification/          VerificationController
    ├── attachment/            AttachmentController, AttachmentCleanupScheduler,
    │                          local + S3 storage
    └── keycloak/              KeycloakAdminService, CurrentUserService, Webhooks
```

---

## 4. Registration & Email Verification

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/register` | Public | Register new user |
| `POST` | `/api/v1/auth/resend-verification` | Public | Resend verification email |

### Registration Flow

```
Client                          Backend                         Keycloak
  │                                │                               │
  │── POST /api/v1/auth/register ──►│                               │
  │   { email, displayName,        │                               │
  │     password, phone? }         │                               │
  │                                │── createUser() ──────────────►│
  │                                │   (VERIFY_EMAIL action)       │
  │                                │◄── keycloakUserId ────────────│
  │                                │                               │──► sends verification email
  │                                │── persist UserProfile         │
  │                                │   status=PENDING_MAIL_VER..   │
  │◄── 201 { userId, message } ────│                               │
  │                                │                               │
  │   (user clicks email link)     │                               │
  │                                │◄── VERIFY_EMAIL webhook ──────│
  │                                │── status = ACTIVE             │
```

**Rollback safety**: if the DB insert fails after Keycloak user creation, the Keycloak user is automatically deleted.

### Resend Verification Flow

```
POST /api/v1/auth/resend-verification
  { "email": "user@example.com" }

Rate limits (configurable):
  - Minimum 2-minute cooldown between requests
  - Maximum 5 attempts per calendar day

Responses:
  204  – email dispatched
  400  – cooldown active | daily cap reached | account not PENDING
  404  – no account found for email
```

### User Statuses

| Status | Meaning |
|--------|---------|
| `PENDING_MAIL_VERIFICATION` | Registered, email not yet confirmed |
| `ACTIVE` | Fully active account |
| `INACTIVE` | Deactivated by user or admin |
| `SUSPENDED` | Suspended by admin |

---

## 5. Authentication

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/auth/me` | JWT | Get current user's full profile |
| `POST` | `/api/v1/auth/forgot-password` | Public | Trigger password-reset email |
| `POST` | `/api/v1/account/change-password` | JWT | Change own password |

### GET /api/v1/auth/me

Returns the enriched profile of the caller:
- Internal DB fields (displayName, phone, status, etc.)
- `emailVerified` — from Keycloak
- `authProviders` — linked social providers (e.g. `google`, `apple`)
- `hasLocalPassword` — whether a local Keycloak password credential exists

Performance: 1 DB query + up to 3 Keycloak Admin API calls. Keycloak failures are non-fatal (safe defaults returned).

### Forgot Password Flow

```
POST /api/v1/auth/forgot-password
  { "email": "user@example.com" }

→ Always returns 200 OK (prevents user enumeration)
→ Keycloak dispatches a reset email if the account exists

Rate limits:
  - 2-minute minimum interval between requests
  - 5 attempts per calendar day max

400 is returned ONLY on rate-limit violation, NOT for unknown email.
```

### Change Password Flow

```
POST /api/v1/account/change-password  [JWT required]
  {
    "currentPassword": "...",
    "newPassword": "...",
    "confirmNewPassword": "..."
  }

1. Resolve caller from JWT
2. Validate account is ACTIVE
3. Verify currentPassword against Keycloak (ROPC grant)
4. Confirm newPassword == confirmNewPassword
5. Set new password via Keycloak Admin API
```

---

## 6. Two-Factor Authentication (2FA)

All 2FA endpoints require a valid Bearer JWT. Identity is derived from the token — no `userId` parameter is accepted.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/2fa/enable` | JWT | Enable TOTP 2FA |
| `DELETE` | `/api/v1/auth/2fa/disable` | JWT | Disable TOTP 2FA |
| `GET` | `/api/v1/auth/2fa/status` | JWT | Get current 2FA status |

### Enable 2FA Flow

```
POST /api/v1/auth/2fa/enable
→ Adds CONFIGURE_TOTP to the user's Keycloak required actions
→ Idempotent (safe to call if already enabled)
→ Keycloak prompts the user to scan a QR code on next login
```

### Disable 2FA Flow

```
DELETE /api/v1/auth/2fa/disable
→ Removes all OTP credentials from Keycloak
→ Strips CONFIGURE_TOTP from required actions
→ Safe to call even when 2FA is not active (no-op)
```

### Status Response

```json
{ "enabled": true }
```

`enabled` is `true` only when an `otp` credential already exists (user completed TOTP setup), not merely when `CONFIGURE_TOTP` is pending.

---

## 7. User Profiles & Locations

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/user-profiles` | ADMIN | Create a user profile |
| `GET` | `/api/v1/user-profiles/me` | JWT | Get my own profile |
| `GET` | `/api/v1/user-profiles/{id}` | JWT (owner) / ADMIN | Get profile by ID |
| `GET` | `/api/v1/user-profiles` | ADMIN | List all profiles (paginated) |
| `PUT` | `/api/v1/user-profiles/{id}` | JWT (owner) / ADMIN | Update profile |
| `DELETE` | `/api/v1/user-profiles/{id}` | ADMIN | Soft-delete profile |
| `POST` | `/api/v1/user-profiles/me/avatar` | JWT | Upload / replace profile avatar |
| `POST` | `/api/v1/user-profiles/{id}/locations` | JWT | Add a location |
| `GET` | `/api/v1/user-profiles/{id}/locations` | JWT | List locations |
| `PUT` | `/api/v1/user-profiles/{id}/locations/{locationId}` | JWT | Update a location |
| `DELETE` | `/api/v1/user-profiles/{id}/locations/{locationId}` | JWT | Delete a location |

### UserProfile Fields

| Field | Type | Notes |
|-------|------|-------|
| `keycloakUserId` | UUID string | Keycloak user ID (immutable) |
| `email` | String | Unique |
| `displayName` | String | Unique |
| `phoneNumber` | String | Optional |
| `avatarUrl` | String | URL to profile photo attachment |
| `status` | Enum | See [User Statuses](#registration--email-verification) |

### Avatar Upload

```
POST /api/v1/user-profiles/me/avatar  [multipart/form-data]
  file: <image file>

- Allowed types: jpg, jpeg, png, gif, webp (max 10 MB)
- If the user already has a profile photo the old file is deleted on success
- Returns the updated profile with the new avatarUrl
```

---

## 8. Authorization — Groups & Permissions

All authorization management endpoints are **Admin-only**.

### Groups

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/groups` | Create a group |
| `GET` | `/api/v1/groups/{id}` | Get group by ID |
| `GET` | `/api/v1/groups` | List all groups (paginated) |
| `PUT` | `/api/v1/groups/{id}` | Update a group |
| `DELETE` | `/api/v1/groups/{id}` | Delete a group |

### Permissions

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/permissions` | Create a permission |
| `GET` | `/api/v1/permissions/{id}` | Get permission by ID |
| `GET` | `/api/v1/permissions` | List all permissions (paginated) |
| `PUT` | `/api/v1/permissions/{id}` | Update a permission |
| `DELETE` | `/api/v1/permissions/{id}` | Delete a permission |

### User–Group Assignments

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/user-groups` | Assign a user to a group |
| `GET` | `/api/v1/user-groups/users/{userProfileId}` | List all groups for a user (paginated) |
| `GET` | `/api/v1/user-groups/groups/{groupId}/members` | List all members of a group (paginated) |
| `DELETE` | `/api/v1/user-groups/users/{userProfileId}/groups/{groupId}` | Remove user from group |

---

## 9. Vehicles

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/garage/vehicles` | JWT | **Preferred** — Create vehicle and add to garage atomically |
| `PUT` | `/api/v1/vehicles/{id}` | JWT (owner) | Update vehicle details |
| `GET` | `/api/v1/vehicles/{id}` | JWT | Get vehicle by ID |
| `GET` | `/api/v1/vehicles/registration/{reg}` | JWT | Find by registration number |
| `GET` | `/api/v1/vehicles/vin/{vin}` | JWT | Find by VIN |
| `GET` | `/api/v1/vehicles/my` | JWT | List all vehicles owned by the current user |
| `DELETE` | `/api/v1/vehicles/{id}` | JWT (owner) | Soft-delete vehicle (cascades to media, documents, garage entries) |

> **Note:** `POST /api/v1/vehicles` (register vehicle only) is **deprecated**. Use `POST /api/v1/garage/vehicles` which creates the vehicle and adds it to the garage in one atomic step.

### Vehicle Fields

| Field | Description |
|-------|-------------|
| `registrationNumber` | UK plate, e.g. `AB12 CDE` |
| `vin` | Vehicle Identification Number |
| `make` / `model` / `variant` | Brand and model info |
| `yearOfManufacture` | Year |
| `fuelType` | Petrol, Diesel, Electric, Hybrid, etc. |
| `transmission` | Manual / Automatic |
| `engineCapacity` | Engine size in cc |
| `colour` | Exterior colour |
| `numberOfDoors` | Door count |
| `bodyType` | Hatchback, Saloon, SUV, etc. |
| `co2Emissions` | g/km emissions figure |
| `currentMileage` | Current odometer reading |
| `firstRegistrationDate` | Date of first UK registration |
| `previousOwners` | Count of previous registered keepers |
| `motExpiryDate` | Current MOT expiry date |
| `taxExpiryDate` | Current vehicle tax expiry date |

All vehicles use soft-delete (`is_deleted = false` SQL restriction). Soft-deleting a vehicle cascades to its linked media and documents.

---

## 10. Vehicle Media

Manages photos and videos linked to a vehicle. The item with `displayOrder = 1` is used as the vehicle thumbnail.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/vehicles/{vehicleId}/media` | JWT (owner) | Upload and link a media file |
| `GET` | `/api/v1/vehicles/{vehicleId}/media` | JWT | List all media ordered by displayOrder |
| `PATCH` | `/api/v1/vehicles/{vehicleId}/media/{mediaId}/order` | JWT (owner) | Update display order of a media item |
| `DELETE` | `/api/v1/vehicles/{vehicleId}/media/{mediaId}` | JWT (owner) | Remove a media item |

### Upload Rules

| Constraint | Detail |
|------------|--------|
| Allowed image types | `jpg`, `jpeg`, `png`, `gif`, `webp` |
| Allowed video types | `mp4`, `mov`, `avi` |
| Max image size | 10 MB per file |
| Max video size | 100 MB per file |
| Max images per vehicle | 30 |
| Max videos per vehicle | 3 |
| Thumbnail | Item with `displayOrder = 1` |

Upload, validation, and linking happen in a **single coordinated operation**. If linking fails, the uploaded file is automatically cleaned up.

---

## 11. Vehicle Documents

Manages documents (MOT certificates, insurance, service records, etc.) linked to a vehicle.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/vehicles/{vehicleId}/documents` | JWT (owner) | Upload and link a document |
| `GET` | `/api/v1/vehicles/{vehicleId}/documents` | JWT | List all documents for a vehicle |
| `DELETE` | `/api/v1/vehicles/{vehicleId}/documents/{documentId}` | JWT (owner) | Remove a document |

### Document Types

| Type | Description |
|------|-------------|
| `MOT` | MOT certificate |
| `SERVICE` | Service history record |
| `INVOICE` | Purchase or repair invoice |
| `INSURANCE` | Insurance document |
| `OTHER` | Miscellaneous document |

### Upload Rules

- Allowed types: `pdf`, `doc`, `docx`, `jpg`, `jpeg`, `png` (max 25 MB)
- Upload, validation, and linking happen in a **single atomic operation**
- If linking fails the uploaded file is cleaned up automatically
- **Requires a valid Bearer JWT and vehicle ownership**

---

## 12. Digital Garage

The Digital Garage is each user's personal vehicle collection. A vehicle can be saved as `OWNED` or `WISHLIST`.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/garage` | JWT | List all vehicles in user's garage (includes thumbnail + mediaCount) |
| `POST` | `/api/v1/garage/vehicles` | JWT | **Preferred** — Create a new vehicle and add to garage atomically |
| `DELETE` | `/api/v1/garage/{vehicleId}` | JWT | Remove a vehicle from the garage |
| `PATCH` | `/api/v1/garage/category` | JWT | Update vehicle category (OWNED / WISHLIST) |
| `PATCH` | `/api/v1/garage/notes` | JWT | Update personal notes for a garage vehicle |

> **Note:** `POST /api/v1/garage` (add an existing vehicle by vehicleId) is **deprecated**. Use `POST /api/v1/garage/vehicles` instead.

### Create Vehicle + Add to Garage (`POST /api/v1/garage/vehicles`)

```json
{
  "vehicle": {
    "make": "Toyota",
    "model": "Corolla",
    "..."
  },
  "garageCategory": "OWNED",
  "notes": "My daily driver"
}
```

Creates the vehicle record and the garage entry in a single atomic request. Returns the `GarageVehicleResponse` which includes vehicle details, garage category, notes, thumbnail URL, and media count.

### Garage Vehicle Categories

| Category | Meaning |
|----------|---------|
| `OWNED` | Vehicle currently owned by the user |
| `WISHLIST` | Vehicle the user wants to track or buy |

---

## 13. Vehicle Passport

The Vehicle Passport aggregates all historical and current data about a vehicle into a single response.

### Endpoint

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/vehicles/{vehicleId}/passport` | JWT | Full vehicle passport |

### Passport Contents

| Section | Data |
|---------|------|
| **Identity** | Registration, VIN, make, model, variant, colour, body type, fuel, transmission, engine capacity, CO2 emissions, doors |
| **Current Snapshot** | Current mileage, first registration date, previous owners, MOT expiry date, tax expiry date |
| **MOT History** | Per-test records: date, expiry, result (PASS/FAIL), advisory notes, failure items, mileage at test |
| **Mileage History** | Timeline of mileage readings with dates and source |
| **Ownership Timeline** | Previous and current owners with start/end dates |
| **Documents** | Associated documents (MOT certificates, insurance, service records, etc.) |
| **Media** | Images and videos linked to the vehicle, ordered by displayOrder |
| **Valuation History** | Historic valuations: dealer retail, trade-in, private sale, auction, and average market values |

---

## 14. Marketplace (Listings)

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/listings` | JWT | Create a new listing (starts as DRAFT) |
| `POST` | `/api/v1/listings/{id}/images` | JWT (seller) | Add images to listing (max 5 total) |
| `POST` | `/api/v1/listings/{id}/publish` | JWT (seller) | Publish listing (DRAFT → ACTIVE) |
| `POST` | `/api/v1/listings/{id}/sold` | JWT (seller) | Mark listing as SOLD |
| `GET` | `/api/v1/listings` | JWT | Browse active listings (paginated) |
| `GET` | `/api/v1/listings/search` | JWT | Search with dynamic filters (paginated) |
| `GET` | `/api/v1/listings/{id}` | JWT | Full listing details |
| `POST` | `/api/v1/listings/{id}/enquiries` | JWT | Submit a buyer enquiry |

### Listing Lifecycle

```
    [Seller creates listing]
            │
            ▼
         DRAFT ──── (add images, edit details)
            │
            │  POST /{id}/publish
            ▼
         ACTIVE ──── (visible to all buyers, searchable)
            │
            │  POST /{id}/sold
            ▼
          SOLD
```

### Search Filters

The `GET /api/v1/listings/search` endpoint supports:

| Filter | Description |
|--------|-------------|
| `make` | Vehicle brand |
| `model` | Vehicle model |
| `yearMin` / `yearMax` | Year of manufacture range |
| `priceMin` / `priceMax` | Price range |
| `mileageMin` / `mileageMax` | Mileage range |
| `fuelType` | Fuel type |
| `transmission` | Manual / Automatic |
| `colour` | Exterior colour |
| `location` | Listing location |

**Sort options**: `price`, `publishedAt`, `yearOfManufacture`, `currentMileage`

### Buyer Enquiry

```
POST /api/v1/listings/{id}/enquiries
  { "message": "Is this still available?" }

→ Stored against the listing for the seller to see
→ Typically leads to opening a Chat between buyer and seller
```

---

## 15. Real-Time Chat

The chat system uses raw WebSockets for real-time delivery and REST for history and management.

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/chats` | JWT | Open or retrieve an existing chat |
| `GET` | `/api/v1/chats` | JWT | List all chats for the current user (newest first) |
| `GET` | `/api/v1/chats/{chatId}/messages` | JWT | Paginated message history (newest first, default 30) |

### WebSocket Connection

```
ws://host/ws/chat?token=<JWT>
```

The JWT is validated at the HTTP → WebSocket upgrade handshake (`ChatHandshakeInterceptor`). Connections without a valid token are immediately closed with `POLICY_VIOLATION`.

### WebSocket Message Types

| Type | Direction | Description |
|------|-----------|-------------|
| `CHAT` | Client → Server | Send a message |
| `ACK` | Server → Client | Server confirms receipt, returns `messageId` and `status` |
| `DELIVERY` | Server → Client | Delivers a message that arrived while offline |
| `STATUS_UPDATE` | Server → Client | Message status changed to DELIVERED or READ |
| `PING` | Server → Client | Heartbeat probe (every 30 s) |
| `PONG` | Client → Server | Heartbeat reply |
| `ERROR` | Server → Client | Server-side error notification |

### Message Statuses

| Status | Meaning |
|--------|---------|
| `SENT` | Persisted in DB |
| `DELIVERED` | Recipient's WebSocket session received it |
| `READ` | Recipient opened the conversation |

### Send Message Flow

```
Client                              Server
  │                                   │
  │── { type: CHAT,                   │
  │     chatId: 42,                   │
  │     messageClientId: "<uuid>",    │  ← client-generated UUID for dedup
  │     content: "Hello!" } ─────────►│
  │                                   │  1. Rate-limit check (10 msg/s per user)
  │                                   │  2. Membership check
  │                                   │  3. Deduplication (messageClientId)
  │                                   │  4. Persist Message (status = SENT)
  │                                   │  5. Try live delivery to receiver
  │◄── { type: ACK,                   │
  │      messageId: 99,               │
  │      status: DELIVERED } ─────────│
  │                       Receiver ◄──│── { type: CHAT, messageId: 99, ... }
```

### Offline Message Delivery

```
[User reconnects]
→ afterConnectionEstablished()
→ deliverOfflineMessages(userId)
→ All SENT messages pushed as DELIVERY frames
→ Status updated to DELIVERED
```

### Heartbeat Mechanism

```
Every 30 seconds:
  Server ──► { type: PING }
  Client ──► { type: PONG }
  Server records pong, resets missed-count

After 2 missed PINGs:
  → Session closed and removed from SessionManager
```

### Safety Features

| Feature | Detail |
|---------|--------|
| Rate limiting | Token-bucket, 10 messages/second per user (configurable) — buckets refilled every 2 s |
| Deduplication | `messageClientId` (UUID) prevents duplicate messages on retry |
| Membership check | Only chat participants can send to a chat |
| Max message size | 5 KB default (configurable) |
| Metrics | `chat.websocket.errors` counter via Micrometer |

### Chat Model

- A chat is always between exactly **two** users (`participantOne.id < participantTwo.id` enforced by DB constraint)
- A chat can optionally be linked to a **marketplace listing** (`listingId`)
- Conversations are sorted by `lastMessageAt`

---

## 16. Verifications

Manages identity and document verification requests submitted by users and reviewed by admins.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/verifications/user-profiles/{userProfileId}` | JWT | Submit a new verification request |
| `GET` | `/api/v1/verifications/{id}` | JWT | Get verification request by ID |
| `GET` | `/api/v1/verifications/user-profiles/{userProfileId}` | JWT | Get all verifications for a user (paginated) |
| `GET` | `/api/v1/verifications` | ADMIN | List verifications filtered by status (paginated, default: PENDING) |
| `PUT` | `/api/v1/verifications/{id}/review` | ADMIN | Approve or reject a request |
| `DELETE` | `/api/v1/verifications/{id}` | ADMIN | Soft-delete a verification request |

### Verification Types

| Type | Description |
|------|-------------|
| `IDENTITY` | Government-issued photo ID |
| `DRIVING_LICENSE` | UK / international driving licence |
| `VEHICLE_REGISTRATION` | V5C / logbook document |
| `INSURANCE` | Proof of vehicle insurance |
| `ADDRESS` | Proof of address (utility bill, bank statement) |

### Verification Statuses

| Status | Description |
|--------|-------------|
| `PENDING` | Submitted, awaiting admin attention |
| `IN_REVIEW` | Admin is currently reviewing |
| `APPROVED` | Verification accepted |
| `REJECTED` | Verification declined (notes provided) |
| `EXPIRED` | Verification window passed |

### Review Flow

```
User submits request
        │
        ▼
    PENDING ──────────────────────────► IN_REVIEW
                                              │
                  ┌───────────────────────────┤
                  │                           │
                  ▼                           ▼
             APPROVED                     REJECTED
          (reviewedBy, reviewedAt)      (reviewedBy, notes)
```

---

## 17. Attachments

Provides unified file upload, download and management for all modules.

> **Preferred approach:** Use module-specific single-step upload endpoints which upload and link files atomically with automatic cleanup on failure:
> - `POST /api/v1/vehicles/{vehicleId}/media` — vehicle photos / videos
> - `POST /api/v1/vehicles/{vehicleId}/documents` — vehicle documents (MOT, insurance…)
> - `POST /api/v1/user-profiles/me/avatar` — user profile photo

### Generic Endpoints (use module-specific endpoints where available)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/attachments` | JWT | Upload a file (multipart) — **deprecated** |
| `GET` | `/api/v1/attachments/{id}` | JWT | Download / stream file |
| `DELETE` | `/api/v1/attachments/{id}` | JWT (owner / ADMIN) | Soft-delete attachment |

### Attachment Categories

| Category | Used For |
|----------|----------|
| `PROFILE_PHOTO` | User avatar |
| `VEHICLE_IMAGE` | Garage / listing photos |
| `VEHICLE_DOCUMENT` | V5C, ownership docs |
| `SERVICE` | Service history records |
| `MOT` | MOT certificates |
| `INVOICE` | Purchase / repair invoices |
| `INSURANCE` | Insurance documents |
| `VERIFICATION_ID` | KYC identity documents |
| `OTHER` | Miscellaneous |

### Attachment Statuses

| Status | Meaning |
|--------|---------|
| `UPLOADED` | File uploaded but not yet linked to an entity |
| `ACTIVE` | Linked and in use |
| `DELETED` | Soft-deleted |

### Visibility Rules

| Visibility | Who Can Download |
|------------|-----------------|
| `PUBLIC` | Any authenticated user |
| `PRIVATE` | Owner or ADMIN |
| `ADMIN_ONLY` | ADMIN role only |

### Upload Validation

| Check | Detail |
|-------|--------|
| Extension whitelist | `jpg, jpeg, png, gif, webp, pdf, doc, docx, xls, xlsx, mp4, mov, avi` |
| Size – images | Max 10 MB (`PROFILE_PHOTO`, `VEHICLE_IMAGE`) |
| Size – documents | Max 25 MB |
| Magic-number check | Apache Tika 3.2.2 verifies actual file signature for `jpg`, `png`, `pdf`, `gif` |

### Storage Backends

Switchable via `attachment.storage.provider`:

| Value | Backend |
|-------|---------|
| `local` | Local filesystem (configurable upload dir) |
| `s3` | AWS S3 v2 (configurable bucket + region) |

### Orphaned Attachment Cleanup

A scheduled task (`AttachmentCleanupScheduler`) runs **every 10 minutes** to clean up orphaned attachments:
- Finds all non-deleted attachments with status `UPLOADED` (never linked after upload)
- Deletes the physical file from storage (best-effort)
- Soft-deletes the DB record (`status = DELETED`, `is_deleted = true`, `deleted_by = "scheduler"`)

This handles edge cases where the JVM was killed between upload and link steps.

---

## 18. Keycloak Integration & Webhooks

### Keycloak Admin Service

`KeycloakAdminService` is the central integration point with Keycloak's Admin REST API:

| Operation | Triggered By |
|-----------|-------------|
| Create user | Registration |
| Delete user | Registration rollback on DB failure |
| Resend verification email | Resend endpoint |
| Send password-reset email | Forgot-password endpoint |
| Send forgot-password email | Combined UPDATE_PASSWORD + VERIFY_EMAIL |
| Set new password | Change-password endpoint |
| Enable / disable user | Admin operations |
| Fetch user by ID / email | Auth flows |
| Get linked social providers | `/auth/me` |
| Manage OTP credentials | 2FA endpoints |
| Send required-action emails | 2FA enable |
| Verify user credentials | Change-password (ROPC grant) |

**All email sending is handled by Keycloak** — the backend never sends mail directly.

### Event Webhook

The custom Keycloak SPI (`vroom-keycloak-spi-1.0-SNAPSHOT.jar`) calls the backend's internal webhook on user events:

```
POST /internal/keycloak/events
  Header: X-Internal-Secret: <webhook_secret>
  Body: { type, userId, realmId, clientId, time }
```

**Currently handled events:**

| Event | Action |
|-------|--------|
| `VERIFY_EMAIL` | Transition user profile: `PENDING_MAIL_VERIFICATION` → `ACTIVE` |

Other events (LOGIN, LOGOUT, UPDATE_PASSWORD, REGISTER) are logged and available for future handlers.

### Custom Keycloak Theme

`keycloak-config/themes/vroom/` provides:
- Custom email templates (`executeActions.ftl`) for HTML and plain-text verification / action emails
- Branding via `theme.properties`

---

## 19. Audit Logging

An AOP aspect (`LoggingAuditAspect`) automatically intercepts all service-layer methods and writes structured logs and audit records.

### What Gets Logged

| Pointcut | Log Level | Audit Record Written |
|----------|-----------|----------------------|
| All `*ServiceImpl.*()` | INFO (entry, exit, duration) | No |
| `create*()` | INFO | **Yes** — action = `CREATE` |
| `update*()` | INFO | **Yes** — action = `UPDATE` |
| `delete*()` | INFO | **Yes** — action = `DELETE` |

### AuditLog Schema

| Column | Description |
|--------|-------------|
| `entity_type` | Class name of the affected entity (e.g. `UserProfile`) |
| `entity_id` | ID of the affected record |
| `action` | `CREATE` / `UPDATE` / `DELETE` |
| `performed_by` | Keycloak user ID from `SecurityContext` (`"system"` for unauthenticated) |
| `metadata` | JSONB — method name and arguments snapshot |
| `created_at` | Timestamp (auto-set on persist) |

---

## 20. Monitoring & Observability

### Spring Actuator

| Endpoint | URL |
|----------|-----|
| Health | `/actuator/health` |
| Info | `/actuator/info` |
| Metrics | `/actuator/metrics` |
| Prometheus scrape | `/actuator/prometheus` |

Health details shown `when-authorized`.

### Prometheus

| Source | Address |
|--------|---------|
| Application | `http://vroom-backend:<port>/actuator/prometheus` |
| Keycloak | `:9000/metrics` |
| PostgreSQL | `:9187/metrics` (postgres-exporter sidecar) |

Custom application metrics:
- `chat.websocket.errors` — WebSocket handler error counter
- Standard Micrometer: JVM, HTTP server, HikariCP, GC, threads

### Grafana Dashboards

Pre-provisioned dashboard (`vroom-overview.json`):
- Application request rates and response times
- JVM heap, GC activity, thread pool utilisation
- HikariCP DB connection pool
- Service health status

### Prometheus Alerts

Key alert rules in `monitoring/prometheus/alerts/alerts.yml`:
- Service down detection
- High memory / CPU usage
- DB connection pool exhaustion
- Slow queries
- High error rates

### Spring Boot Admin

Enabled server at `spring.boot.admin.server.enabled=true`.  
UI accessible at the application root `/` on the configured port.

---

## 21. Security Model

### JWT Resource Server

All protected endpoints validate Bearer JWTs issued by Keycloak:
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` — fetches Keycloak's public keys
- `spring.security.oauth2.resourceserver.jwt.issuer-uri` — validates the `iss` claim

### Public Paths (no token required)

| Path | Notes |
|------|-------|
| `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` | API documentation |
| `/actuator/health`, `/actuator/info` | Health checks |
| `/api/v1/auth/**` | Registration, resend-verification, forgot-password, login flows |
| `/internal/keycloak/events` | Keycloak webhook (validated by `X-Internal-Secret` header) |
| `/ws/chat` | WebSocket upgrade — JWT validated inside `ChatHandshakeInterceptor` |

### Role-Based Access Control

| Role | Capabilities |
|------|-------------|
| `ADMIN` | Full access — user management, verifications, groups, permissions |
| Authenticated user | Own profile, own vehicles/garage, marketplace, chat, own verifications |
| Public | Registration, forgot-password, resend-verification |

### CORS

Configured via `CorsConfig` with environment-variable-driven allow-lists (`CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`, `CORS_MAX_AGE`).

### Soft Deletes

All major entities use `@SQLRestriction("is_deleted = false")` — deleted records remain in the database for audit purposes but are invisible to all JPA queries.

---

## 22. Configuration Reference

All application settings are driven by environment variables. See `application.properties` for the full mapping.

### Server

| Env Var | Description |
|---------|-------------|
| `SERVER_PORT` | HTTP listen port |
| `SWAGGER_SERVER_URL` | Base URL shown in Swagger UI (default: `http://localhost:<SERVER_PORT>`) |

### Database

| Env Var | Description |
|---------|-------------|
| `DB_URL` | JDBC connection URL |
| `DB_USERNAME` | DB username |
| `DB_PASSWORD` | DB password |
| `LIQUIBASE_SCHEMA` | Schema for Liquibase migrations |

### Keycloak

| Env Var | Description |
|---------|-------------|
| `KEYCLOAK_ISSUER_URI` | JWT issuer URI |
| `KEYCLOAK_JWK_SET_URI` | JWKS endpoint |
| `KEYCLOAK_ADMIN_SERVER_URL` | Keycloak base URL |
| `KEYCLOAK_ADMIN_REALM` | Realm name |
| `KEYCLOAK_ADMIN_CLIENT_ID` | Admin client ID |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | Admin client secret |
| `KEYCLOAK_ADMIN_USERNAME` | Admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | Admin password |

### Rate Limits

| Env Var | Default Meaning |
|---------|-----------------|
| `REGISTRATION_VERIFICATION_RESEND_INTERVAL_MINUTES` | 2 min cooldown between resend requests |
| `REGISTRATION_VERIFICATION_DAILY_MAX_RETRIES` | 5 resend attempts per day |
| `AUTH_PASSWORD_RESET_RESEND_INTERVAL_MINUTES` | 2 min cooldown between reset requests |
| `AUTH_PASSWORD_RESET_DAILY_MAX_RETRIES` | 5 reset attempts per day |

### CORS

| Env Var | Description |
|---------|-------------|
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `CORS_ALLOWED_METHODS` | Comma-separated allowed HTTP methods |
| `CORS_ALLOWED_HEADERS` | Allowed request headers (`*` = all) |
| `CORS_EXPOSED_HEADERS` | Headers exposed to the browser |
| `CORS_MAX_AGE` | Pre-flight cache duration (seconds) |

### Attachments

| Env Var | Description |
|---------|-------------|
| `ATTACHMENT_STORAGE_PROVIDER` | `local` or `s3` |
| `ATTACHMENT_LOCAL_UPLOAD_DIR` | Root dir for local storage |
| `ATTACHMENT_MAX_IMAGE_SIZE_BYTES` | Max image upload size |
| `ATTACHMENT_MAX_DOCUMENT_SIZE_BYTES` | Max document upload size |
| `ATTACHMENT_IMAGE_CATEGORIES` | Comma-separated categories treated as images |
| `ATTACHMENT_ALLOWED_EXTENSIONS` | Comma-separated allowed file extensions |
| `ATTACHMENT_ALLOWED_MIME_TYPES` | Comma-separated allowed MIME types |
| `ATTACHMENT_S3_BUCKET_NAME` | S3 bucket (s3 provider only) |
| `ATTACHMENT_S3_REGION` | AWS region (s3 provider only) |

### Chat / WebSocket

| Env Var | Default |
|---------|---------|
| `CHAT_HEARTBEAT_INTERVAL_SECONDS` | 30 |
| `CHAT_MAX_MISSED_HEARTBEATS` | 2 |
| `CHAT_MESSAGE_MAX_SIZE_BYTES` | 5120 (5 KB) |
| `CHAT_RATE_LIMIT_MESSAGES_PER_SECOND` | 10 |

---

## 23. Running Locally

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15 running (or use the full Docker Compose stack — see [root README](../README.md))
- Keycloak 26 running

### Build & Run

```bash
cd vroom

# Run tests
./mvnw test

# Start the application
./mvnw spring-boot:run

# Or build a JAR first
./mvnw package -DskipTests
java -jar target/vroom-0.0.1-SNAPSHOT.jar
```

### API Documentation

Once running:
- **Swagger UI**: http://localhost:8082/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8082/v3/api-docs
- **Spring Boot Admin**: http://localhost:8082/

### Minimum Environment Variables for Local Dev

```env
SERVER_PORT=8082

# Database
DB_URL=jdbc:postgresql://localhost:5432/vroom
DB_USERNAME=vroom
DB_PASSWORD=vroom
LIQUIBASE_SCHEMA=public

# Keycloak
KEYCLOAK_ISSUER_URI=http://localhost:8090/realms/vroom
KEYCLOAK_JWK_SET_URI=http://localhost:8090/realms/vroom/protocol/openid-connect/certs
KEYCLOAK_ADMIN_SERVER_URL=http://localhost:8090
KEYCLOAK_ADMIN_REALM=vroom
KEYCLOAK_ADMIN_CLIENT_ID=vroom-backend
KEYCLOAK_ADMIN_CLIENT_SECRET=<your-client-secret>
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=admin

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000
CORS_ALLOWED_METHODS=GET,POST,PUT,PATCH,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=*
CORS_EXPOSED_HEADERS=Authorization
CORS_MAX_AGE=3600

# Attachments
ATTACHMENT_STORAGE_PROVIDER=local
ATTACHMENT_LOCAL_UPLOAD_DIR=uploads
ATTACHMENT_MAX_IMAGE_SIZE_BYTES=10485760
ATTACHMENT_MAX_DOCUMENT_SIZE_BYTES=26214400
ATTACHMENT_IMAGE_CATEGORIES=PROFILE_PHOTO,VEHICLE_IMAGE
ATTACHMENT_ALLOWED_EXTENSIONS=jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,mp4,mov,avi
ATTACHMENT_ALLOWED_MIME_TYPES=image/jpeg,image/png,image/gif,image/webp,application/pdf
ATTACHMENT_S3_BUCKET_NAME=
ATTACHMENT_S3_REGION=

# Rate limits
REGISTRATION_VERIFICATION_RESEND_INTERVAL_MINUTES=2
REGISTRATION_VERIFICATION_DAILY_MAX_RETRIES=5
AUTH_PASSWORD_RESET_RESEND_INTERVAL_MINUTES=2
AUTH_PASSWORD_RESET_DAILY_MAX_RETRIES=5

# Chat / WebSocket
CHAT_HEARTBEAT_INTERVAL_SECONDS=30
CHAT_MAX_MISSED_HEARTBEATS=2
CHAT_MESSAGE_MAX_SIZE_BYTES=5120
CHAT_RATE_LIMIT_MESSAGES_PER_SECOND=10
```

For the full Docker Compose setup (PostgreSQL, Keycloak, Prometheus, Grafana), see the [root README](../README.md).

---

*Last updated: April 2026*
