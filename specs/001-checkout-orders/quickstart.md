# Quickstart & Validation Guide: Checkout de Pedidos

**Phase**: 1 (Design validation)  
**Date**: 2026-11-19  
**Branch**: 001-checkout-orders

---

## Overview

This guide demonstrates how to validate the checkout order feature end-to-end. It covers:
1. Prerequisites and service startup
2. Runnable validation scenarios
3. Expected outcomes proving P1 requirements are met
4. Troubleshooting tips

---

## Prerequisites

### Environment
- **Java**: 21 or later
- **Maven**: 3.8+
- **MongoDB**: 4.0+ (via Docker Compose or local installation)
- **Git**: For branch management
- **HTTP Client**: curl, Postman, or similar

### Local Setup

#### 1. Clone and Switch Branch
```bash
git clone https://github.com/jhonatanedward/checkout-project.git
cd checkout-project
git checkout 001-checkout-orders
```

#### 2. Start MongoDB

**Option A: Docker Compose** (Recommended)
```bash
# In project root or services/checkout-service
docker-compose up -d mongodb

# Verify connectivity
docker ps | grep mongodb
# Expected: Container running on port 27017
```

**Option B: Local MongoDB**
```bash
mongod --dbpath /path/to/checkout-db

# Verify connectivity
mongo --eval "db.adminCommand('ping')"
# Expected: { "ok": 1 }
```

#### 3. Build and Run Service

```bash
cd services/checkout-service

# Build
mvn clean package -DskipTests

# Run (application will start on port 8080 by default)
mvn spring-boot:run

# Or run JAR directly
java -jar target/checkout-service-*.jar
```

**Verify Service is Running**:
```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

#### 4. Verify MongoDB Connection

```bash
curl http://localhost:8080/actuator/health/ping
# Expected: "pong" or health status showing MongoDB as UP
```

---

## Validation Scenario 1: Create Valid Order (P1 Requirement)

**Story**: Crear pedido con productos existentes, cálculo de total en servidor, consulta por ID.

### Step 1.1: Prepare Request

**Endpoint**: `POST /api/v1/orders`

**Request**:
```bash
IDEMPOTENCY_KEY="scenario-1-$(date +%s)"

curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": 2 },
      { "productId": "WIDGET_B", "quantity": 1 }
    ]
  }' | jq .
```

### Step 1.2: Expected Response (201 Created)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "totalAmount": 99.99,
  "currency": "USD",
  "items": [
    {
      "productId": "WIDGET_A",
      "productName": "Widget A",
      "quantity": 2,
      "unitPrice": 29.99,
      "subtotal": 59.98
    },
    {
      "productId": "WIDGET_B",
      "productName": "Widget B",
      "quantity": 1,
      "unitPrice": 40.01,
      "subtotal": 40.01
    }
  ],
  "idempotencyKey": "scenario-1-1700000000",
  "createdAt": "2026-11-19T12:00:00Z"
}
```

### Step 1.3: Validate Outcomes

**Check 1: Total Calculation**
```bash
# Extract from response
TOTAL=$(curl -s -X POST http://localhost:8080/api/v1/orders ... | jq .totalAmount)
# Expected: 99.99 (2 × 29.99 + 1 × 40.01)
# Verify: (2 * 29.99) + (1 * 40.01) = 99.99 ✓
```

**Check 2: Product Data from Trusted Source**
```bash
# Verify product names and prices match trusted source
# (Trusted products defined in service configuration)
# Expected: WIDGET_A = "Widget A", price 29.99; WIDGET_B = "Widget B", price 40.01
# Client-sent values (if any) ignored; server values used ✓
```

**Check 3: Order ID Assigned**
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders ... | jq -r .id)
echo "Order ID: $ORDER_ID"
# Expected: UUID format (not null, not empty)
```

### Step 1.4: Retrieve Order by ID

```bash
curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID | jq .
```

**Expected Response (200 OK)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "totalAmount": 99.99,
  "currency": "USD",
  "items": [
    // Same snapshot as created
  ],
  "idempotencyKey": "scenario-1-1700000000",
  "createdAt": "2026-11-19T12:00:00Z"
}
```

**Validation**:
- Order retrieved successfully
- Snapshot preserved (names, prices, quantities unchanged)
- Same ID as returned from creation ✓

---

## Validation Scenario 2: Idempotent Repetition (P1 Requirement)

**Story**: Repetir creación con la misma clave → mismo pedido, sin duplicación.

### Step 2.1: Create First Order

```bash
IDEMPOTENCY_KEY="scenario-2-$(date +%s)"

RESPONSE_1=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "items": [
      { "productId": "WIDGET_C", "quantity": 1 }
    ]
  }')

ORDER_ID_1=$(echo $RESPONSE_1 | jq -r .id)
echo "First order: $ORDER_ID_1"
```

### Step 2.2: Repeat Same Request

```bash
# Same idempotency key, same body
RESPONSE_2=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "items": [
      { "productId": "WIDGET_C", "quantity": 1 }
    ]
  }')

ORDER_ID_2=$(echo $RESPONSE_2 | jq -r .id)
HTTP_STATUS=$(echo $RESPONSE_2 | jq -r .status // "error")
echo "Second order: $ORDER_ID_2"
echo "Status: $HTTP_STATUS"
```

### Step 2.3: Validate Idempotency

**Check 1: Same Order ID**
```bash
if [ "$ORDER_ID_1" == "$ORDER_ID_2" ]; then
  echo "✓ Idempotent: Both requests returned same order ID"
else
  echo "✗ FAIL: Different order IDs returned (duplicate order created)"
  exit 1
fi
```

**Check 2: Verify Only One Order in Database**

```bash
# Query MongoDB directly
mongosh checkout-service --eval "db.orders.find({idempotencyKey: '$IDEMPOTENCY_KEY'}).count()"
# Expected: 1 (exactly one order for this key)
```

**Check 3: Verify Only One Event in Database**

```bash
mongosh checkout-service --eval "db.order_events.find({orderId: ObjectId('$ORDER_ID_1')}).count()"
# Expected: 1 (exactly one event for this order)
```

---

## Validation Scenario 3: Conflict Detection (P1 Requirement)

**Story**: Usar misma clave con contenido diferente → rechazo seguro; pedido original preservado.

### Step 3.1: Create Order with Key

```bash
IDEMPOTENCY_KEY="scenario-3-$(date +%s)"

curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": 1 }
    ]
  }' | jq .
```

### Step 3.2: Reuse Key with Different Content

```bash
# Same key, different product
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "items": [
      { "productId": "WIDGET_B", "quantity": 2 }
    ]
  }' | jq .
```

### Step 3.3: Expected Response (409 Conflict)

```json
{
  "error": "IDEMPOTENCY_KEY_CONFLICT",
  "message": "Idempotency key already used for a different request within 24 hours",
  "idempotencyKey": "scenario-3-1700000000",
  "details": {
    "conflictingOrderId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Validation**:
- HTTP status 409 (Conflict)
- Original order NOT modified
- Conflicting request rejected ✓

---

## Validation Scenario 4: Validation Failure (P1 Requirement)

**Story**: Solicitud inválida (sin ítems, cantidad negativa, producto inexistente) → rechazo sin crear pedido.

### Step 4.1: Empty Items Array

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: validation-empty-$(date +%s)" \
  -d '{
    "items": []
  }' | jq .
```

**Expected**: 400 Bad Request, `VALIDATION_FAILED`, "At least one item is required"

### Step 4.2: Negative Quantity

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: validation-qty-$(date +%s)" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": -1 }
    ]
  }' | jq .
```

**Expected**: 400 Bad Request, `VALIDATION_FAILED`, "Quantity must be positive"

### Step 4.3: Non-Existent Product

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: validation-prod-$(date +%s)" \
  -d '{
    "items": [
      { "productId": "NONEXISTENT_XYZ", "quantity": 1 }
    ]
  }' | jq .
```

**Expected**: 422 Unprocessable Entity, `PRODUCT_NOT_FOUND`

### Step 4.4: Duplicate Product in Request

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: validation-dup-$(date +%s)" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": 1 },
      { "productId": "WIDGET_A", "quantity": 2 }
    ]
  }' | jq .
```

**Expected**: 400 Bad Request, `VALIDATION_FAILED`, "Duplicate product IDs not allowed"

### Step 4.5: Verify No Orders Created

```bash
# Query MongoDB
mongosh checkout-service --eval "db.orders.countDocuments({})"
# Should NOT increase for any of the above failed requests
```

---

## Validation Scenario 5: Atomic Persistence (P1 Requirement)

**Story**: Falha entre persistência do pedido e evento → nenhum registro deixado; operação segura para repetição.

### Step 5.1: Setup (Requires Test Environment)

This scenario requires simulating a failure during persistence. In production testing:

1. Temporarily disable event collection writes (via test configuration)
2. Send a valid create request
3. Verify rollback occurred (no order created)

**In Integration Tests** (via code):
```java
@Test
public void testAtomicTransactionRollback() {
    // Inject failing repository
    // Send valid request
    // Verify: no order, no event in database
    // Verify: response indicates failure (500 or 422)
    // Verify: safe to retry with same idempotency key
}
```

### Step 5.2: Expected Behavior

- **No Order Persisted**: Database query returns 0 for new order
- **No Event Persisted**: Database query returns 0 for corresponding event
- **Response**: 500 Internal Server Error or 422 depending on failure type
- **Retry Safe**: Same idempotency key can be used to retry after recovery

---

## Validation Scenario 6: Snapshot Preservation (P1 Requirement)

**Story**: Después de crear pedido, cambiar datos del producto confiable → consulta debe devolver snapshot original.

### Step 6.1: Create Order

```bash
ORDER=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: snapshot-$(date +%s)" \
  -d '{
    "items": [
      { "productId": "WIDGET_A", "quantity": 1 }
    ]
  }')

ORDER_ID=$(echo $ORDER | jq -r .id)
ORIGINAL_PRICE=$(echo $ORDER | jq .items[0].unitPrice)
echo "Order $ORDER_ID created with price: $ORIGINAL_PRICE"
```

### Step 6.2: Modify Trusted Product (Test-Only)

```bash
# Admin endpoint or direct DB update (test-only operation)
# Change WIDGET_A price from 29.99 to 39.99 in trusted source
curl -X PUT http://localhost:8080/admin/products/WIDGET_A \
  -H "Content-Type: application/json" \
  -d '{"unitPrice": 39.99}'

# Verify product source updated
curl -s http://localhost:8080/admin/products/WIDGET_A | jq .unitPrice
# Expected: 39.99
```

### Step 6.3: Retrieve Order Again

```bash
RETRIEVED=$(curl -s -X GET http://localhost:8080/api/v1/orders/$ORDER_ID)
RETRIEVED_PRICE=$(echo $RETRIEVED | jq .items[0].unitPrice)
echo "Order retrieved with price: $RETRIEVED_PRICE"
```

### Step 6.4: Validate Snapshot Preserved

```bash
if [ "$ORIGINAL_PRICE" == "$RETRIEVED_PRICE" ]; then
  echo "✓ Snapshot preserved: $ORIGINAL_PRICE (unchanged despite product source update)"
else
  echo "✗ FAIL: Price changed from $ORIGINAL_PRICE to $RETRIEVED_PRICE"
  exit 1
fi
```

---

## Troubleshooting

### Service Won't Start

**Symptom**: `Connection refused` or MongoDB connection timeout

**Solution**:
```bash
# Check MongoDB is running
docker ps | grep mongodb

# Check service logs
tail -f logs/application.log

# Verify network connectivity
nc -zv localhost 27017

# Restart MongoDB
docker restart mongodb
```

### Order Creation Returns 500

**Symptom**: "INTERNAL_SERVER_ERROR"

**Solution**:
1. Check application logs for stack trace
2. Verify MongoDB collections exist (service should auto-create)
3. Verify `checkout-service` database exists
4. Restart service: `mvn spring-boot:run`

### Idempotency Not Working

**Symptom**: Same key creates duplicate orders

**Solution**:
1. Verify idempotency_keys collection exists in MongoDB
2. Verify `@Transactional` annotation on CreateOrderUseCase
3. Check application logs for transaction errors
4. Query MongoDB directly:
   ```bash
   mongosh checkout-service --eval "db.idempotency_keys.find({}).pretty()"
   ```

### Order Query Returns 404

**Symptom**: Created order cannot be retrieved

**Solution**:
1. Verify order ID format (should be UUID)
2. Query MongoDB directly:
   ```bash
   mongosh checkout-service --eval "db.orders.find({id: 'your-order-id'}).pretty()"
   ```
3. Check if orders were created in correct database

---

## Automated Test Suite

For comprehensive validation, run the test suite:

```bash
cd services/checkout-service

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CreateOrderUseCaseTest

# Run with integration tests (requires MongoDB)
mvn verify
```

**Expected Results**:
- All unit tests pass
- All integration tests pass with Testcontainers MongoDB
- Code coverage > 80% for domain and application layers

---

## Success Criteria Validation

| Criterion | Validation | Status |
|-----------|-----------|--------|
| SC-001: Totals calculated correctly | Scenario 1.3 Check 1 | ✓ Automated |
| SC-002: Snapshot preserved on retrieval | Scenario 6 | ✓ Automated |
| SC-003: Idempotency ensures one order | Scenario 2.3 | ✓ Automated |
| SC-004: Atomic persistence (no partial state) | Scenario 5 | ✓ Integration test |
| SC-005: User can create and retrieve order | Scenario 1 | ✓ Automated |
| SC-006: No partial orders on validation failure | Scenario 4 | ✓ Automated |

---

## Ready for Implementation

✓ Validation scenarios specified and runnable  
✓ Expected outcomes documented  
✓ Troubleshooting guide provided  
✓ All P1 requirements covered by scenarios  
✓ Next: Implementation via /speckit-tasks
