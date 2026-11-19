# Research: Checkout de Pedidos - Technical Decisions

**Phase**: 0 (Research & Technical Resolution)  
**Date**: 2026-11-19  
**Branch**: 001-checkout-orders

---

## Research Topic 1: MongoDB Multi-Document Transactions in Spring Boot

### Decision
**Chosen**: Use Spring Boot 4.1.2 and a Spring infrastructure adapter with `@Transactional` and `MongoTransactionManager`. The framework-free use case invokes a transaction port; its order, `order_events` outbox, and idempotency writes share the same MongoDB transaction.

### Rationale
1. **Spring Integration**: `@Transactional` is the idiomatic approach in Spring Boot; Spring Data MongoDB automatically manages MongoDB sessions and transaction state.
2. **Multi-Document Atomicity**: A MongoDB replica set supports the required multi-document transaction; no sharding is required for this increment.
3. **Simplicity for P1**: Avoids manual session/transaction management; focus remains on domain logic and idempotency.
4. **Clear Boundaries**: The application owns the business decisions and the transaction port; the Spring adapter owns transaction mechanics.

### Configuration
- **Spring Boot**: Keep the existing Maven parent at 4.1.2 and use its managed dependency versions.
- **Database configuration**: Set `spring.mongodb.uri` to a replica-set URI with the dedicated `checkout-service` database.
- **Transactional Method**: Annotate the Spring-managed transaction adapter, not `CreateOrderUseCase`. Use the same `MongoDatabaseFactory` for `MongoTransactionManager` and `MongoTemplate`.
- **Session Lifetime**: Managed by Spring for participating operations; calls must cross the transactional proxy.

### Failure Scenario Handling
- If a write fails and rollback is confirmed, neither order, event, nor idempotency record remains.
- A network failure during commit can leave the outcome unknown. Report a retryable failure without claiming rollback; retry with the same key and content within the window to resolve the committed order or create one if no commit occurred.

### Alternatives Considered
1. **Manual MongoDB Session Management**: More verbose, error-prone; @Transactional is preferred.
2. **Broker publishing now**: Requires a worker and delivery semantics outside this increment; persist only the outbox record now.
3. **Eventual Consistency (async events)**: Contradicts P1 requirement for atomic persistence; rejected.

---

## Research Topic 2: Idempotency Key Management Strategy (24-Hour Deduplication Window)

### Decision
**Chosen**: Store one record per key in `idempotency_keys` with a unique key index, a hash of normalized product IDs and quantities, and `expiresAt` set to the creation timestamp plus 24 hours. Capture the timestamp in the transaction immediately before persisting the new order; it becomes durable only when the transaction commits. The window starts before commit confirmation, so a long transaction reduces the time remaining after confirmation. A confirmed rollback leaves no persisted window. An `expiresAt` TTL index is only asynchronous cleanup; application logic checks expiration and atomically replaces an expired record without waiting for TTL deletion.

### Rationale
1. **Atomic Conflict Detection**: A unique index on the key prevents simultaneous records with different hashes.
2. **Cleanup**: TTL deletion may be delayed; explicit expiration makes the 24-hour boundary independent of it.
3. **Concurrency**: After a duplicate-key/write conflict aborts a transaction, re-read or retry in a fresh transaction; bound retries and report exhaustion as retryable.
4. **Failure Handling**: The key claim, order, and event commit together; an unknown commit outcome is resolved by a same-key retry within the deduplication window.

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
- Unique index: `{key: 1}`
- TTL index: `{expiresAt: 1}` with `expireAfterSeconds: 0`

### Conflict Handling
1. **Same key, same content**: Return existing order (idempotent success)
2. **Same key, different content**: Reject with HTTP 409 Conflict (preserve original order)
3. **New key**: Proceed with order creation
4. **Expired key**: Atomically replace its record and proceed, whether TTL cleanup has run or not.

### Alternatives Considered
1. **Outbox + Event Sourcing**: Over-engineered for P1; deferred.
2. **In-Memory Cache (Redis)**: External dependency; MongoDB provides TTL natively.
3. **TTL as the deduplication boundary**: Rejected because deletion is asynchronous and may occur after expiration.
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
Register `MongoTransactionManager` with the same `MongoDatabaseFactory` used by `MongoTemplate`; transactions require a replica set. See [Spring Data MongoDB transactions](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html).

### Testing Strategy
1. **Unit Tests** (OrderTest, OrderItemTest): Test domain objects; no database needed.
2. **Integration Tests** (MongoRepositoryTest, CreateOrderUseCaseTest):
   - Use Testcontainers to spawn MongoDB container
   - Shared container across test suite for speed
   - Clean database or separate collection per test method

3. **Contract Tests** (OrderControllerTest): Mock repositories; focus on REST mapping and error responses.

### Dependencies (pom.xml)
Add the MongoDB starter and MongoDB Testcontainers test dependencies using versions compatible with the existing Spring Boot 4.1.2 parent; do not pin Spring Boot 3.x starters.

### Alternatives Considered
1. **JPA + SQL Database**: Requires complex transaction management for multi-entity writes; MongoDB is simpler for document-oriented data.
2. **Spring Data REST Auto-Endpoints**: Not sufficient; requires custom validation logic.
3. **Manual MongoDB Driver**: Too verbose; Spring Data MongoDB abstracts boilerplate.

---

## Research Topic 4: Outbox Pattern vs. Direct Event Persistence

### Decision
**Chosen**: `order_events` is the outbox collection. Persist each creation event in the same transaction as the order and idempotency record. A worker and broker publication are out of scope.

### Rationale
1. **P1 Scope**: Broker publishing and worker execution are out of scope; the outbox record is persisted now.
2. **Atomicity Requirement**: Core requirement is that order and event persist together or fail together; @Transactional satisfies this.
3. **Simplicity**: `order_events` is the one event/outbox collection; do not create a second collection.
4. **Evolutionary**: A later increment can define worker state, retries, and delivery semantics for the existing outbox.

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
When a later increment requires broker publishing, design the worker and delivery-state semantics around the existing `order_events` collection; do not add a duplicate event collection.

### Alternatives Considered
1. **Full Outbox Pattern Now**: Unnecessary complexity; deferred to P2.
2. **Event Sourcing**: Over-engineered for P1; domain events suffice.
3. **Eventual Consistency (async events)**: Contradicts atomic persistence requirement; rejected.
4. **Synchronous Broker Publishing**: Would require broker availability in P1; explicitly out-of-scope.

---

## Summary of Technical Decisions

| Topic | Decision | Trade-off |
|-------|----------|-----------|
| Transactions | Spring @Transactional adapter + MongoDB multi-doc ACID | Requires a replica set and transaction manager |
| Idempotency | Unique key, explicit expiry, TTL cleanup, and content hash | Atomic replacement handles the 24-hour boundary even when TTL is delayed |
| MongoDB Integration | Spring Data MongoDB + Testcontainers | External dependency; standard in Spring ecosystem |
| Event Persistence | `order_events` is the outbox written atomically | Worker and broker publication deferred |

All decisions maintain **hexagonal architecture principles**: business rules isolated in domain layer, use cases orchestrate atomic operations, MongoDB detail hidden behind repository ports.

---

## Implementation Readiness

✓ All research items resolved  
✓ No NEEDS CLARIFICATION remaining  
✓ Ready for Phase 1: Data Model & Contracts design  
✓ Ready for Phase 2: Task generation and implementation
