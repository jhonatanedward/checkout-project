# Tasks: Checkout de Pedidos

**Input**: Design documents from `specs/001-checkout-orders/`

**Prerequisites**: `plan.md`, `spec.md`; also `data-model.md`, `contracts/rest-api.md`, `research.md`, and `quickstart.md`

**Organization**: Tasks are grouped by user story. The order within the phases follows the requested implementation sequence: domain and unit tests, MongoDB persistence with outbox and idempotency, then REST endpoints and integration tests.

**Service path**: The existing Maven module is `services/checkout-service` (not `services/checkout`). Java sources use the existing package root `dev.edward.ecommerce.checkout_service`.

**Outbox scope**: The `order_events` collection is the outbox: each order-created event is persisted in the same MongoDB transaction as its order so a future worker can read it. This increment does not include the worker or publishing to or consuming from a broker.

## Phase 1: Setup

**Purpose**: Add the runtime and test dependencies to the existing Java 21 Spring Boot Maven service.

- [ ] T001 Add Spring Web, Bean Validation, Spring Data MongoDB, MongoDB Testcontainers dependencies, and Maven Failsafe execution for `*IT` tests at `verify` to `services/checkout-service/pom.xml`, retaining the existing Spring Boot parent and test starter.

## Phase 2: Foundational

**Purpose**: The existing service module, Java 21 Maven setup, application entry point, and test source set already provide the project foundation. No additional blocking tasks are needed; start with domain behavior and unit tests.

**Checkpoint**: The existing `services/checkout-service` module is ready for feature implementation.

## Phase 3: User Story 1 - Criar pedido com total confiável (Priority: P1) — domínio e testes unitários

**Goal**: Model orders and immutable item snapshots, calculate totals from trusted product data, and implement create/read use cases independently of Spring and MongoDB.

**Independent Test**: Unit tests prove positive-quantity and non-duplicate item rules, exact server-side totals, snapshot creation from trusted product data, and lookup behavior without starting Spring or MongoDB.

### Domain and application

- [ ] T002 [P] [US1] Create `OrderId`, `OrderStatus`, and immutable `OrderItem` value types with UUID identity, positive quantity, positive unit price exactly representable with two decimal places, and exact calculated subtotal in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/order/OrderId.java`, `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/order/OrderStatus.java`, and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/order/OrderItem.java`.
- [ ] T003 [US1] Implement the immutable `Order` aggregate in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/order/Order.java`, validating a non-empty item list with no repeated product IDs and calculating its total from item subtotals using exact decimal arithmetic.
- [ ] T004 [US1] Add `OrderItem` unit tests for zero/negative quantity or price, incompatible currency or nonrepresentable precision, and exact subtotal calculation without rounding in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/domain/order/OrderItemTest.java`.
- [ ] T005 [US1] Add `Order` unit tests for empty and duplicate-product rejection, total calculation, and immutable item snapshots in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/domain/order/OrderTest.java`.
- [ ] T006 [P] [US1] Define the `TrustedProduct`, `OrderRepository`, and `TrustedProductCatalog` types/ports needed by the create flow in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/product/TrustedProduct.java`, `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/port/out/OrderRepository.java`, and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/port/out/TrustedProductCatalog.java`.
- [ ] T007 [US1] Implement `CreateOrderUseCase` to reject empty or repeated product lines, load authoritative product names and prices through `TrustedProductCatalog`, reject missing or invalid products, build snapshots, calculate the total, and save through `OrderRepository` in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderUseCase.java`.
- [ ] T008 [US1] Add isolated `CreateOrderUseCase` unit tests using in-memory port fakes for trusted values, ignored client-supplied values, missing products, repeated products, and all-or-nothing validation in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderUseCaseTest.java`.
- [ ] T009 [US1] Implement `GetOrderUseCase` using `OrderRepository`, returning the saved snapshot or a typed not-found result, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/order/GetOrderUseCase.java`.
- [ ] T010 [US1] Add `GetOrderUseCase` unit tests for existing and unknown order IDs in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/application/order/GetOrderUseCaseTest.java`.

**Checkpoint**: The order domain and use cases are validated with unit tests and do not depend on MongoDB, HTTP, or Spring annotations.

## Phase 4: User Story 3 - Tratar falhas sem estado parcial (Priority: P1) — MongoDB e outbox atômicos

**Goal**: Persist the order and its creation event to the dedicated checkout database in one MongoDB multi-document transaction managed by Spring `@Transactional`.

**Independent Test**: A MongoDB replica-set integration test proves both documents commit together and that an injected outbox write failure leaves neither document persisted.

### Persistence and transaction boundary

- [ ] T011 [P] [US3] Add MongoDB document mappings for the order aggregate and embedded item snapshots, using collection `orders`, the business UUID as the lookup key, and exact Decimal128 monetary values with unrepresentable values rejected, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/document/OrderDocument.java` and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/document/OrderItemDocument.java`.
- [ ] T012 [US3] Implement the MongoDB adapter for `OrderRepository`, mapping domain orders to and from `OrderDocument` and supporting lookup by business order ID, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/MongoOrderRepositoryAdapter.java`.
- [ ] T013 [P] [US3] Define the immutable `OrderCreatedEvent` and `OutboxRepository` port, including order ID, event type, creation timestamp, and event payload, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/event/OrderCreatedEvent.java` and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/port/out/OutboxRepository.java`.
- [ ] T014 [US3] Implement the MongoDB outbox document and adapter using the `order_events` collection, with a unique event/order identifier and no broker-publishing behavior, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/document/OutboxEventDocument.java` and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/MongoOutboxRepositoryAdapter.java`.
- [ ] T015 [P] [US3] Configure the dedicated checkout database using Spring Boot 4's `spring.mongodb.uri` and a `MongoTransactionManager` using the same `MongoDatabaseFactory` as `MongoTemplate` in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/config/MongoTransactionConfiguration.java` and `services/checkout-service/src/main/resources/application.yaml`.
- [ ] T016 [US3] Define a framework-free transaction port in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/port/out/OrderCreationTransaction.java`; implement it with a Spring-managed `@Transactional` adapter in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/MongoOrderCreationTransaction.java`; update `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderUseCase.java` to execute order and exactly one outbox event write within that port while keeping business decisions in the use case.
- [ ] T017 [US3] Add MongoDB Testcontainers replica-set configuration and persistence integration tests proving order/outbox commit together and failures injected at either order or outbox persistence leave neither document in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/MongoOrderCreationTransactionIT.java`.

**Checkpoint**: Every confirmed successful create commits one order and one outbox event; a confirmed rollback leaves neither. An unknown commit outcome is reported without assuming rollback. No broker is configured or invoked.

## Phase 5: User Story 2 - Repetir criação com segurança (Priority: P1) — idempotência

**Goal**: Deduplicate create requests for 24 hours, return the original order for the same normalized content, and reject different content for a still-active key.

**Independent Test**: Application tests prove replay returns the original order, a changed request conflicts, expired keys can be reused, and competing requests cannot create duplicate orders or outbox events.

### Idempotency

- [ ] T018 [US2] Define the idempotency record, typed replay/conflict outcomes, and persistence port with key, normalized request hash, order ID, creation time, and expiration time in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/domain/idempotency/IdempotencyRecord.java` and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/port/out/IdempotencyRepository.java`.
- [ ] T019 [P] [US2] Implement deterministic request normalization and SHA-256 content hashing over product IDs and quantities, sorting by product ID so semantically identical item orderings hash identically, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/idempotency/OrderRequestFingerprint.java` and `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/application/idempotency/OrderRequestFingerprintTest.java`.
- [ ] T020 [US2] Implement the MongoDB idempotency adapter and indexes: unique index on the key and TTL index on `expiresAt`; check expiry explicitly and atomically replace expired records even before TTL deletion. Translate duplicate-key/write conflicts into an unchecked, framework-free exception that propagates across the transactional proxy and causes rollback in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/document/IdempotencyDocument.java` and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/mongo/MongoIdempotencyRepositoryAdapter.java`.
- [ ] T021 [US2] Extend `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderUseCase.java` to decide replay or conflict before catalog lookup and atomically claim the key and save the order, event, and completed record via ports. Inject `java.time.Clock` and capture the creation timestamp inside the transaction immediately before persisting the new order; set expiration to that timestamp plus 24 hours, with no persisted window on confirmed rollback. In `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/persistence/MongoOrderCreationTransaction.java` keep only the Spring transaction boundary and do not swallow retryable conflict exceptions before rollback. Handle them outside the transactional proxy only after the transaction ends, then retry or re-read in a fresh transaction; bound retries and explicitly report exhaustion. For unknown commit outcome, return a retryable failure without claiming rollback; require retry with the same key and content within the window.
- [ ] T022 [US2] Add idempotency use-case tests for same-key/same-content replay even when the catalog is unavailable, different-content conflict, expired-key reuse, retry exhaustion, and unknown commit outcomes in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderIdempotencyTest.java`.

**Checkpoint**: Replays resolve to the original order and never add another order or outbox event; active-key content conflicts do not modify existing records.

## Phase 6: User Story 4 - Usar fonte local confiável de produtos (Priority: P2)

**Goal**: Supply authoritative product IDs, names, prices, and active state from controlled local configuration, without adding a product API.

**Independent Test**: Adapter tests prove configured values are authoritative, unknown/inactive products are rejected, and client-supplied names or prices cannot alter the catalog values.

### Local product source

- [ ] T023 [P] [US4] Add typed configuration properties for a controlled local product catalog with one currency and sample entries `WIDGET_A` at 29.99 USD and `WIDGET_B` at 40.01 USD in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/config/TrustedProductsProperties.java` and `services/checkout-service/src/main/resources/application.yaml`.
- [ ] T024 [US4] Implement the `TrustedProductCatalog` adapter backed by local configuration: reject malformed, unavailable, or incomplete catalog configuration as a source-level failure, with correction and restart as the recovery path; reject missing/invalid data for a specific identified product as a product-level validation failure, including its ID, name, active state, configured currency, and positive two-decimal-exact price, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/catalog/LocalTrustedProductCatalogAdapter.java`.
- [ ] T025 [US4] Add catalog adapter and use-case tests for configured, unknown and inactive products, invalid values, authoritative names/prices, simulated catalog unavailability with no order or event, and retry after recovery in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/catalog/LocalTrustedProductCatalogAdapterTest.java` and `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/application/order/CreateOrderUseCaseTest.java`.

**Checkpoint**: The service has a local trusted catalog and no product endpoint or external product-service dependency.

## Phase 7: User Story 1 - Criar e consultar pedidos (Priority: P1) — REST e integração

**Goal**: Expose the create and lookup use cases through the documented REST contract and verify the full checkout flow against MongoDB.

**Independent Test**: Run the application with a MongoDB replica set; create an order through HTTP, retrieve its original snapshot, and verify persisted order/outbox counts and documented error responses.

### REST endpoints and integration tests

- [ ] T026 [P] [US1] Create validated create-request, order-response, and order-item DTOs matching `contracts/rest-api.md` in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/dto/CreateOrderRequest.java`, `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/dto/OrderResponse.java`, and `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/dto/OrderItemResponse.java`.
- [ ] T027 [US1] Implement `OrderController` with `POST /api/v1/orders` and `GET /api/v1/orders/{orderId}`, returning 201 for new orders and 200 for a replay, in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/OrderController.java`; register the framework-free create/get use cases and UTC `Clock` as Spring beans, wire their ports to the catalog and Mongo adapters and ensure calls use the transactional proxy in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/config/CheckoutConfiguration.java`; verify context startup and bean wiring against the replica-set test database in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/OrderControllerIT.java`.
- [ ] T028 [P] [US1] Map malformed requests to 400, unknown order IDs to 404, idempotency conflicts to 409, nonexistent/inactive products or missing/invalid product-specific data to 422, unavailable/malformed/incomplete catalog as a whole to 503, and persistence or unknown-commit failures to explicit retryable 500/503 responses per `specs/001-checkout-orders/contracts/rest-api.md` in `services/checkout-service/src/main/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/ApiExceptionHandler.java`.
- [ ] T029 [US1] Add HTTP integration tests for valid creation and lookup, exact total, immutable snapshot after changing the test catalog name and price, malformed/invalid requests, missing product price (422), unavailable catalog as a whole (503), and unknown-order 404 using the MongoDB Testcontainers replica set in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/OrderControllerIT.java`.
- [ ] T030 [US2] Add replica-set HTTP integration tests for concurrent same-key/same-content and same-key/different-content requests, asserting one order/event, original-order replay or 409 as appropriate. Inject a controllable application clock to test replay before expiry and reuse at/after `createdAt + 24h`; advance that clock while MongoDB's TTL monitor still sees a future expiration, assert the expired record remains before retry, and verify reuse without TTL cleanup. Verify a failed creation with confirmed rollback does not persist a window in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/OrderIdempotencyIT.java`.
- [ ] T031 [US3] Add HTTP integration tests injecting order/outbox write failures with confirmed rollback and retry. Separately test both unknown-commit outcomes against actual replica-set state: committed order/event with the response lost must replay the original, and an uncommitted transaction with unknown outcome must create exactly one order/event on retry. Both errors must avoid promising rollback and both retries must use the same key/content within the window in `services/checkout-service/src/test/java/dev/edward/ecommerce/checkout_service/infrastructure/rest/OrderAtomicityIT.java`.

**Checkpoint**: The REST API, persistence, trusted catalog, atomic outbox, idempotency, and failure handling work together end-to-end.

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Keep the validation guide consistent with the final module paths, collection names, and runnable test commands.

- [ ] T032 Update `specs/001-checkout-orders/quickstart.md` to use `services/checkout-service`, the configured local product catalog, the `order_events` outbox collection, and the actual Maven integration-test commands; remove references to unimplemented product admin endpoints.
- [ ] T033 From the module directory, run `.\mvnw.cmd test` and `.\mvnw.cmd verify` using `services/checkout-service/mvnw.cmd`, then resolve failures in the feature's source and test files.

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: T001; required before compiling the feature.
- **Foundational (Phase 2)**: No additional work; the Maven module and Java 21 setup already exist.
- **US1 domain/application (Phase 3)**: T002–T010 depend on T001; these unit tests run without MongoDB.
- **US3 MongoDB/outbox (Phase 4)**: T011–T017 depend on US1 domain ports and use cases; transaction test depends on all persistence and transaction-boundary tasks.
- **US2 idempotency (Phase 5)**: T018–T022 depend on the create use case and Mongo transaction boundary; the idempotency adapter and transaction changes build on US3 persistence.
- **US4 local catalog (Phase 6)**: T023–T025 implement and test the catalog port introduced in US1; they must complete before full REST integration testing.
- **US1 REST (Phase 7)**: T026–T029 depend on the application use cases, Mongo/outbox transaction, idempotency, and local catalog. T030 verifies US2 over HTTP; T031 verifies US3 over HTTP.
- **Polish (Phase 8)**: T032–T033 depend on the endpoint and integration-test phases being complete.

### User-story dependencies

- **US1 domain and application**: Starts after setup; no dependency on another story.
- **US3 persistence and atomic outbox**: Requires the US1 domain/application ports and create flow.
- **US2 idempotency**: Requires the US3 MongoDB transaction boundary to atomically claim the key with order and outbox writes.
- **US4 local product source**: Implements the trusted-product port from US1; REST end-to-end validation requires its completion.
- **US1 REST completion**: Requires US2, US3, and US4 for a complete externally usable checkout.

### Parallel opportunities

- T002 can run alongside T004 because value types and their tests are separate files; T004 must be run after T002 exists.
- T004 and T005 can be implemented in parallel after T002 and T003 have defined their corresponding domain types.
- T006 and T013 are separate port/domain files and may be prepared independently, but the use case must consume the resulting ports.
- T011, T013, and T015 are separate Mongo document/event/configuration files and can be implemented in parallel after the domain types are defined.
- T019 fingerprint logic/tests and T023 catalog-properties binding can be developed in parallel; T024 depends on T023.
- T026 and T028 are separate REST DTO/error-handler files and can be implemented in parallel; T027 depends on the DTOs and application use cases.
- T029, T030, and T031 cover distinct HTTP scenarios; keep their test data isolated so their tests can run concurrently after the server and Testcontainers setup are ready.

## Parallel Example: Domain and Unit Tests First

```text
1. Implement T002 and T003 (domain types and aggregate).
2. In parallel, prepare T004 and T005 (unit-test files); run them once the corresponding types exist.
3. Implement T006–T009 and run the isolated use-case unit tests before starting MongoDB work.
```

## Parallel Example: Persistence and Outbox

```text
After the domain ports and event model exist:
- Implement the order document/adapter (T011–T012).
- Implement the outbox model/adapter (T013–T014).
- Configure the checkout database and transaction manager (T015).
Then implement the transaction boundary (T016) and verify rollback/commit behavior (T017).
```

## Implementation Strategy

1. **Domain slice first**: Complete T001–T010; prove order invariants and use-case behavior with unit tests.
2. **Atomic persistence slice**: Complete T011–T017; prove MongoDB order/outbox atomicity before exposing HTTP.
3. **Idempotency slice**: Complete T018–T022; prove replay, conflict, expiry, and concurrent-claim behavior.
4. **Trusted local catalog**: Complete T023–T025; keep product API and external product dependencies out of scope.
5. **REST and end-to-end validation**: Complete T026–T031; validate contract and failure behavior through HTTP.
6. **Polish and verify**: Complete T032–T033 and run the module's Maven tests.

## Format Validation

All implementation tasks use the required checkbox, sequential `T` identifier, optional `[P]` marker only for parallelizable work, user-story label for story tasks, and an explicit repository-relative file path.
