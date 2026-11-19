# Implementation Plan: Checkout de Pedidos

**Current branch**: `main` | **Feature directory**: `001-checkout-orders` | **Date**: 2026-11-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-checkout-orders/spec.md`

## Summary

Implementar o incremento P1 de criação e consulta de pedidos em um serviço de checkout Spring Boot. O serviço deve receber produtos e quantidades, validar contra uma fonte local confiável, calcular o total no servidor, persistir o pedido e seu evento de criação de forma atômica usando MongoDB e @Transactional, e suportar repetição segura via chaves de idempotência. A deduplicação funciona por 24 horas; falhas não deixam estado parcial.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (existing Maven parent)

**Primary Dependencies**: Spring Framework (MVC/REST), Spring Data MongoDB, MongoDB

**Storage**: MongoDB database `checkout-service`; collections `orders`, `order_events` (outbox), and `idempotency_keys`; connection via `spring.mongodb.uri`.

**Transactions**: A Spring-managed infrastructure adapter uses `@Transactional` and `MongoTransactionManager` backed by the same `MongoDatabaseFactory` as `MongoTemplate`. The framework-free use case executes the atomic work through a transaction port; order, outbox event, and idempotency record share the transaction. A replica set is required.

**Testing**: JUnit 5, Testcontainers (MongoDB), Mockito

**Target Platform**: Web service (Spring Boot)

**Project Type**: Web service / REST API (checkout)

**Performance Goals**: No latency target is specified for this increment; concurrent creation must preserve the idempotency and atomicity guarantees.

**Constraints**: Consistência atômica (pedido + evento sempre juntos); deduplicação por 24 horas; rejeição de pedidos parciais

**Scale/Scope**: Foco inicial no P1; extensível para pagamentos, estoque, broker em incrementos futuros

**Idempotency and failures**: Normalize product IDs and quantities independently of item order. A unique index on the key serializes claims; expiry is checked explicitly and an expired record may be replaced atomically without waiting for TTL cleanup. Duplicate-key/write conflicts are resolved after the failed transaction ends by retrying in a fresh transaction or re-reading the committed record; retries are bounded and exhaustion is reported as retryable. If commit outcome is unknown, do not claim rollback or create a new key: report a retryable error and retry with the same key and content.

**Catalog and money**: The local controlled catalog uses one configured currency; positive prices and totals must be exactly representable to two decimal places without rounding. Missing, invalid, or unavailable catalog data fails explicitly; a corrected configuration may require a service restart. Tests simulate temporary catalog unavailability and recovery without requiring production hot reload.

## Constitution Check

**GATE: PASS**

✓ **I. Java para Código de Aplicação**: Spring Boot com Java 21 — conforme  
✓ **II. Arquitetura de Aprendizagem Progressiva**: Hexagonal obrigatória; foco em atomicidade, deduplicação, fonte local confiável de dados  
✓ **III. Simplicidade e Padrões com Propósito**: Spring @Transactional para consistência; MongoDB multi-documento para atomicidade (problema concreto)  
✓ **IV. Isolamento das Regras de Negócio**: Separação clara entre domínio (pedidos, validação, cálculo), aplicação (casos de uso), e adaptadores (REST, MongoDB)  
✓ **V. Confiabilidade Verificada e Repetição Segura**: Chave de idempotência de 24h; testes incluem falha de persistência, duplicação, e validação  
✓ **VI. Arquitetura Hexagonal como Padrão**: Núcleo com entities (Pedido, Item, Evento), use-cases (CriarPedido, ConsultarPedido); portas de persistência; adaptadores REST e MongoDB

## Project Structure

### Documentation (this feature)

```text
specs/001-checkout-orders/
├── plan.md              # This file (planning output)
├── research.md          # Phase 0 output (technologies, patterns, deduplication strategies)
├── data-model.md        # Phase 1 output (entities, relationships, MongoDB schema)
├── quickstart.md        # Phase 1 output (validation scenarios, test setup, run commands)
├── contracts/           # Phase 1 output (REST API contracts)
└── tasks.md             # Phase 2 output (dependency-ordered implementation tasks)
```

### Source Code (services/checkout-service)

```text
services/checkout-service/
├── src/main/java/dev/edward/ecommerce/checkout_service/
│   ├── domain/              # Pedido, item, produto, idempotência e evento
│   ├── application/
│   │   ├── order/           # Casos de uso de criar e consultar
│   │   └── port/out/        # Portas de catálogo, persistência e transação
│   ├── infrastructure/      # Adaptadores REST, catálogo, MongoDB e configuração
│   └── CheckoutServiceApplication.java
├── src/main/resources/application.yaml
├── src/test/java/dev/edward/ecommerce/checkout_service/
│   ├── domain/              # Testes unitários
│   ├── application/         # Testes de casos de uso
│   └── infrastructure/      # Testes de adaptadores e integração
├── pom.xml
└── mvnw.cmd
```

**Structure Decision**: Arquitetura hexagonal com domínio e casos de uso independentes de frameworks; adaptadores REST, catálogo local e MongoDB usam portas do núcleo. O banco dedicado `checkout-service` contém pedidos, outbox `order_events` e chaves de idempotência. O adaptador transacional Spring protege a operação completa.

## Phase 1: Design & Contracts (COMPLETE)

### Deliverables

#### 1. Data Model (`data-model.md`) ✓
- **Order Entity**: UUID id, status, totalAmount, currency, items array, idempotency key, timestamps
- **OrderItem**: Product snapshot with name, quantity, unit price, subtotal
- **OrderCreatedEvent**: Audit record of order creation
- **IdempotencyKey**: 24-hour deduplication with content-hash conflict detection
- **MongoDB Collections**: `orders`, `order_events`, `idempotency_keys`
- **Indexes**: Unique constraints, TTL cleanup, query optimization

#### 2. REST API Contracts (`contracts/rest-api.md`) ✓
- **POST /api/v1/orders**: Create order (201, 200 idempotent, 400 validation, 409 conflict, 422 product rule, 500 error, 503 temporary unavailability)
- **GET /api/v1/orders/{orderId}**: Retrieve order (200, 404)
- **Idempotency Semantics**: 24-hour window, same key + same content = idempotent, same key + different content = 409
- **Error Response Format**: Standard error structure with details

#### 3. Quickstart & Validation (`quickstart.md`) ✓
- **Validation**: Setup, create/lookup, replay, conflict, expiration, transaction failures, catalog recovery and snapshot preservation.
- **SC-005**: The 90% user-success criterion requires a separate user test; automated HTTP tests alone do not measure it.

#### 4. Project Structure
```
services/checkout-service/
├── src/main/java/dev/edward/ecommerce/checkout_service/
│   ├── domain/           → Entities, value-objects, business rules
│   ├── application/      → Use cases (CreateOrderUseCase, GetOrderUseCase)
│   ├── infrastructure/   → REST, MongoDB, configuration
│   └── CheckoutServiceApplication.java
├── src/test/java/dev/edward/ecommerce/checkout_service/
│   ├── domain/           → Entity tests
│   ├── application/      → Use case tests
│   └── infrastructure/   → Controller and repository tests
└── pom.xml              → Maven config with Spring Data MongoDB, Testcontainers
```

**Hexagonal Architecture**: Domain layer isolated; portas de persistência (TrustedProductRepository, OrderRepository, etc.); adaptadores Spring/MongoDB

---

## Constitution Re-Check (Post-Phase 1 Design)

**PASS** ✓

All principles maintained:
- ✓ I. Java 21 + Spring Boot
- ✓ II. Hexagonal mandatory; teaching atomicity, deduplication, trusted data
- ✓ III. Patterns have concrete purpose (consistency, deduplication, auditability)
- ✓ IV. Business rules isolated in domain layer
- ✓ V. Tests include failure scenarios (rollback, conflict, validation)
- ✓ VI. Hexagonal architecture enforced; core → ports → adapters

No complexity violations. MongoDB transactions justified by P1 requirement for atomic persistence.

---

## Completion Status

**Phase 0: Research** ✓ COMPLETE
- All technical unknowns resolved in `research.md`
- MongoDB transactions strategy defined
- Idempotency implementation approach documented
- Testing strategy established

**Phase 1: Design & Contracts** ✓ COMPLETE
- Data model defined with entities, relationships, validation rules
- MongoDB collections and indexes specified
- REST API contracts fully specified (requests, responses, errors)
- Quickstart guide with 6 validation scenarios
- Project structure aligned with hexagonal architecture

**Phase 2: Tasks** ✓ Generated in `tasks.md`; implementation remains pending.

---

## Artifacts Generated

```
specs/001-checkout-orders/
├── plan.md                           # This file (planning output)
├── research.md                       # Phase 0: Technology decisions & alternatives
├── data-model.md                     # Phase 1: Entities, MongoDB schema, relationships
├── quickstart.md                     # Phase 1: Validation guide
└── contracts/
    └── rest-api.md                   # Phase 1: API contracts, error responses
```

---

## Next Steps

### For Implementation
1. Review the dependency-ordered task list in `tasks.md`
2. Implement the tasks on the chosen working branch
3. Follow hexagonal architecture pattern:
   - Domain layer: entities, validation rules, business logic
   - Application layer: use cases (CreateOrderUseCase, GetOrderUseCase)
   - Infrastructure layer: REST controllers, MongoDB repositories, configuration
4. Write tests following scenario order from quickstart.md
5. Validate against success criteria

### For Code Review
- Verify domain layer has zero framework dependencies
- Confirm @Transactional covers order + event persistence
- Check idempotency repository indexes and TTL configuration
- Validate error handling matches contract specifications
- Ensure hexagonal boundaries respected (no domain code importing infrastructure)

---

**Status**: Tasks generated; implementation pending

**Feature directory**: 001-checkout-orders

**Review**: Constitution compliant; design complete; artifacts generated
