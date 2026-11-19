# Data Model: Checkout de Pedidos

**Phase**: 1 (Design & Contracts)  
**Date**: 2026-11-19  
**Branch**: 001-checkout-orders

---

## Domain Entities

### Order (Pedido)

**Purpose**: Represents a customer's purchase with a snapshot of products, quantities, and prices at creation time.

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-----------|---|
| `id` | UUID | PK, Generated | Unique order identifier |
| `status` | Enum | {CREATED, CANCELLED} | Current state; initially CREATED |
| `totalAmount` | BigDecimal | > 0, exactly representable to 2 decimal places | Total in configured currency (calculated server-side) |
| `currency` | String | Fixed length 3 (e.g., "USD") | Currency code for all prices in order |
| `items` | List[OrderItem] | 1+ items | Snapshot of products at creation time |
| `idempotencyKey` | String | Non-unique historical value | Key used at creation; uniqueness is enforced by the active idempotency record, not by orders |
| `createdAt` | Instant | Immutable | Timestamp of order creation (server-side) |
| `createdBy` | String | Optional | Client identifier or IP (optional for future audit trail) |

**Validation Rules**:
- Must have at least 1 item
- Total amount must be > 0
- All items must reference valid products from trusted source
- No duplicate product IDs within items
- Cannot be modified after creation (immutable aggregate root)

**State Transitions**:
- CREATED → (CANCELLED in future increments, out of scope for P1)

**MongoDB Collection**: `orders`

---

### OrderItem (Item do Pedido)

**Purpose**: Snapshot of a product within an order, preserving name, quantity, and unit price from creation time.

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-----------|---|
| `productId` | String | Unique within Order | Reference to product in trusted source |
| `productName` | String | Immutable snapshot | Product name at order creation time |
| `quantity` | Integer | > 0 | Quantity ordered (must be positive) |
| `unitPrice` | BigDecimal | > 0, exactly representable to 2 decimal places | Price per unit at order creation time |
| `subtotal` | BigDecimal | Calculated | Quantity × UnitPrice |

**Validation Rules**:
- ProductId must exist in trusted product source
- Quantity must be positive integer
- UnitPrice must match trusted source at creation time
- Prices and totals use the one configured currency; reject incompatible currency, values requiring rounding, and values not representable as MongoDB Decimal128.
- No modification after order creation

**Embedded in**: Order.items array

**MongoDB Field Mapping**:
```json
{
  "items": [
    {
      "productId": "prod-001",
      "productName": "Widget A",
      "quantity": 2,
      "unitPrice": 29.99,
      "subtotal": 59.98
    }
  ]
}
```

---

### OrderCreatedEvent (Evento de Criação)

**Purpose**: Immutable record of order creation for audit, event sourcing foundation, and future event-driven features.

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-----------|---|
| `id` | UUID | PK, Generated | Event identifier |
| `orderId` | UUID | FK → Order.id | Reference to created order |
| `eventType` | String | "ORDER_CREATED" | Event type marker |
| `createdAt` | Instant | Immutable | When order was created (matches Order.createdAt) |
| `payload` | Object | JSON | Event data snapshot |

**Payload Structure**:
```json
{
  "orderId": "uuid",
  "totalAmount": 29.99,
  "currency": "USD",
  "itemCount": 1,
  "idempotencyKey": "key-abc123"
}
```

**MongoDB Collection**: `order_events` (outbox; no worker or broker publishing in this increment)

---

### IdempotencyKey (Chave de Idempotência)

**Purpose**: Track which orders have been created for a given idempotency key within a 24-hour deduplication window.

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-----------|---|
| `id` | UUID | PK, Generated | Record identifier |
| `key` | String | Unique | Idempotency key from client request |
| `contentHash` | String | SHA-256 hash | Hash of normalized request body for conflict detection |
| `orderId` | UUID | FK → Order.id | Reference to order in the committed record |
| `createdAt` | Instant | Immutable | Order creation timestamp recorded inside the transaction before commit; persisted only on successful creation |
| `expiresAt` | Instant | TTL | Exactly `createdAt` + 24 hours; TTL deletion may be delayed |

**Validation Rules**:
- Key must be non-empty string (client-provided)
- ContentHash uniquely identifies request content
- Unique index on key prevents multiple records for the same key; contentHash is compared by the use case.
- Expiration is checked explicitly; an expired record can be atomically replaced even before asynchronous TTL cleanup.
- The claim and completed record participate in the same transaction as the order and event; a committed record has an orderId.
- The recorded creation timestamp is taken immediately before persisting the new order. It is not the commit confirmation timestamp; a long transaction reduces the time remaining after confirmation.

**Conflict Resolution**:
1. Same key + same content → Return existing orderId (idempotent)
2. Same key + different content → Reject with 409 Conflict
3. New key → Create new record and proceed with order creation

**MongoDB Collection**: `idempotency_keys`

---

## Relationships & Cardinality

```
Order (1) ──────────────── (N) OrderItem
  ├─ inherits idempotencyKey
  └─ generates (1) OrderCreatedEvent

IdempotencyKey → Order (one committed order)

OrderCreatedEvent → Order (1-to-1, one event per order)
```

**Transaction Boundary**:
- Order creation and OrderCreatedEvent persistence happen in a single atomic transaction
- IdempotencyKey is checked and created within the same transaction
- A confirmed rollback leaves no order, event, or idempotency record and starts no persisted deduplication window. A communication failure during commit can leave the outcome unknown; retry with the same key and content within the window.

---

## MongoDB Schema & Indexes

### Collection: `orders` (Database: `checkout-service`)

**Document Structure**:
```json
{
  "_id": "uuid",
  "status": "CREATED",
  "totalAmount": 99.99,
  "currency": "USD",
  "items": [
    {
      "productId": "prod-001",
      "productName": "Widget A",
      "quantity": 2,
      "unitPrice": 29.99,
      "subtotal": 59.98
    },
    {
      "productId": "prod-002",
      "productName": "Widget B",
      "quantity": 1,
      "unitPrice": 40.01,
      "subtotal": 40.01
    }
  ],
  "idempotencyKey": "key-abc123",
  "createdAt": ISODate("2026-11-19T12:00:00Z"),
  "createdBy": "mobile-client-v1"
}
```

**Indexes**:
| Index | Fields | Unique | Purpose |
|-------|--------|--------|---------|
| Primary | `{_id: 1}` | Yes | Business UUID stored as the MongoDB identifier |
| IdempotencyKey | `{idempotencyKey: 1}` | No | Support idempotency checks (with transaction) |
| Status+CreatedAt | `{status: 1, createdAt: -1}` | No | Support future queries by status/date |

---

### Collection: `order_events` (Database: `checkout-service`)

**Document Structure**:
```json
{
  "_id": ObjectId,
  "id": "uuid",
  "orderId": "uuid (FK)",
  "eventType": "ORDER_CREATED",
  "createdAt": ISODate("2026-11-19T12:00:00Z"),
  "payload": {
    "orderId": "uuid",
    "totalAmount": 99.99,
    "currency": "USD",
    "itemCount": 2,
    "idempotencyKey": "key-abc123"
  }
}
```

**Indexes**:
| Index | Fields | Unique | Purpose |
|-------|--------|--------|---------|
| Primary | `{_id: 1}` | Yes | MongoDB default |
| OrderID Lookup | `{orderId: 1}` | Yes | One event per order |
| Event Type + Created | `{eventType: 1, createdAt: -1}` | No | Support event sourcing queries (future) |

---

### Collection: `idempotency_keys` (Database: `checkout-service`)

**Document Structure**:
```json
{
  "_id": ObjectId,
  "id": "uuid",
  "key": "client-provided-key-123",
  "contentHash": "sha256-hash-of-request-body",
  "orderId": "uuid (FK)",
  "createdAt": ISODate("2026-11-19T12:00:00Z"),
  "expiresAt": ISODate("2026-11-20T12:00:00Z")
}
```

**Indexes**:
| Index | Fields | Unique | Purpose |
|-------|--------|--------|---------|
| Key | `{key: 1}` | Yes | One current record per key, regardless of content |
| TTL | `{expiresAt: 1}` | No | Cleanup after expiration, `expireAfterSeconds: 0`; not the expiration boundary |

---

## Validation & Business Rules

### Order Creation Validation

1. **Request Validation**:
   - Idempotency key: required, non-empty string
   - Items: required, non-empty array
   - Each item: productId (required), quantity (required, > 0)

2. **Product Validation** (against trusted source):
   - Each productId must exist and be active
   - Prices and names from trusted source are authoritative; client-provided values ignored
   - No duplicate productIds within single request

3. **Consistency Checks**:
   - Total = SUM(item.quantity × item.unitPrice)
   - All prices in same currency
   - Total > 0

4. **Idempotency Check**:
   - Look up the key and compare the normalized content hash when it is unexpired.
   - Same content returns the committed order before catalog lookup; different content is rejected.
   - If absent or expired, atomically claim or replace the record inside the creation transaction.
   - On a duplicate-key/write conflict, retry or re-read after the aborted transaction in a fresh transaction; bound retries.

5. **Atomicity Check**:
   - Both Order and OrderCreatedEvent must persist in single transaction
   - A confirmed rollback leaves neither record; unknown commit outcomes must not be reported as confirmed rollback. Retrying with the same key and content is safe within the window.

---

## Design Decisions & Trade-offs

| Decision | Rationale | Alternative Rejected |
|----------|-----------|----------------------|
| Separate OrderItem entities (not products) | Snapshot preserves order state independent of product changes | Storing product references only; would lose historical data |
| Dedicated idempotency collection | Explicit conflict detection and TTL cleanup | In-memory cache (no persistence across restarts) |
| Event stored with payload | Self-contained for future consumption; don't require order lookup | Event stores only references; reduces auditability |
| currency as string field | Support future multi-currency orders; validate on input | Fixed to single value; inflexible |
| No Payment/Inventory/Fulfillment data | P1 scope constraint; deferred to future increments | Trying to model full order lifecycle now; over-engineering |

---

## MongoDB Deployment & Configuration

**Database Name**: `checkout-service` (dedicated to this service)

**Transaction Support**: 
- Requires MongoDB 4.0+
- Transactions enabled by default for replica sets
- Single-shard safe (no distributed complexity for P1)

**Connection String** (example):
```
mongodb://mongodb-host:27017/checkout-service
```

**Spring Boot Application Properties**:
```properties
spring.mongodb.uri=mongodb://mongodb-host:27017/checkout-service?replicaSet=rs0
spring.data.mongodb.auto-index-creation=true
```

---

## Ready for Phase 2: Contracts & Task Generation

✓ All entities defined with validation rules  
✓ MongoDB collections and indexes specified  
✓ Relationships and transaction boundaries clear  
✓ Idempotency strategy detailed  
✓ Next: REST API contracts and implementation tasks
