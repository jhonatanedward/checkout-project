# REST API Contract: Checkout de Pedidos

**Version**: 1.0.0  
**Base Path**: `/api/v1/orders`  
**Authentication**: (Out-of-scope for P1; assumed trusted client)  
**Content-Type**: `application/json`

---

## Endpoint 1: Create Order

**Purpose**: Create a new order with products and quantities; validate against trusted source; ensure atomic persistence of order and creation event.

### Request

**Method**: `POST`  
**Path**: `/api/v1/orders`  
**Headers**:
```
Content-Type: application/json
Idempotency-Key: string (required, unique within 24h)
```

**Body** (`CreateOrderRequest`):
```json
{
  "items": [
    {
      "productId": "prod-001",
      "quantity": 2
    },
    {
      "productId": "prod-002",
      "quantity": 1
    }
  ]
}
```

**Field Validation**:
| Field | Type | Constraints | Description |
|-------|------|-----------|---|
| `items` | Array | 1+ items | List of product references |
| `items[].productId` | String | Non-empty | Product identifier from trusted source |
| `items[].quantity` | Integer | > 0 | Positive quantity only |

**Idempotency-Key Header**:
- **Required**: Yes
- **Format**: Any non-empty string (client chooses; e.g., UUID, request ID)
- **Scope**: Unique within 24-hour window for this endpoint
- **Conflict**: Same key + different normalized product IDs or quantities → 409 error (safe; original order unmodified). Item order and client-supplied names, prices, or totals do not affect the comparison.

---

### Response

#### Success: 201 Created

**Status Code**: `201`

**Body** (`CreateOrderResponse`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
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
  "createdAt": "2026-11-19T12:00:00Z"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|---|
| `id` | UUID | Order identifier (use for retrieval) |
| `status` | Enum | "CREATED" for new orders |
| `totalAmount` | Decimal | Calculated on server: SUM(item.subtotal) |
| `currency` | String | Currency code (e.g., "USD") |
| `items` | Array | Full snapshot of ordered products at creation time |
| `items[].productId` | String | Product ID (echoed) |
| `items[].productName` | String | Authoritative name from trusted source |
| `items[].quantity` | Integer | Quantity ordered (echoed) |
| `items[].unitPrice` | Decimal | Authoritative unit price from trusted source |
| `items[].subtotal` | Decimal | Calculated: quantity × unitPrice |
| `idempotencyKey` | String | Idempotency key (echoed; for record) |
| `createdAt` | ISO-8601 | Server timestamp of creation |

#### Idempotent Success: 200 OK (Duplicate Request)

**When**: Same Idempotency-Key + same normalized product IDs and quantities sent again within 24 hours

**Status Code**: `200`

**Body**: Identical to 201 response (same order that was created initially)

**Guarantee**: Exactly one order created; multiple requests with same key return same result.

---

#### Conflict Error: 409 Conflict

**When**: Same Idempotency-Key + different normalized product IDs or quantities within the 24-hour window

**Status Code**: `409`

**Body** (`ErrorResponse`):
```json
{
  "error": "IDEMPOTENCY_KEY_CONFLICT",
  "message": "Idempotency key already used for a different request within 24 hours",
  "idempotencyKey": "key-abc123",
  "details": {
    "conflictingOrderId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Behavior**: Original order is NOT modified; client must use a new idempotency key to create a different order.

---

#### Validation Error: 400 Bad Request

**When**: Request fails structural validation (missing items, invalid quantity, duplicate product, etc.)

**Status Code**: `400`

**Body** (`ErrorResponse`):
```json
{
  "error": "VALIDATION_FAILED",
  "message": "Order creation validation failed",
  "details": {
    "violations": [
      {
        "field": "items",
        "message": "At least one item is required"
      }
    ]
  }
}
```

**Possible Violations**:
- `items`: Missing, empty, or invalid structure
- `items[n].productId`: Missing or empty
- `items[n].quantity`: Missing, non-positive, non-integer, or non-numeric
- `items[n].duplicate`: Same productId appears in multiple items
- `Idempotency-Key`: Missing from headers

**Guarantee**: No order created if validation fails; safe to retry after fixing request.

---

#### Business Rule Error: 422 Unprocessable Entity

**When**: Request is structurally valid but a referenced product is nonexistent, inactive, or has an invalid price.

**Status Code**: `422`

**Body** (`ErrorResponse`):
```json
{
  "error": "PRODUCT_NOT_FOUND",
  "message": "Product not found in the trusted catalog",
  "details": {
    "retryable": false
  }
}
```

**Possible Errors**:
- `PRODUCT_NOT_FOUND`: One or more product IDs do not exist in trusted source
- `PRODUCT_INACTIVE`: One or more products are marked inactive
- `INVALID_PRODUCT_PRICE`: One or more products have invalid or incompatible prices

**Guarantee**: No order or event created; correct the request or catalog data before retrying.

---

#### Temporary Failure: 503 Service Unavailable

When the trusted local product source is unavailable or incomplete, return `503` with error code `PRODUCT_DATA_UNAVAILABLE` and `details.retryable: true`; no order or event is confirmed. Retry with the same key after the catalog is restored. If invalid startup configuration prevents the service from starting, correct it and restart the service. When bounded concurrency retries are exhausted, return a retryable `503`; retry with the same key and content.

---

#### Server Error: 500 Internal Server Error

**When**: Unexpected failure during order creation or persistence

**Status Code**: `500`

**Body** (`ErrorResponse`):
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "Order creation failed due to an unexpected error",
  "details": {
    "requestId": "req-12345",
    "retryable": true
  }
}
```

**Guarantee**: A confirmed rollback leaves no order or event. A communication failure during commit can leave the outcome unknown; the response must not claim rollback or success. Retry the same content with the same idempotency key to recover the existing order or create one if the transaction did not commit.

---

## Endpoint 2: Get Order

**Purpose**: Retrieve a previously created order and its snapshot of products at creation time.

### Request

**Method**: `GET`  
**Path**: `/api/v1/orders/{orderId}`  
**URL Parameters**:
| Parameter | Type | Description |
|-----------|------|---|
| `orderId` | UUID | Order identifier (from create response) |

---

### Response

#### Success: 200 OK

**Status Code**: `200`

**Body** (`OrderResponse`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
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
  "createdAt": "2026-11-19T12:00:00Z"
}
```

**Guarantee**: Returned data is the **snapshot** preserved at order creation time, unchanged even if products were later modified in the trusted source.

---

#### Not Found: 404 Not Found

**When**: Order ID does not exist

**Status Code**: `404`

**Body** (`ErrorResponse`):
```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Order does not exist",
  "details": {
    "orderId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Guarantee**: No information leakage (does not confirm whether order exists privately); same response for all unknown IDs.

---

## Error Response Format

All error responses follow this standard structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "details": {
    "field": "optional detailed context"
  }
}
```

**Standard HTTP Status Codes**:
| Code | Scenario |
|------|----------|
| 200 | Success (GET, or idempotent replay of POST with same key) |
| 201 | Success (new order created) |
| 400 | Invalid request structure or values |
| 404 | Resource not found (order ID doesn't exist) |
| 409 | Conflict (idempotency key reused with different content) |
| 422 | Nonexistent/inactive product or invalid product price |
| 500 | Server or persistence error; commit may be unknown |
| 503 | Trusted catalog unavailable or bounded concurrency retries exhausted; retryable |

---

## Idempotency Semantics

### Idempotency Window

- **Duration**: 24 hours from first request with a given key
- **Behavior after 24h**: Key can be reused for a new order creation
- **Comparison**: Product IDs and quantities are normalized independently of item order. Client-provided names, prices, and totals are ignored.
- **Cleanup**: Logical expiry is checked explicitly and does not depend on asynchronous MongoDB TTL deletion.

### Idempotency Examples

**Scenario 1: Successful Replay**
```
Request 1: POST /orders {items: [...]} + Idempotency-Key: "key-1"
Response 1: 201 Created, orderId: "order-1"

(Network issue; client retries with same key)

Request 2: POST /orders {items: [...]} + Idempotency-Key: "key-1"
Response 2: 200 OK, orderId: "order-1"  ← Same order returned
```
**Result**: One order created; both responses identify same order.

**Scenario 2: Conflict Detected**
```
Request 1: POST /orders {items: [{productId: "A", quantity: 1}]} + Idempotency-Key: "key-1"
Response 1: 201 Created, orderId: "order-1"

(Client makes different order by mistake with same key)

Request 2: POST /orders {items: [{productId: "B", quantity: 1}]} + Idempotency-Key: "key-1"
Response 2: 409 Conflict  ← Different content rejected
```
**Result**: Original order-1 unmodified; client must use new key.

**Scenario 3: Expired Key (Post 24h)**
```
Request 1: POST /orders {items: [...]} + Idempotency-Key: "key-1"
Response 1: 201 Created, orderId: "order-1"

(24+ hours pass)

Request 2: POST /orders {items: [...]} + Idempotency-Key: "key-1"
Response 2: 201 Created, orderId: "order-2"  ← New order created
```
**Result**: Key can be reused; new order created (not deduplicated).

---

## Content Negotiation

**Supported Media Types**:
- `application/json` (required; default)

**Character Encoding**:
- UTF-8 (required)

---

## Example Workflow

### Create Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-20261119-001" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": 2 },
      { "productId": "WIDGET_B", "quantity": 1 }
    ]
  }'

# Response:
# 201 Created
# {
#   "id": "550e8400-e29b-41d4-a716-446655440000",
#   "status": "CREATED",
#   "totalAmount": 99.99,
#   ...
# }
```

### Retrieve Order
```bash
curl -X GET http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000

# Response:
# 200 OK
# {
#   "id": "550e8400-e29b-41d4-a716-446655440000",
#   "status": "CREATED",
#   ...
# }
```

---

## Ready for Implementation

✓ Request/response schemas fully specified  
✓ Error cases and codes defined  
✓ Idempotency behavior documented  
✓ Next: Implementation via /speckit-tasks
