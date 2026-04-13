# VROOM Chat System — Complete Technical Reference

> **Last updated: April 2026**  
> Spring Boot 3.5.11 · Java 21 · PostgreSQL 15 · Raw WebSockets (no STOMP)

---

## Table of Contents

1. [Why WebSockets](#1-why-websockets)
2. [Architecture Overview](#2-architecture-overview)
3. [Component Breakdown — Every File Explained](#3-component-breakdown--every-file-explained)
4. [Data Model — Entities & Enums](#4-data-model--entities--enums)
5. [Database Schema — Full DDL + Constraints + Indexes](#5-database-schema--full-ddl--constraints--indexes)
6. [Message Formats — Every WebSocket Frame](#6-message-formats--every-websocket-frame)
7. [REST API — Full Reference](#7-rest-api--full-reference)
8. [Message State Lifecycle](#8-message-state-lifecycle)
9. [Complete End-to-End Flows](#9-complete-end-to-end-flows)
    - 9.1 [First-Time Chat (from Marketplace Listing)](#91-first-time-chat-from-marketplace-listing)
    - 9.2 [Send a Message — Both Users Online](#92-send-a-message--both-users-online)
    - 9.3 [Send a Message — Receiver Offline](#93-send-a-message--receiver-offline)
    - 9.4 [Receiver Reconnects — Offline Delivery + STATUS_UPDATE to Sender](#94-receiver-reconnects--offline-delivery--status_update-to-sender)
    - 9.5 [Read Receipt Flow](#95-read-receipt-flow)
    - 9.6 [Load Message History (Pagination)](#96-load-message-history-pagination)
    - 9.7 [Heartbeat / Keep-Alive](#97-heartbeat--keep-alive)
    - 9.8 [Session Eviction (missed heartbeats)](#98-session-eviction-missed-heartbeats)
    - 9.9 [Rate-Limit Rejection](#99-rate-limit-rejection)
    - 9.10 [Duplicate Message Rejection](#910-duplicate-message-rejection)
    - 9.11 [Multi-Device Login](#911-multi-device-login)
    - 9.12 [WebSocket Token Expiry & Reconnect](#912-websocket-token-expiry--reconnect)
10. [Safety & Reliability Features](#10-safety--reliability-features)
11. [Metrics & Observability](#11-metrics--observability)
12. [Client Responsibilities](#12-client-responsibilities)
    - 12.1 [Authentication & Token Management](#121-authentication--token-management)
    - 12.2 [Opening a Chat](#122-opening-a-chat)
    - 12.3 [WebSocket Connection Management](#123-websocket-connection-management)
    - 12.4 [Sending Messages](#124-sending-messages)
    - 12.5 [Receiving Messages](#125-receiving-messages)
    - 12.6 [Heartbeat Handling](#126-heartbeat-handling)
    - 12.7 [Error Handling](#127-error-handling)
    - 12.8 [Loading History & Pagination](#128-loading-history--pagination)
    - 12.9 [Unread Counts & Badge Updates](#129-unread-counts--badge-updates)
13. [Known Gaps & Future Work](#13-known-gaps--future-work)
14. [Configuration Reference](#14-configuration-reference)
15. [Scaling Strategy](#15-scaling-strategy)
16. [Roadmap](#16-roadmap)

---

## 1. Why WebSockets

| Option | Why rejected |
|--------|-------------|
| **HTTP Polling** | Client hammers the server every N seconds regardless of activity — wasteful and adds latency |
| **Server-Sent Events (SSE)** | One-directional only — client cannot send messages over the same connection |
| **WebSockets** | ✅ Full-duplex, persistent, low-latency — ideal for real-time bidirectional chat |

WebSockets were chosen because:
- **Real-time delivery** — messages appear instantly without polling delay
- **Low overhead** — after the HTTP upgrade handshake, frames have only 2–10 bytes of framing overhead vs full HTTP headers per request
- **Native Spring support** — `spring-boot-starter-websocket` integrates cleanly with the existing security and DI container
- **Scale target** — designed for ~10k concurrent users, well within single-node WebSocket capacity

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Mobile / Web)                      │
│                                                                      │
│  [REST calls over HTTPS]          [WebSocket over WSS]               │
│  • GET/POST /api/v1/chats/**      • ws://host/ws/chat?token=<JWT>    │
│  • Paginated message history      • Bidirectional real-time frames   │
│  • Open / list chats              • CHAT | READ_RECEIPT | PING | PONG│
│                                   • ACK | DELIVERY | STATUS_UPDATE   │
└──────────────┬────────────────────────────┬─────────────────────────┘
               │ HTTPS                      │ WSS
               ▼                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       SPRING BOOT SERVER                            │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  SecurityConfig  ←  JWT validation (Keycloak JWKS)           │   │
│  │  /ws/chat is public path — auth done by ChatHandshakeInt.    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  REST Layer:                                                        │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  ChatController  →  ChatService  →  DB                     │     │
│  │  POST /api/v1/chats  (listingId required)                  │     │
│  │  GET  /api/v1/chats                                        │     │
│  │  GET  /api/v1/chats/{id}/messages                         │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  WebSocket Layer:                                                   │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  ChatHandshakeInterceptor  ←  JWT decode at WS upgrade      │     │
│  │           │                                                 │     │
│  │           ▼                                                 │     │
│  │  ChatWebSocketHandler  ←  frame routing                     │     │
│  │    ├── MessageRateLimiter  ←  10 msg/s token bucket         │     │
│  │    ├── ChatSessionManager  ←  userId↔List<session> map      │     │
│  │    │       ├── multi-device fan-out to all sessions         │     │
│  │    │       └── heartbeat scheduler (per-session tracking)   │     │
│  │    └── ChatService  ←  business logic                       │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                     │
│  Data Layer:                                                        │
│  ┌──────────────────────────┐  ┌─────────────────────────────────┐  │
│  │  ChatRepository          │  │  MessageRepository              │  │
│  │  • findByParticipants    │  │  • paginated history            │  │
│  │  • findAllByParticipant  │  │  • undelivered lookup           │  │
│  │  • isParticipant         │  │  • dedup check                  │  │
│  └──────────────────────────┘  │  • bulk status update           │  │
│                                │  • batch unread count           │  │
│                                │  • delivered-for-reader query   │  │
│                                └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────┐
│   PostgreSQL 15      │
│   chat + message     │
│   tables             │
└──────────────────────┘
```

---

## 3. Component Breakdown — Every File Explained

### `config/ChatProperties.java`

Reads all `chat.*` application properties via `@Value`.  
Provides typed access to: heartbeat interval, max missed heartbeats, max message size, messages per second.  
All values are configurable via environment variables (e.g. `CHAT_HEARTBEAT_INTERVAL_SECONDS`).

### `config/WebSocketConfig.java`

Spring WebSocket configuration. Registers the `/ws/chat` endpoint with:
- **Handler**: `ChatWebSocketHandler` — processes all inbound frames
- **Interceptor**: `ChatHandshakeInterceptor` — validates JWT before upgrading
- **Origin patterns**: `"*"` (should be restricted to `CORS_ALLOWED_ORIGINS` in production)

### `controller/ChatController.java`

Standard Spring `@RestController` at `/api/v1/chats`. Three endpoints visible in Swagger UI.

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/chats` | Open an existing chat or create a new one (idempotent) |
| `GET /api/v1/chats` | Return all chats for the current user, sorted by most recent message |
| `GET /api/v1/chats/{chatId}/messages` | Paginated message history (30 per page, newest first) |

### `dto/OpenChatRequest.java`

Request body for `POST /api/v1/chats`:

```json
{
  "otherUserId": 5,
  "listingId": 12
}
```

Both fields are **required** (`@NotNull`). Every chat must originate from a marketplace listing.

### `dto/ChatResponse.java`

```json
{
  "id": 1,
  "otherUserId": 5,
  "otherUserDisplayName": "John Doe",
  "listingId": 12,
  "lastMessageAt": "2026-04-12T10:00:00Z",
  "unreadCount": 3
}
```

`unreadCount` = messages with status `SENT` or `DELIVERED`. Resets when the user sends a `READ_RECEIPT`.

### `dto/MessageResponse.java`

```json
{
  "id": 99,
  "chatId": 1,
  "senderId": 3,
  "senderDisplayName": "Alice",
  "content": "Is this still available?",
  "messageType": "TEXT",
  "status": "READ",
  "messageClientId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-04-12T10:01:00Z"
}
```

### `dto/WsInboundMessage.java`

WebSocket frame sent **by the client to the server**:

| Field | Required for | Description |
|-------|-------------|-------------|
| `type` | All frames | `CHAT`, `PING`, `PONG`, `READ_RECEIPT` |
| `chatId` | `CHAT`, `READ_RECEIPT` | Target conversation ID |
| `messageClientId` | `CHAT` | UUID-v4 idempotency key generated by client |
| `messageType` | `CHAT` (optional) | `TEXT` (default), `IMAGE`, `FILE` |
| `content` | `CHAT` | Message text body |

### `dto/WsOutboundMessage.java`

WebSocket frame sent **by the server to the client**:

| Frame type | Fields populated |
|-----------|-----------------|
| `ACK` | `type`, `messageId`, `messageClientId`, `status` |
| `CHAT` | `type`, `messageId`, `chatId`, `senderId`, `senderDisplayName`, `content`, `messageType`, `status`, `messageClientId`, `createdAt` |
| `DELIVERY` | Same as `CHAT` |
| `STATUS_UPDATE` | `type`, `messageId`, `newStatus` |
| `PING` | `type` only |
| `PONG` | `type` only |
| `ERROR` | `type`, `errorCode`, `errorMessage` |

### `entity/Chat.java`

JPA entity for the `chat` table. **Every chat is tied to a marketplace listing** (`listingId` is mandatory). Canonical ordering: `participantOne.id < participantTwo.id` enforced by code and DB CHECK constraint.

### `entity/Message.java`

JPA entity for the `message` table. Immutable once persisted — only `status` may change.

### `entity/MessageStatus.java`

```
SENT      → saved in DB; receiver has not received it yet (or is offline)
DELIVERED → frame sent to receiver's active WebSocket session
READ      → receiver sent READ_RECEIPT; all their DELIVERED messages in this chat are READ
```

### `entity/WsMessageType.java`

```
CHAT          Client → Server   Send a message
READ_RECEIPT  Client → Server   User has read all messages in a chat (requires chatId)
ACK           Server → Client   Server confirms CHAT receipt; includes messageId + status
DELIVERY      Server → Client   Delivers an offline (SENT) message to a reconnected user
STATUS_UPDATE Server → Client   Message status changed: DELIVERED or READ (sent to sender)
PING          Server → Client   Heartbeat probe (every 30 s)
PONG          Client → Server   Heartbeat reply
ERROR         Server → Client   Error notification
```

### `repository/ChatRepository.java`

| Method | Purpose |
|--------|---------|
| `findByParticipantsAndListing(p1, p2, listingId)` | Look up chat by both participants + listing |
| `findAllByParticipant(userId)` | All chats for a user, sorted by `lastMessageAt DESC NULLS LAST` |
| `isParticipant(chatId, userId)` | Authorization check |

### `repository/MessageRepository.java`

| Method | Purpose |
|--------|---------|
| `findAllByChatIdOrderByCreatedAtDesc(chatId, pageable)` | Paginated message history |
| `findUndeliveredForUser(chatId, userId)` | SENT/DELIVERED messages in one chat |
| `findAllUndeliveredForUser(userId)` | All SENT messages across all chats — used on reconnect |
| `findBySenderIdAndMessageClientId(senderId, clientId)` | Deduplication check |
| `bulkUpdateStatus(chatId, userId, from, to)` | Bulk status transition |
| `countUnreadByChatIds(userId, chatIds)` | **Batch** unread count per chat — eliminates N+1 in `listChats()` |
| `countUndeliveredForUser(chatId, userId)` | Single-chat COUNT — used by `openOrGetChat()` |
| `findDeliveredMessagesForReader(chatId, userId)` | DELIVERED messages to mark READ; JOIN FETCHes sender for STATUS_UPDATE fan-out |

### `service/ChatService.java`

| Method | Triggered by |
|--------|-------------|
| `openOrGetChat(currentUserId, otherUserId, listingId)` | `POST /api/v1/chats` |
| `listChats(currentUserId)` | `GET /api/v1/chats` |
| `processMessage(senderProfileId, inbound)` | WebSocket `CHAT` frame |
| `getMessages(chatId, currentUserId, pageable)` | `GET /api/v1/chats/{id}/messages` |
| `markRead(chatId, userId)` | WebSocket `READ_RECEIPT` frame |
| `markDelivered(chatId, userId)` | Internal |
| `deliverOfflineMessages(userId, keycloakUserId)` | On WebSocket connection establishment |

### `service/impl/ChatServiceImpl.java`

- **`openOrGetChat`**: validates `listingId` not null + `listingRepository.existsById()`, canonical lookup, creates chat if missing
- **`processMessage`**: size → membership → dedup → persist → update lastMessageAt → live delivery
- **`markRead`**: loads DELIVERED messages sent by other user, marks them READ, sends `STATUS_UPDATE { READ }` to each message's sender (all their devices)
- **`deliverOfflineMessages`**: delivers SENT messages as DELIVERY frames on reconnect, then sends `STATUS_UPDATE { DELIVERED }` to each sender
- **`listChats`**: batch unread count via single `GROUP BY` query — no N+1
- **Metrics**: 5 counters including `chat.messages.read` + 1 timer

### `websocket/ChatHandshakeInterceptor.java`

Validates JWT at WebSocket upgrade. Token from `Authorization: Bearer` header or `?token=` query param. Stores Keycloak `sub` in session attributes.

### `websocket/ChatSessionManager.java`

**Multi-device support** — maps each userId to a `CopyOnWriteArrayList<WebSocketSession>`.

- `register(userId, session)` — adds session; **never closes existing sessions**
- `remove(userId, session)` — removes specific session; cleans up user entry when list is empty
- `sendToUser(userId, message)` — fans out to **all open sessions**; per-session locks; returns `true` if at least one received it
- `recordPong(session)` — resets missed heartbeats on the **specific session** that replied
- `isOnline(userId)` — true if any session is open
- **Heartbeat**: each session tracked independently; evicts only the unresponsive session

### `websocket/ChatWebSocketHandler.java`

Frame routing:

| Frame | Action |
|-------|--------|
| `PING` | Send `PONG` immediately |
| `PONG` | `sessionManager.recordPong(session)` — per-session |
| `READ_RECEIPT` | `chatService.markRead(chatId, userId)` |
| `CHAT` | Rate-limit → `chatService.processMessage()` → ACK to all sender's devices |
| other | `ERROR { UNKNOWN_TYPE }` |

`@Scheduled(fixedRate = 1000)` — rate-limiter refill every **1 second** (matches `messages-per-second` config).

On disconnect: removes only the specific session; removes rate-limiter bucket only when last session disconnects.

### `websocket/MessageRateLimiter.java`

Token-bucket per-user (shared across all devices). Default 10 CHAT frames/second. Refilled every 1 second. Only CHAT frames are counted; PING/PONG/READ_RECEIPT are exempt.

---

## 4. Data Model — Entities & Enums

### Chat

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, auto-increment | Chat identifier |
| `participant_one` | `BIGINT` | FK → `user_profile`, NOT NULL | Lower user_profile.id |
| `participant_two` | `BIGINT` | FK → `user_profile`, NOT NULL | Higher user_profile.id |
| `listing_id` | `BIGINT` | NOT NULL | Linked marketplace listing (**mandatory**) |
| `last_message_at` | `TIMESTAMPTZ` | Nullable | Timestamp of most recent message |
| `is_deleted` | `BOOLEAN` | DEFAULT FALSE | Soft-delete flag |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | NOT NULL | Audit timestamps |
| `created_by` / `updated_by` | `VARCHAR(255)` | Audit | Keycloak user IDs |

### Message

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, auto-increment | Message identifier |
| `chat_id` | `BIGINT` | FK → `chat`, NOT NULL | Parent conversation |
| `sender_id` | `BIGINT` | FK → `user_profile`, NOT NULL | Who sent it |
| `content` | `TEXT` | Nullable | Message body |
| `message_type` | `VARCHAR(20)` | NOT NULL DEFAULT 'TEXT' | `TEXT` / `IMAGE` / `FILE` |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT 'SENT' | `SENT` / `DELIVERED` / `READ` |
| `message_client_id` | `VARCHAR(64)` | NOT NULL, UNIQUE with sender | Client idempotency key |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Immutable creation time |

---

## 5. Database Schema — Full DDL + Constraints + Indexes

### Tables

```sql
CREATE TABLE chat (
    id               BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    participant_one  BIGINT      NOT NULL,
    participant_two  BIGINT      NOT NULL,
    listing_id       BIGINT      NOT NULL,    -- mandatory: every chat originates from a listing
    last_message_at  TIMESTAMPTZ,
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    deleted_by       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

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
```

### Constraints

```sql
ALTER TABLE chat ADD CONSTRAINT fk_chat_participant_one
    FOREIGN KEY (participant_one) REFERENCES user_profile (id);

ALTER TABLE chat ADD CONSTRAINT fk_chat_participant_two
    FOREIGN KEY (participant_two) REFERENCES user_profile (id);

-- listing_id is always non-null → unique constraint reliably prevents duplicate chats
ALTER TABLE chat ADD CONSTRAINT uq_chat_participants_listing
    UNIQUE (participant_one, participant_two, listing_id);

ALTER TABLE chat ADD CONSTRAINT chk_chat_participant_order
    CHECK (participant_one < participant_two);

ALTER TABLE message ADD CONSTRAINT fk_message_chat
    FOREIGN KEY (chat_id) REFERENCES chat (id);

ALTER TABLE message ADD CONSTRAINT fk_message_sender
    FOREIGN KEY (sender_id) REFERENCES user_profile (id);

ALTER TABLE message ADD CONSTRAINT uq_message_client_id
    UNIQUE (sender_id, message_client_id);  -- deduplication guard
```

### Indexes

```sql
CREATE INDEX idx_chat_participant_one    ON chat (participant_one);
CREATE INDEX idx_chat_participant_two    ON chat (participant_two);
CREATE INDEX idx_chat_last_message_at   ON chat (participant_one, last_message_at DESC);
CREATE INDEX idx_chat_listing           ON chat (listing_id);

CREATE INDEX idx_message_chat_created_at ON message (chat_id, created_at DESC);
CREATE INDEX idx_message_undelivered     ON message (chat_id, status)
    WHERE status IN ('SENT', 'DELIVERED');
CREATE INDEX idx_message_sender          ON message (sender_id);
```

---

## 6. Message Formats — Every WebSocket Frame

### Frames sent by the CLIENT

#### `CHAT` — Send a message
```json
{
  "type": "CHAT",
  "chatId": 42,
  "messageClientId": "550e8400-e29b-41d4-a716-446655440000",
  "messageType": "TEXT",
  "content": "Is this car still available?"
}
```

#### `READ_RECEIPT` — Notify server that the user has read all messages in a chat
```json
{
  "type": "READ_RECEIPT",
  "chatId": 42
}
```
- Send whenever the user **opens** a chat screen or reaches the bottom of the message list.
- The server marks all `DELIVERED` messages in the chat (sent by the other user) as `READ`, then sends `STATUS_UPDATE { newStatus: "READ" }` to each original sender.
- No `ACK` is returned to the reader.
- Not rate-limited.
- Idempotent — safe to call multiple times.

#### `PONG` — Heartbeat reply
```json
{ "type": "PONG" }
```
Must be sent immediately upon receiving a server `PING`.

#### `PING` — Client-initiated heartbeat (optional)
```json
{ "type": "PING" }
```

---

### Frames sent by the SERVER

#### `ACK` — Confirms message was processed
```json
{
  "type": "ACK",
  "messageId": 99,
  "messageClientId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DELIVERED"
}
```
Sent to **all of the sender's connected devices**. `status` is `DELIVERED` if receiver was online; `SENT` if offline.

#### `CHAT` — Incoming message
```json
{
  "type": "CHAT",
  "messageId": 99,
  "chatId": 42,
  "senderId": 3,
  "senderDisplayName": "Alice",
  "content": "Is this car still available?",
  "messageType": "TEXT",
  "status": "DELIVERED",
  "messageClientId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-04-12T10:00:00Z"
}
```
Sent to **all of the receiver's connected devices**.

#### `DELIVERY` — Offline message delivered on reconnect
Identical structure to `CHAT` but `type = "DELIVERY"`. Multiple frames may arrive in rapid succession.

#### `STATUS_UPDATE` — Message status changed
```json
{
  "type": "STATUS_UPDATE",
  "messageId": 99,
  "newStatus": "READ"
}
```
Sent to the **original sender** (all their devices):
- `newStatus = "DELIVERED"`: receiver's device received the message (after reconnect).
- `newStatus = "READ"`: receiver opened the chat and sent `READ_RECEIPT`.

#### `PING` — Server heartbeat probe
```json
{ "type": "PING" }
```
Sent every 30 seconds to each session individually.

#### `PONG` — Server reply to client PING
```json
{ "type": "PONG" }
```

#### `ERROR` — Server-side error
```json
{
  "type": "ERROR",
  "errorCode": "RATE_LIMITED",
  "errorMessage": "Too many messages. Slow down."
}
```

| `errorCode` | Cause | Client action |
|-------------|-------|---------------|
| `AUTH_ERROR` | Keycloak user ID missing | Reconnect with valid token |
| `PARSE_ERROR` | Invalid JSON | Fix JSON; re-send |
| `RATE_LIMITED` | >10 CHAT frames/second | Back off 1–2 s; retry with same `messageClientId` |
| `MESSAGE_ERROR` | Membership / size / duplicate / missing chatId | Read `errorMessage` |
| `USER_NOT_FOUND` | Sender's profile not in DB | Refresh app |
| `UNKNOWN_TYPE` | Unsupported `type` value | Fix client code |

---

## 7. REST API — Full Reference

All endpoints require `Authorization: Bearer <JWT>`.

### `POST /api/v1/chats`

**Request:**
```json
{ "otherUserId": 5, "listingId": 12 }
```

Both fields are **required**. Idempotent — same pair+listing always returns the same chat.

**Response:** `200 OK` with `ChatResponse`.

**Errors:**
- `400` — `otherUserId` equals caller ID, or `listingId` missing
- `404` — listing or user not found

---

### `GET /api/v1/chats`

Returns all conversations sorted by `lastMessageAt DESC NULLS LAST`.  
Unread counts resolved via single batch query (no N+1).

---

### `GET /api/v1/chats/{chatId}/messages?page=0&size=30`

Paginated history, newest first. Returns `Page<MessageResponse>`.

**Errors:** `401` if caller is not a participant.

---

## 8. Message State Lifecycle

```
Client sends CHAT ──►  ┌──────────┐
                        │  SENT    │  persisted in DB; receiver not yet reached
                        └────┬─────┘
                             │
              ┌──────────────┴───────────────┐
              │ Receiver online               │ Receiver reconnects later
              ▼                               ▼
         ┌──────────┐                   ┌──────────┐
         │ DELIVERED│ ◄── live WS       │ DELIVERED│ ◄── DELIVERY frame on reconnect
         │          │     ACK(DELIVERED) │          │     STATUS_UPDATE(DELIVERED)→sender
         └────┬─────┘  →ender           └────┬─────┘
              │                              │
              └──────────────┬───────────────┘
                             │  Receiver sends READ_RECEIPT
                             ▼
                        ┌──────────┐
                        │   READ   │  STATUS_UPDATE(READ) → sender (all devices)
                        └──────────┘
```

| Transition | Trigger | Where |
|-----------|---------|-------|
| → `SENT` | `messageRepository.save()` | `processMessage()` |
| `SENT` → `DELIVERED` | Live WS delivery success | `processMessage()` |
| `SENT` → `DELIVERED` | Receiver reconnects | `deliverOfflineMessages()` |
| `DELIVERED` → `READ` | Receiver sends `READ_RECEIPT` | `markRead()` |

---

## 9. Complete End-to-End Flows

### 9.1 First-Time Chat (from Marketplace Listing)

```
User B (Buyer)                   Backend                    User A (Seller)
     │                              │                              │
     │  POST /api/v1/chats          │                              │
     │  { otherUserId: A,           │                              │
     │    listingId: 42 }           │                              │
     │ ─────────────────────────►   │                              │
     │                              │  1. listingRepository.existsById(42) ✅
     │                              │  2. findByParticipantsAndListing → not found
     │                              │  3. create Chat { listing_id: 42 }
     │ ◄─── 200 { id:1, unread:0 }  │
```

---

### 9.2 Send a Message — Both Users Online

```
Sender (A)               Backend                    Receiver (B)
   │                        │                    [phone]   [tablet]
   │  CHAT frame            │                       │         │
   │ ──────────────────────►│                       │         │
   │                        │  persist, deliver     │         │
   │                        │ ─────────────────────►│         │  CHAT frame to phone
   │                        │ ──────────────────────────────► │  CHAT frame to tablet
   │ ◄── ACK{DELIVERED}     │
```

---

### 9.3 Send a Message — Receiver Offline

```
Sender (A)               Backend                    Receiver (B)
   │                        │                          (offline)
   │  CHAT frame            │
   │ ──────────────────────►│
   │                        │  no sessions for B → false
   │ ◄── ACK{SENT}          │  message stays SENT
```

---

### 9.4 Receiver Reconnects — Offline Delivery + STATUS_UPDATE to Sender

```
Receiver (B)             Backend                    Sender (A)
     │                      │                           │
     │  WS connect          │                           │
     │ ─────────────────────►                           │
     │                      │  deliverOfflineMessages() │
     │ ◄── DELIVERY{msg202} │                           │
     │                      │  msg202.status = DELIVERED│
     │                      │  saveAll()                │
     │                      │                           │
     │                      │  sendToUser(keycloak_A,   │
     │                      │   STATUS_UPDATE{          │
     │                      │    messageId:202,         │
     │                      │    newStatus:DELIVERED}) ►│
     │                      │                           │  A's UI: ✓ → ✓✓
```

---

### 9.5 Read Receipt Flow

```
Receiver (B)             Backend                    Sender (A)
     │                      │                           │
     │  (opens chat)        │                           │
     │  READ_RECEIPT{42}    │                           │
     │ ─────────────────────►                           │
     │                      │  markRead(chatId=42, B):  │
     │                      │  findDeliveredMessages    │
     │                      │    → [201, 203]           │
     │                      │  status = READ, saveAll() │
     │                      │                           │
     │                      │  STATUS_UPDATE{201,READ} ►│
     │                      │  STATUS_UPDATE{203,READ} ►│
     │                      │                           │  A's UI: ✓✓ → ✓✓(blue)
     │
     │  (no ACK back to B — fire and forget)
```

---

### 9.6 Load Message History (Pagination)

```
GET /api/v1/chats/{chatId}/messages?page=0&size=30
→ isParticipant() check
→ findAllByChatIdOrderByCreatedAtDesc → 30 newest
← Page<MessageResponse> (newest first — reverse for display)

Scroll to top → page=1 → prepend older messages
Stop when response.last == true
```

---

### 9.7 Heartbeat / Keep-Alive

```
Server pings every 30s per session:

Server                    Client (phone)         Client (tablet)
  │── PING ──────────────►│                            │
  │── PING ────────────────────────────────────────────►│
  │◄── PONG ─────────────│  (resets missed=0 for phone session)
  │◄── PONG ────────────────────────────────────────────│  (resets missed=0 for tablet)
```

---

### 9.8 Session Eviction (missed heartbeats)

Each session tracks missed heartbeats independently. One device going offline does not affect others.

```
Phone session misses 2 PINGs (60s) → evict phone session only
Tablet session: unaffected
sessions[user] = [session_tablet]  (phone removed)
```

---

### 9.9 Rate-Limit Rejection

```
User sends 11th CHAT within 1 second:
← ERROR { RATE_LIMITED }
Wait ~1s → retry with SAME messageClientId → succeeds
```

Rate limit = 10 CHAT frames/second shared across all devices of the same user.

---

### 9.10 Duplicate Message Rejection

```
Client sends CHAT with clientId="X" → saved, ACK sent
Client retries same clientId="X":
← ERROR { MESSAGE_ERROR, "Duplicate message: X" }
Client: treat as success (already in DB)
```

---

### 9.11 Multi-Device Login

```
Phone connects:   sessions[user_A] = [session_phone]
Browser connects: sessions[user_A] = [session_phone, session_browser]
                  (phone NOT closed)

Message arrives for A:
  sendToUser(A) → session_phone.send() + session_browser.send()

Phone disconnects:
  sessions[user_A] = [session_browser]
  rate limiter bucket KEPT (browser still active)

Browser disconnects (last session):
  sessions.remove(user_A)
  rateLimiter.removeUser(user_A)
```

---

### 9.12 WebSocket Token Expiry & Reconnect

```
1. JWT expires mid-session (server keeps accepting — token checked at handshake only)
2. Client detects expiry or gets 403 on reconnect attempt
3. Refresh via Keycloak /token
4. Reconnect with new JWT → DELIVERY frames catch up missed messages
```

---

## 10. Safety & Reliability Features

| Feature | Implementation | Config |
|---------|---------------|--------|
| **JWT auth at WS upgrade** | `ChatHandshakeInterceptor` → 403 on failure | — |
| **Membership enforcement** | `isParticipant()` on CHAT and READ_RECEIPT frames | — |
| **listingId mandatory** | `@NotNull` + `listingRepository.existsById()` | — |
| **Message deduplication** | App check + DB UNIQUE `(sender_id, message_client_id)` | — |
| **Rate limiting** | Token-bucket 10/s per user (all devices); PING/PONG/READ_RECEIPT exempt | `messages-per-second` |
| **Message size limit** | Byte check before DB access | `max-size-bytes` |
| **Heartbeat / eviction** | Per-session PING; evict after 2 missed per session | `heartbeat-interval-seconds` |
| **Thread-safe delivery** | Per-session lock in `sendToSession()` | — |
| **Offline message recovery** | DELIVERY frames on reconnect | — |
| **Read receipts** | `READ_RECEIPT` → DELIVERED→READ → STATUS_UPDATE to sender | — |
| **STATUS_UPDATE on delivery** | After offline delivery, notify senders | — |
| **N+1 prevention** | `listChats()` uses single `GROUP BY` for unread counts | — |
| **Race condition prevention** | `DataIntegrityViolationException` caught as duplicate error | — |
| **Multi-device fan-out** | All devices receive messages; per-session heartbeat tracking | — |
| **Canonical chat ordering** | `participant_one < participant_two` in code + DB CHECK | — |
| **Chat soft-delete** | `@SQLRestriction("is_deleted = false")` on `Chat` entity | — |

---

## 11. Metrics & Observability

| Metric | Type | Description |
|--------|------|-------------|
| `chat.websocket.active_connections` | Gauge | Total open WS sessions (all users, all devices) |
| `chat.messages.sent` | Counter | Messages persisted |
| `chat.messages.delivered` | Counter | Messages delivered live |
| `chat.messages.read` | Counter | Messages marked READ via READ_RECEIPT |
| `chat.messages.duplicate_rejected` | Counter | Duplicate frames rejected |
| `chat.websocket.errors` | Counter | Handler errors |
| `chat.delivery.latency` | Timer | persist → sendToUser() |

```promql
# Active connections
chat_websocket_active_connections

# Messages per second
rate(chat_messages_sent_total[1m])

# Live delivery rate
rate(chat_messages_delivered_total[1m]) / rate(chat_messages_sent_total[1m])

# Read rate
rate(chat_messages_read_total[5m]) / rate(chat_messages_sent_total[5m])

# p99 delivery latency
histogram_quantile(0.99, rate(chat_delivery_latency_seconds_bucket[5m]))
```

---

## 12. Client Responsibilities

### 12.1 Authentication & Token Management

```
1. Obtain JWT via Keycloak OIDC (Authorization Code + PKCE)
2. Store securely: Keychain (iOS), EncryptedSharedPreferences (Android), httpOnly cookie (Web)
3. REST: Authorization: Bearer <access_token>
4. WebSocket: ws://host/ws/chat?token=<access_token>  (or Authorization header for native)
5. Before reconnect: check exp claim → refresh if expired → never reconnect with expired token
6. If refresh token expired: redirect to login
```

### 12.2 Opening a Chat

```
Every chat MUST originate from a marketplace listing.

1. User taps "Contact Seller" on listing ID=42
2. POST /api/v1/chats  { "otherUserId": <seller_id>, "listingId": 42 }
3. Store returned chatId
4. Navigate to chat screen
5. Load history: GET /api/v1/chats/{chatId}/messages?page=0&size=30
6. Send READ_RECEIPT: { "type": "READ_RECEIPT", "chatId": <chatId> }

Errors:
  400 → listingId missing or invalid request
  404 → listing does not exist (deleted/expired) — show error to user
```

### 12.3 WebSocket Connection Management

```
1. Connect: ws://host/ws/chat?token=<JWT>
2. On connect: process any DELIVERY frames → send READ_RECEIPT for open chat
3. On disconnect: exponential backoff (1s × 2^attempt, max 30s) → refresh token → reconnect
4. Multi-device: all devices connect independently — no conflict
5. On app foreground: reconnect if needed; GET /api/v1/chats to sync counts
```

### 12.4 Sending Messages

```
1. Generate UUID-v4 as messageClientId (use proper library, never reuse)
2. Optimistic UI: show message as "pending"
3. Send: { "type": "CHAT", "chatId": N, "messageClientId": "<uuid>", "content": "..." }
4. On ACK { status: DELIVERED } → show ✓✓
   On ACK { status: SENT }      → show ✓
5. On STATUS_UPDATE { newStatus: DELIVERED } → update to ✓✓
   On STATUS_UPDATE { newStatus: READ }      → update to ✓✓(blue) / "Seen"
6. On disconnect before ACK: queue + retry with SAME uuid after reconnect
```

### 12.5 Receiving Messages

```
A. CHAT (live)
   → Append to message list
   → If chat NOT open: unread++ / show notification
   → If chat IS open: send READ_RECEIPT immediately

B. DELIVERY (offline catch-up)
   → Same as CHAT
   → If chat IS open: send READ_RECEIPT after processing all DELIVERY frames

C. STATUS_UPDATE { messageId, newStatus }
   → Find message by messageId in local store
   → DELIVERED → show ✓✓
   → READ      → show ✓✓(blue) / "Seen"
   (This frame comes to the SENDER, not the reader)
```

### 12.6 Heartbeat Handling

```
On PING received: immediately send PONG (synchronous, no delay)
Each session tracked independently: one device missing PINGs won't affect others

Optional client PING: if no server data for >45s → send PING → expect PONG within 10s
```

### 12.7 Error Handling

```
AUTH_ERROR     → Refresh JWT, reconnect
PARSE_ERROR    → Bug in client; do not retry
RATE_LIMITED   → Wait 1-2s, retry with SAME messageClientId
MESSAGE_ERROR  (starts with "Duplicate:") → Already saved; treat as success
MESSAGE_ERROR  (other) → Show error, offer retry
USER_NOT_FOUND → Log out, contact support
UNKNOWN_TYPE   → Fix client code
```

### 12.8 Loading History & Pagination

```
GET /api/v1/chats/{chatId}/messages?page=0&size=30

Response is NEWEST FIRST — reverse array for display.
Scroll to top → increment page → prepend older messages.
Stop when response.last == true.
```

### 12.9 Unread Counts & Badge Updates

```
unreadCount = SENT + DELIVERED messages from the other user (READ not counted)

1. On app start: GET /api/v1/chats → read unreadCount per chat
2. CHAT/DELIVERY for closed chat → localUnread[chatId]++
3. User opens chat → localUnread[chatId] = 0 → send READ_RECEIPT
4. App foreground: GET /api/v1/chats to resync
5. Badge = sum(all localUnread values)
```

---

## 13. Known Gaps & Future Work

### 13.1 🟠 Missing — Push Notifications (Offline Users)

A user who stays offline never gets notified of waiting messages. Requires FCM/APNs/Web Push integration.

**Sketch:**
```
sendToUser(B, CHAT) returns false:
  → schedule background job
  → if B doesn't reconnect in N minutes:
    → fetch B's push tokens
    → send FCM/APNs: { title: "Alice", body: "Is this still available?" }
```

### 13.2 ⚪ Missing — Typing Indicators

`TYPING` inbound frame → fan out to other participant's session → never persisted (ephemeral).

### 13.3 ⚪ Edge Case — READ_RECEIPT Before Offline Delivery Completes

`markRead()` transitions `DELIVERED → READ`. If a client sends `READ_RECEIPT` before `deliverOfflineMessages()` finishes, some messages may temporarily remain `DELIVERED`.

**Mitigation:** Client should send `READ_RECEIPT` after receiving all `DELIVERY` frames (wait for end-of-stream signal or short timeout), not immediately on connect.

---

## 14. Configuration Reference

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `chat.websocket.heartbeat-interval-seconds` | `CHAT_HEARTBEAT_INTERVAL_SECONDS` | `30` | Seconds between server PING probes |
| `chat.websocket.max-missed-heartbeats` | `CHAT_MAX_MISSED_HEARTBEATS` | `2` | Per-session eviction threshold |
| `chat.message.max-size-bytes` | `CHAT_MESSAGE_MAX_SIZE_BYTES` | `5120` | Max message content in bytes (5 KB) |
| `chat.rate-limit.messages-per-second` | `CHAT_RATE_LIMIT_MESSAGES_PER_SECOND` | `10` | Max CHAT frames/second per user (all devices) |

Eviction time = `heartbeatIntervalSeconds × maxMissedHeartbeats` = 60 s by default.

```properties
chat.websocket.heartbeat-interval-seconds=${CHAT_HEARTBEAT_INTERVAL_SECONDS:30}
chat.websocket.max-missed-heartbeats=${CHAT_MAX_MISSED_HEARTBEATS:2}
chat.message.max-size-bytes=${CHAT_MESSAGE_MAX_SIZE_BYTES:5120}
chat.rate-limit.messages-per-second=${CHAT_RATE_LIMIT_MESSAGES_PER_SECOND:10}
```

---

## 15. Scaling Strategy

### Current: Single-Server

```
Client A (phone)    ──►  ┌─────────────────────────────┐
Client A (browser)  ──►  │  Spring Boot (single node)  │
Client B            ──►  │                             │
                         │  sessions:                  │
                         │   A → [phone, browser]      │
                         │   B → [phone]               │
                         └─────────────────────────────┘
                                       │
                                  PostgreSQL
```

### Future: Multi-Server with Redis Pub/Sub

```
┌──────────────┐           ┌──────────────┐
│   Server 1   │           │   Server 2   │
│  User A, C   │           │  User B      │
└──────┬───────┘           └──────┬───────┘
       └──────────┬───────────────┘
                  │
            ┌─────▼─────┐
            │   Redis   │  pub/sub per userId
            └───────────┘
```

Replace `ConcurrentHashMap` with Redis pub/sub. `sendToUser()` delivers locally or publishes to `chat:user:{keycloakUserId}`. No changes needed to handler, service, or repositories.

---

## 16. Roadmap

| Feature | Priority | Notes |
|---------|----------|-------|
| **Push notifications** | 🟠 Medium | FCM/APNs/Web Push for offline users |
| **Typing indicators** | ⚪ Low | Ephemeral `TYPING` frame |
| **Message attachments** | ⚪ Low | Upload via `/api/v1/attachments` |
| **Message deletion** | ⚪ Low | Per-participant soft-delete flags |
| **Group chats** | ⚪ Low | `chat_participant` junction table |
| **Message reactions** | ⚪ Low | `message_reaction` table |
| **Message search** | ⚪ Low | PostgreSQL full-text index |
| **Redis session store** | ⚪ Low | Horizontal scaling |
| **End-to-end encryption** | ⚪ Future | Phase 3 |

