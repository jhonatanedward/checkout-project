# Research: Checkout de Pedidos - Technical Decisions

**Phase**: 0 (Research & Technical Resolution)  
**Date**: 2026-11-19  
**Branch**: 001-checkout-orders

---

## Research Topic 1: MongoDB Multi-Document Transactions in Spring Boot

### Decision
**Chosen**: Use Spring Framework `@Transactional` annotation with Spring Data MongoDB repositories to manage multi-document transactions at the application boundary (CreateOrderUseCase). MongoDB's client-side session management (automatically handled by Spring Data MongoDB 3.x) provides ACID guarantees for operations on a single shard, which is sufficient for P1 scope.

### Rationale
1. **Spring Integration**: `@Transactional` is the idiomatic approach in Spring Boot; Spring Data MongoDB automatically manages MongoDB sessions and transaction state.
2. **Multi-Document Atomicity**: MongoDB 4.0+ supports multi-document ACID transactions within a single shard. For a single checkout service and dedicated database, this is acceptable without sharding complexity.
3. **Simplicity for P1**: Avoids manual session/transaction management; focus remains on domain logic and idempotency.
4. **Clear Boundaries**: Transaction scope is explicit at the use-case level (create order + persist event as one unit).

### Configuration
- **Spring Data MongoDB**: Version 3.4.x or higher (bundled with Spring Boot 3.1+)
- **MongoDB Driver**: 4.8+ (supports transactions)
- **Transactional Method**: Place `@Transactional` on `CreateOrderUseCase.createOrder()` to ensure both order and event persist atomically.
- **Session Lifetime**: Automatically managed by Spring; no manual session creation required.

### Failure Scenario Handling
- If order persists but event fails → transaction rolls back; no order remains in DB
- If event persists but order fails → transaction rolls back; no event remains in DB
- If network fails mid-transaction → MongoDB automatically aborts; client receives error; safe to retry with idempotency key

### Alternatives Considered
1. **Manual MongoDB Session Management**: More verbose, error-prone; @Transactional is preferred.
2. **Outbox Pattern**: Would add complexity (separate outbox collection, additional processing) for P1; deferred to future increments.
3. **Eventual Consistency (async events)**: Contradicts P1 requirement for atomic persistence; rejected.

---

## Research Topic 2: Idempotency Key Management Strategy (24-Hour Deduplication Window)

### Decision
**Chosen**: Store idempotency keys in a dedicated MongoDB collection (`idempotency_keys`) with:
- Composite unique index on `(key, orderContent hash)` to detect content conflicts
- TTL index set to 86400 seconds (24 hours) to auto-delete expired keys
- Simple synchronous lookup before order creation

### Rationale
1. **Atomic Conflict Detection**: Unique index on (key + content hash) ensures conflicting requests are rejected before order creation attempt.
2. **Automatic Cleanup**: TTL index removes keys after 24 hours; no manual scheduled cleanup needed.
3. **Simplicity**: Single collection query; no distributed locking complexity for P1.
4. **Failure Handling**: If idempotency storage fails, the transaction as a whole fails (safe retry).

### Schema (idempotency_keys collection)
```json
{
  "_id": ObjectId,
  "key": "string (unique within 24h window)",
  "contentHash": "string (SHA-256 of request body)",
  "orderId": "string (reference to created order)",
  "createdAt": ISODate,
  "expiresAt": ISODate
}
```

**Indexes**:
- Unique compound index: `{key: 1, contentHash: 1}` with partial filter `{expiresAt: {$gt: now}}`
- TTL index: `{expiresAt: 1}` with `expireAfterSeconds: 0`

### Conflict Handling
1. **Same key, same content**: Return existing order (idempotent success)
2. **Same key, different content**: Reject with HTTP 409 Conflict (preserve original order)
3. **New key**: Proceed with order creation

### Alternatives Considered
1. **Outbox + Event Sourcing**: Over-engineered for P1; deferred.
2. **In-Memory Cache (Redis)**: External dependency; MongoDB provides TTL natively.
3. **Soft Deletes with Scheduled Cleanup**: Manual cleanup is error-prone; TTL index is automatic and reliable.
4. **Hash-based Deduplication Only**: Cannot detect content conflicts; rejected.

---

## Research Topic 3: Spring Boot MongoDB Integration and Testing

### Decision
**Chosen**:
- **Spring Data MongoDB**: Use `MongoRepository` interface and `@Document` annotations for entity mapping.
- **Configuration**: Application properties or Java config for connection string, database name, and transaction settings.
- **Testing**: Testcontainers with MongoDB for integration tests; embedded MongoDB for fast unit tests if needed.

### Rationale
1. **Spring Idiomatic**: MongoRepository follows Spring Data patterns; minimal boilerplate.
2. **Declarative Mapping**: `@Document`, `@Field`, and `@Index` annotations reduce manual serialization.
3. **Testcontainers**: Provides real MongoDB instance in tests; no mocking surprises at runtime.
4. **Database Isolation**: Dedicated checkout-service database ensures no conflicts with other services.

### Key Configuration Points
```java
@Configuration
public class MongoConfig {
    @Bean
    public MongoTransactionManager transactionManager(MongoClient mongoClient) {
        return new MongoTransactionManager(mongoClient.getDatabase("checkout-service"));
    }
}
```

### Testing Strategy
1. **Unit Tests** (OrderTest, OrderItemTest): Test domain objects; no database needed.
2. **Integration Tests** (MongoRepositoryTest, CreateOrderUseCaseTest):
   - Use Testcontainers to spawn MongoDB container
   - Shared container across test suite for speed
   - Clean database or separate collection per test method

3. **Contract Tests** (OrderControllerTest): Mock repositories; focus on REST mapping and error responses.

### Dependencies (pom.xml)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
    <version>3.2.0</version>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
```

### Alternatives Considered
1. **JPA + SQL Database**: Requires complex transaction management for multi-entity writes; MongoDB is simpler for document-oriented data.
2. **Spring Data REST Auto-Endpoints**: Not sufficient; requires custom validation logic.
3. **Manual MongoDB Driver**: Too verbose; Spring Data MongoDB abstracts boilerplate.

---

## Research Topic 4: Outbox Pattern vs. Direct Event Persistence

### Decision
**Chosen**: For P1, implement **direct atomic persistence** of order + event in a single transaction (not outbox pattern). Event records are stored in a separate `order_events` collection to separate concerns, but both writes happen in one transaction.

### Rationale
1. **P1 Scope**: Broker publishing is out-of-scope; no outbox pattern needed yet.
2. **Atomicity Requirement**: Core requirement is that order and event persist together or fail together; @Transactional satisfies this.
3. **Simplicity**: One collection (orders) + event collection (order_events) in a single transaction is cleaner than outbox.
4. **Evolutionary**: When P2 adds broker publishing, outbox can be introduced without changing domain logic (application layer insertion point).

### Schema (order_events collection)
```json
{
  "_id": ObjectId,
  "orderId": "string (reference to Order)",
  "eventType": "ORDER_CREATED",
  "createdAt": ISODate,
  "payload": {
    "orderId": "string",
    "totalAmount": number,
    "currency": "string",
    "itemCount": number
  }
}
```

### Outbox Introduction Path (Post-P1)
When P2 requires broker publishing:
1. Add `outbox_events` collection
2. Keep atomic write of order + order_events in P1 semantics
3. Introduce background job to publish outbox_events to broker
4. Maintain both tables until broker consumer confirms processing

### Alternatives Considered
1. **Full Outbox Pattern Now**: Unnecessary complexity; deferred to P2.
2. **Event Sourcing**: Over-engineered for P1; domain events suffice.
3. **Eventual Consistency (async events)**: Contradicts atomic persistence requirement; rejected.
4. **Synchronous Broker Publishing**: Would require broker availability in P1; explicitly out-of-scope.

---

## Summary of Technical Decisions

| Topic | Decision | Trade-off |
|-------|----------|-----------|
| Transactions | Spring @Transactional + MongoDB multi-doc ACID | Requires single shard; sufficient for P1 |
| Idempotency | Dedicated collection + TTL index + content hash | Adds one query per request; reliable 24h window |
| MongoDB Integration | Spring Data MongoDB + Testcontainers | External dependency; standard in Spring ecosystem |
| Event Persistence | Direct atomic write + separate collection (no outbox) | Outbox added later when broker publishing needed |

All decisions maintain **hexagonal architecture principles**: business rules isolated in domain layer, use cases orchestrate atomic operations, MongoDB detail hidden behind repository ports.

---

## Implementation Readiness

✓ All research items resolved  
✓ No NEEDS CLARIFICATION remaining  
✓ Ready for Phase 1: Data Model & Contracts design  
✓ Ready for Phase 2: Task generation and implementation
