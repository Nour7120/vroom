# VROOM Chat System

## Table of Contents

1. [Why WebSockets](#1-why-websockets)
2. [Architecture Overview](#2-architecture-overview)
3. [Module Structure](#3-module-structure)
4. [WebSocket Lifecycle](#4-websocket-lifecycle)
5. [Database Schema](#5-database-schema)
6. [Message State Lifecycle](#6-message-state-lifecycle)
7. [Offline Message Handling](#7-offline-message-handling)
8. [Duplicate Message Prevention](#8-duplicate-message-prevention)
9. [Heartbeat Mechanism](#9-heartbeat-mechanism)
10. [Rate Limiting](#10-rate-limiting)
11. [Message Size Limits](#11-message-size-limits)
12. [Security](#12-security)
13. [REST APIs](#13-rest-apis)
14. [Metrics and Observability](#14-metrics-and-observability)
15. [Attachment Support (Phase 2)](#15-attachment-support-phase-2)
16. [Configuration Reference](#16-configuration-reference)
17. [Scaling Strategy](#17-scaling-strategy)
18. [Future Improvements](#18-future-improvements)

---

## 1. Why WebSockets

| Option | Why rejected |
|---|---|
| **HTTP Polling** | Wasteful — client hammers the server every N seconds regardless of activity |
| **Server-Sent Events (SSE)** | One-directional only — client cannot send messages over the same connection |
| **WebSockets** | ✅ Full-duplex, persistent, low-latency — ideal for real-time bidirectional chat |

WebSockets were chosen because:
- **Real-time delivery** — messages appear instantly without polling delay
- **Low overhead** — after the initial HTTP upgrade handshake, frames have only 2–10 bytes of framing overhead vs full HTTP headers per request
- **Native Spring support** — `spring-boot-starter-websocket` integrates cleanly with the existing security and DI container
- **Scale target** — the system is designed for ~10k concurrent users, well within single-node WebSocket capacity

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT                               │
│  ws://host/ws/chat?token=<JWT>   HTTP /api/v1/chats/**      │
└───────────────┬──────────────────────────┬──────────────────┘
                │ WebSocket                │ REST
                ▼                          ▼
┌──────────────────────────────────────────────────────────────┐
│                     SPRING BOOT SERVER                       │
│                                                              │
│  ChatHandshakeInterceptor  ◄── JWT validation at upgrade     │
│         │                                                    │
│         ▼                                                    │
│  ChatWebSocketHandler      ◄── PING/PONG/CHAT frame dispatch │
│    │         │                                               │
│    │         ▼                                               │
│    │  MessageRateLimiter   ◄── 10 msg/sec per user           │
│    │                                                         │
│    ▼                                                         │
│  ChatSessionManager        ◄── userId → WebSocketSession map │
│    │                            + heartbeat scheduler        │
│    ▼                                                         │
│  ChatService               ◄── business logic + DB          │
│    │         │                                               │
│    ▼         ▼                                               │
│  ChatRepo  MessageRepo     ◄── JPA / PostgreSQL              │
└──────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|---|---|
| `ChatHandshakeInterceptor` | Validates JWT at WS upgrade time; stores `userId` in session attributes |
| `ChatWebSocketHandler` | Routes frames: CHAT → service, PING → PONG, PONG → heartbeat reset |
| `ChatSessionManager` | Thread-safe `ConcurrentHashMap<userId, WebSocketSession>` + heartbeat scheduler |
| `MessageRateLimiter` | Token-bucket rate limiter, refilled every second |
| `ChatService` | Deduplication, membership check, persist, live delivery, offline recovery |
| `ChatRepository` | Canonical participant-ordered chat lookup |
| `MessageRepository` | Timeline queries, undelivered lookup, bulk status update |
| `ChatController` | REST: open chat, list chats, paginated message history |

---

## 3. Module Structure

```
modules/chat/
├── config/
│   ├── ChatProperties.java          # @Value bindings for all chat.* properties
│   └── WebSocketConfig.java         # Registers /ws/chat handler + interceptor
├── controller/
│   └── ChatController.java          # REST: POST /chats, GET /chats, GET /chats/{id}/messages
├── dto/
│   ├── ChatResponse.java            # REST response: chat summary with unread count
│   ├── MessageResponse.java         # REST response: single message
│   ├── OpenChatRequest.java         # REST request: open or get a chat
│   ├── WsInboundMessage.java        # WebSocket frame: client → server
│   └── WsOutboundMessage.java       # WebSocket frame: server → client
├── entity/
│   ├── Chat.java                    # @Entity: conversation between two users
│   ├── Message.java                 # @Entity: individual message (immutable)
│   ├── MessageStatus.java           # SENT | DELIVERED | READ
│   ├── MessageType.java             # TEXT | IMAGE | FILE
│   └── WsMessageType.java           # CHAT | ACK | DELIVERY | STATUS_UPDATE | PING | PONG | ERROR
├── repository/
│   ├── ChatRepository.java          # isParticipant, findByParticipants, findAllByParticipant
│   └── MessageRepository.java       # timeline, undelivered, dedup, bulk status update
├── service/
│   ├── ChatService.java             # Interface
│   └── impl/
│       └── ChatServiceImpl.java     # Full implementation with Micrometer metrics
└── websocket/
    ├── ChatHandshakeInterceptor.java # JWT validation at HTTP → WS upgrade
    ├── ChatSessionManager.java       # userId ↔ session registry + heartbeat
    ├── ChatWebSocketHandler.java     # TextWebSocketHandler: connects, routes, disconnects
    └── MessageRateLimiter.java       # Token-bucket per-user rate limiter
```

---

## 4. WebSocket Lifecycle

### Connection

```
Client                              Server
  │                                   │
  │── HTTP GET /ws/chat?token=<JWT> ──►│
  │                                   │  ChatHandshakeInterceptor.beforeHandshake()
  │                                   │    → decode JWT
  │                                   │    → store keycloakUserId in attributes
  │◄── 101 Switching Protocols ───────│
  │                                   │  ChatWebSocketHandler.afterConnectionEstablished()
  │                                   │    → register session in ChatSessionManager
  │◄── [offline messages delivered] ──│    → deliverOfflineMessages()
```

### Sending a Message

```
Client                              Server
  │                                   │
  │── { type:CHAT, chatId:42,         │
  │     messageClientId:"uuid",       │
  │     content:"Hello!" } ──────────►│
  │                                   │  ChatWebSocketHandler.handleTextMessage()
  │                                   │    → rate limit check
  │                                   │    → ChatService.processMessage()
  │                                   │        → membership check
  │                                   │        → deduplication check
  │                                   │        → persist Message (status=SENT)
  │                                   │        → attempt live delivery to receiver
  │◄── { type:ACK,                    │
  │      messageId:99,                │
  │      status:DELIVERED } ──────────│
  │                                   │
  │                    Receiver ◄─────│── { type:CHAT, messageId:99, ... }
```

### Heartbeat

```
Server                              Client
  │                                   │
  │  (every 30s)                      │
  │── { type:PING } ─────────────────►│
  │◄── { type:PONG } ─────────────────│
  │   recordPong() → reset missedCount │
  │                                   │
  │  (if 2 missed PINGs)              │
  │   → close session                 │
  │   → evict from SessionManager     │
```

### Disconnection

```
Client                              Server
  │── close frame ───────────────────►│
  │                                   │  afterConnectionClosed()
  │                                   │    → SessionManager.remove(userId)
  │                                   │    → RateLimiter.removeUser(userId)
```

---

## 5. Database Schema

### `chat` table

```sql
CREATE TABLE chat (
    id               BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    participant_one  BIGINT      NOT NULL,  -- lower user_profile.id
    participant_two  BIGINT      NOT NULL,  -- higher user_profile.id
    listing_id       BIGINT,               -- optional marketplace listing
    last_message_at  TIMESTAMPTZ,
    -- BaseEntity audit columns
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ...
);

-- Constraints
UNIQUE (participant_one, participant_two, listing_id)
CHECK  (participant_one < participant_two)             -- canonical ordering
FK     participant_one → user_profile(id)
FK     participant_two → user_profile(id)

-- Indexes
idx_chat_participant_one
idx_chat_participant_two
idx_chat_last_message_at  ON (participant_one, last_message_at DESC)
idx_chat_listing          ON (listing_id) WHERE listing_id IS NOT NULL
```

**Design decision — canonical ordering:**
The application always stores `min(userId, otherUserId)` as `participant_one`. This guarantees that looking up "does a chat exist between users A and B?" is a single equality query with no OR condition, and the UNIQUE constraint works correctly regardless of which user initiates.

### `message` table

```sql
CREATE TABLE message (
    id                  BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    chat_id             BIGINT      NOT NULL,
    sender_id           BIGINT      NOT NULL,
    content             TEXT,
    message_type        VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    status              VARCHAR(20) NOT NULL DEFAULT 'SENT',
    message_client_id   VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Constraints
FK     chat_id   → chat(id)
FK     sender_id → user_profile(id)
UNIQUE (sender_id, message_client_id)                 -- deduplication

-- Indexes
idx_message_chat_created_at  ON (chat_id, created_at DESC)  -- timeline
idx_message_undelivered      ON (chat_id, status) WHERE status IN ('SENT','DELIVERED')
idx_message_sender           ON (sender_id)
```

**Design decision — no soft delete on messages:**
Messages are immutable records. Deleting a message is a future feature (Phase 2) that would use a `deleted_for_sender` / `deleted_for_receiver` flag rather than a global `is_deleted`, to match real messaging UX.

---

## 6. Message State Lifecycle

```
                    ┌─────────────────────────────┐
                    │         SENT                │
                    │  (persisted, receiver       │
                    │   offline or not yet ack'd) │
                    └──────────────┬──────────────┘
                                   │ receiver connects / message delivered via WebSocket
                                   ▼
                    ┌─────────────────────────────┐
                    │        DELIVERED            │
                    │  (frame sent to receiver's  │
                    │   active WebSocket session) │
                    └──────────────┬──────────────┘
                                   │ receiver opens the chat (Phase 2)
                                   ▼
                    ┌─────────────────────────────┐
                    │          READ               │
                    │  (receiver has seen the     │
                    │   message in the UI)        │
                    └─────────────────────────────┘
```

| Transition | Trigger |
|---|---|
| → `SENT` | Message persisted to DB |
| `SENT` → `DELIVERED` | Live WebSocket delivery succeeds, OR user reconnects and offline messages are pushed |
| `DELIVERED` → `READ` | Phase 2: client sends a read receipt frame |

---

## 7. Offline Message Handling

When the receiver is **offline** at the time a message is sent:

1. The message is persisted with `status = SENT`
2. `ChatSessionManager.isOnline(receiverId)` returns `false` — no delivery attempted
3. Message sits in the DB indexed by `idx_message_undelivered`

When the receiver **reconnects**:

1. `ChatWebSocketHandler.afterConnectionEstablished()` fires
2. `ChatService.deliverOfflineMessages(userId, keycloakUserId)` is called
3. All messages where `status IN ('SENT')` and the user is a participant are loaded (ordered by `created_at ASC`)
4. Each message is pushed as a `DELIVERY` frame over the new WebSocket session
5. Successfully delivered messages are bulk-updated to `status = DELIVERED`

---

## 8. Duplicate Message Prevention

Every message sent by a client must include a `messageClientId` — a UUID-v4 generated by the client before sending.

**Two-layer protection:**

| Layer | Mechanism |
|---|---|
| Application | `MessageRepository.findBySenderIdAndMessageClientId()` — explicit check before insert |
| Database | `UNIQUE (sender_id, message_client_id)` — catches race conditions where two identical frames arrive simultaneously and the app-level check races |

If a duplicate is detected, the server responds with `ERROR { errorCode: "MESSAGE_ERROR", errorMessage: "Duplicate message: <clientId>" }`. The client should treat this as a successful send (the original already persisted).

---

## 9. Heartbeat Mechanism

### Flow

```
ChatSessionManager
  └── ScheduledExecutorService (single daemon thread "ws-heartbeat")
        └── pingAllSessions() — fires every heartbeatIntervalSeconds
              │
              ├── For each session: increment missedHeartbeats counter
              ├── Send { type: PING } frame
              │
              └── If missedHeartbeats >= maxMissedHeartbeats:
                    → closeQuietly(session)
                    → evict(userId) from sessions + locks maps
```

### Configuration

```properties
chat.websocket.heartbeat-interval-seconds=30
chat.websocket.max-missed-heartbeats=2
```

With defaults: a session is evicted after **60 seconds** of silence (2 × 30s).

When the client receives a `PING`, it must respond with `{ type: "PONG" }`. On receipt of a PONG, `ChatSessionManager.recordPong()` resets the missed counter to 0.

---

## 10. Rate Limiting

A **token-bucket** algorithm is used, implemented without any external dependency.

### How it works

1. Each user starts with `messagesPerSecond` tokens (default: 10)
2. Each CHAT frame consumes 1 token via `MessageRateLimiter.tryConsume(userId)`
3. If the bucket is empty → the frame is rejected with `ERROR { errorCode: "RATE_LIMITED" }`
4. `ChatWebSocketHandler` has a `@Scheduled(fixedRate = 1000)` method that calls `rateLimiter.refillAll()` every second, restoring all buckets to max

### Configuration

```properties
chat.rate-limit.messages-per-second=10
```

PING/PONG frames are **not** rate-limited — only CHAT frames consume tokens.

---

## 11. Message Size Limits

Text message `content` is validated in `ChatServiceImpl.processMessage()`:

```java
if (content.getBytes().length > props.getMaxMessageSizeBytes()) {
    throw new BadRequestException("Message exceeds maximum allowed size");
}
```

Default: **5120 bytes (5 KB)**. Configure via:

```properties
chat.message.max-size-bytes=5120
```

Oversized messages are rejected with an `ERROR` frame before any DB access.

---

## 12. Security

### WebSocket authentication

Spring Security's `SessionCreationPolicy.STATELESS` does not protect WebSocket upgrades by default. Authentication is handled explicitly in `ChatHandshakeInterceptor`:

1. Extract JWT from `Authorization: Bearer <token>` header or `?token=<JWT>` query param
2. Decode and validate via the existing `JwtDecoder` bean (same JWKS endpoint as the REST API)
3. Store the Keycloak `sub` (userId) in the session attributes
4. Return `false` from `beforeHandshake()` to reject unauthenticated upgrades → HTTP 403

**The `/ws/chat` path is added to `SecurityConfig.PUBLIC_PATHS`** — this is intentional and correct. Spring Security's HTTP filter chain sees the initial GET request as "unauthenticated" because there's no `Authorization` header in standard browser WebSocket upgrade requests; the token is passed as a query param instead. The interceptor performs all authentication.

### Chat membership enforcement

Every inbound CHAT frame validates `ChatRepository.isParticipant(chatId, senderProfileId)` before processing. An `UnauthorizedException` is thrown if the sender is not a participant.

---

## 13. REST APIs

### `POST /api/v1/chats`
Open or retrieve an existing chat with another user.

**Request:**
```json
{
  "otherUserId": 5,
  "listingId": 12
}
```

**Response:**
```json
{
  "id": 1,
  "otherUserId": 5,
  "otherUserDisplayName": "John Doe",
  "listingId": 12,
  "lastMessageAt": "2026-03-13T10:00:00Z",
  "unreadCount": 3
}
```

---

### `GET /api/v1/chats`
List all chats for the current user, sorted by most recent message.

---

### `GET /api/v1/chats/{chatId}/messages?page=0&size=30`
Paginated message history, newest first.

**Response:**
```json
{
  "content": [
    {
      "id": 99,
      "chatId": 1,
      "senderId": 3,
      "senderDisplayName": "Alice",
      "content": "Is this still available?",
      "messageType": "TEXT",
      "status": "DELIVERED",
      "messageClientId": "550e8400-e29b-41d4-a716-446655440000",
      "createdAt": "2026-03-13T10:01:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 2,
  "size": 30,
  "number": 0
}
```

---

### WebSocket Frame Examples

**Send a message:**
```json
{ "type": "CHAT", "chatId": 1, "messageClientId": "uuid-v4", "messageType": "TEXT", "content": "Hello!" }
```

**ACK from server:**
```json
{ "type": "ACK", "messageId": 99, "messageClientId": "uuid-v4", "status": "DELIVERED" }
```

**Heartbeat ping from server:**
```json
{ "type": "PING" }
```

**Client pong:**
```json
{ "type": "PONG" }
```

**Error from server:**
```json
{ "type": "ERROR", "errorCode": "RATE_LIMITED", "errorMessage": "Too many messages. Slow down." }
```

---

## 14. Metrics and Observability

All metrics are exposed via Micrometer and scraped by Prometheus at `/actuator/prometheus`.

| Metric | Type | Description |
|---|---|---|
| `chat.websocket.active_connections` | Gauge | Current number of open WebSocket sessions |
| `chat.messages.sent` | Counter | Total messages successfully persisted |
| `chat.messages.delivered` | Counter | Total messages delivered live over WebSocket |
| `chat.messages.duplicate_rejected` | Counter | Duplicate frames rejected |
| `chat.websocket.errors` | Counter | Total WebSocket handler errors |
| `chat.delivery.latency` | Timer | Duration from message persist to live delivery |

**Grafana dashboard queries (PromQL):**

```promql
# Active connections
chat_websocket_active_connections

# Messages per second
rate(chat_messages_sent_total[1m])

# Delivery rate
rate(chat_messages_delivered_total[1m])

# p99 delivery latency
histogram_quantile(0.99, rate(chat_delivery_latency_seconds_bucket[5m]))
```

---

## 15. Attachment Support (Phase 2)

The schema is already prepared. `message_type` supports `TEXT | IMAGE | FILE`.

**Planned Phase 2 flow:**

1. Client uploads file via `POST /api/v1/attachments` (existing attachment module)
2. Server returns `attachmentId`
3. Client sends WS frame: `{ type: "CHAT", messageType: "IMAGE", content: "<attachmentId>" }`
4. Server resolves the attachment record and includes the storage URL in the outbound frame

Attachment storage uses the existing `AttachmentService` which supports both local and S3 backends via the `attachment.storage.provider` property.

---

## 16. Configuration Reference

```properties
# Heartbeat interval in seconds (default: 30)
chat.websocket.heartbeat-interval-seconds=30

# Sessions evicted after this many missed heartbeats (default: 2 = 60s)
chat.websocket.max-missed-heartbeats=2

# Maximum message content size in bytes (default: 5120 = 5 KB)
chat.message.max-size-bytes=5120

# Max CHAT frames per user per second (default: 10)
chat.rate-limit.messages-per-second=10
```

All values support environment variable substitution:
```properties
chat.websocket.heartbeat-interval-seconds=${CHAT_HEARTBEAT_INTERVAL_SECONDS:30}
```

---

## 17. Scaling Strategy

The current implementation stores sessions **in-process memory** (`ConcurrentHashMap`). This is correct and sufficient for a single-server deployment serving up to ~10k concurrent users.

### Multi-server scaling (future)

When horizontal scaling is required, the session store must be externalised:

```
┌──────────┐    ┌──────────┐
│ Server 1 │    │ Server 2 │
│ User A   │    │ User B   │
└────┬─────┘    └────┬─────┘
     │               │
     └───────┬────────┘
             │
        ┌────▼────┐
        │  Redis  │  ← pub/sub channel per userId
        └─────────┘
```

**Plan:**
1. Replace `ConcurrentHashMap<userId, WebSocketSession>` with a Redis pub/sub fanout
2. When server 1 wants to deliver to User B (connected to server 2):
   - Publish the message to Redis channel `chat:user:{userId}`
   - Server 2 is subscribed to that channel and forwards to the local session
3. Use Spring Data Redis `RedisMessageListenerContainer` for this

**No code change required** beyond replacing `ChatSessionManager` — all other components are already session-location agnostic.

---

## 18. Future Improvements

| Feature | Notes |
|---|---|
| **Read receipts** | Client sends `{ type: "READ", messageId: N }` → server updates status to `READ`, pushes `STATUS_UPDATE` to sender |
| **Typing indicators** | Ephemeral — never persisted, just forwarded to the other participant |
| **Message deletion** | Per-participant soft delete flags (`deleted_for_sender`, `deleted_for_receiver`) |
| **Group chats** | Requires a `chat_participant` junction table instead of fixed `participant_one/two` |
| **Message reactions** | New `message_reaction` table linking `message_id + user_id + emoji` |
| **Push notifications** | When user is offline and stays offline > N minutes, send FCM/APNs push |
| **Redis session store** | For horizontal scaling (see §17) |
| **Message search** | PostgreSQL full-text index on `message.content` |
| **Encryption** | End-to-end encryption at Phase 3 |

