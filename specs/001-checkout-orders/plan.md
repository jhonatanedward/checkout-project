# Implementation Plan: Checkout de Pedidos

**Branch**: `001-checkout-orders` | **Date**: 2026-11-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-checkout-orders/spec.md`

## Summary

Implementar o incremento P1 de criação e consulta de pedidos em um serviço de checkout Spring Boot. O serviço deve receber produtos e quantidades, validar contra uma fonte local confiável, calcular o total no servidor, persistir o pedido e seu evento de criação de forma atômica usando MongoDB e @Transactional, e suportar repetição segura via chaves de idempotência. A deduplicação funciona por 24 horas; falhas não deixam estado parcial.

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.x)

**Primary Dependencies**: Spring Framework (MVC/REST), Spring Data MongoDB, MongoDB

**Storage**: MongoDB - collections de pedidos e outbox em banco de dados dedicado ao serviço de checkout

**Transactions**: Spring @Transactional com suporte a transações multi-documento do MongoDB

**Testing**: JUnit 5, Testcontainers (MongoDB), Mockito

**Target Platform**: Web service (Spring Boot)

**Project Type**: Web service / REST API (checkout)

**Performance Goals**: Latência aceitável em criação de pedido (<500ms p95); suporte a múltiplas criações concorrentes sem duplicação

**Constraints**: Consistência atômica (pedido + evento sempre juntos); deduplicação por 24 horas; rejeição de pedidos parciais

**Scale/Scope**: Foco inicial no P1; extensível para pagamentos, estoque, broker em incrementos futuros

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
├── src/main/java/com/checkout/
│   ├── domain/              # Núcleo: entities, value-objects, business rules
│   │   ├── order/
│   │   │   ├── Order.java
│   │   │   ├── OrderItem.java
│   │   │   ├── OrderId.java
│   │   │   └── OrderStatus.java
│   │   ├── product/
│   │   │   ├── TrustedProduct.java
│   │   │   └── TrustedProductRepository.java (porta)
│   │   ├── idempotency/
│   │   │   ├── IdempotencyKey.java
│   │   │   └── IdempotencyKeyRepository.java (porta)
│   │   └── event/
│   │       ├── OrderCreatedEvent.java
│   │       └── OrderEventRepository.java (porta)
│   │
│   ├── application/        # Casos de uso
│   │   ├── CreateOrderUseCase.java
│   │   └── GetOrderUseCase.java
│   │
│   ├── infrastructure/      # Adaptadores
│   │   ├── rest/
│   │   │   └── OrderController.java
│   │   ├── persistence/
│   │   │   ├── MongoOrderRepository.java
│   │   │   ├── MongoIdempotencyRepository.java
│   │   │   ├── MongoOrderEventRepository.java
│   │   │   ├── LocalTrustedProductRepository.java
│   │   │   └── OrderDocument.java (MongoDB document)
│   │   └── config/
│   │       └── CheckoutConfig.java
│   │
│   └── CheckoutApplication.java
│
├── src/test/java/com/checkout/
│   ├── domain/
│   │   └── order/OrderTest.java
│   ├── application/
│   │   ├── CreateOrderUseCaseTest.java
│   │   └── GetOrderUseCaseTest.java
│   └── infrastructure/
│       ├── rest/OrderControllerTest.java
│       └── persistence/MongoRepositoryTest.java (Testcontainers)
│
├── pom.xml
└── README.md
```

**Structure Decision**: Arquitetura hexagonal com separação clara entre domínio (entities, regras de negócio), aplicação (casos de uso), e adaptadores (REST, MongoDB). MongoDB em coleções dedicadas (checkout-db) com suporte a transações multi-documento. Spring @Transactional garante atomicidade pedido+evento.

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
- **POST /api/v1/orders**: Create order (201, 200 idempotent, 400 validation, 409 conflict, 422 business rule, 500 error)
- **GET /api/v1/orders/{orderId}**: Retrieve order (200, 404)
- **Idempotency Semantics**: 24-hour window, same key + same content = idempotent, same key + different content = 409
- **Error Response Format**: Standard error structure with details

#### 3. Quickstart & Validation (`quickstart.md`) ✓
- **6 Validation Scenarios**:
  1. Create valid order (total calculation, trusted source, retrieval)
  2. Idempotent repetition (same order, no duplicate)
  3. Conflict detection (different content, 409 response)
  4. Validation failures (empty, negative, non-existent, duplicate products)
  5. Atomic persistence (rollback, no partial state)
  6. Snapshot preservation (product data changes don't affect order)
- **Setup Instructions**: Prerequisites, MongoDB startup, service startup
- **Troubleshooting**: Common issues and solutions
- **Success Criteria Validation**: All SC-001 through SC-006 covered

#### 4. Project Structure
```
services/checkout-service/
├── src/main/java/com/checkout/
│   ├── domain/           → Entities, value-objects, business rules
│   ├── application/      → Use cases (CreateOrderUseCase, GetOrderUseCase)
│   ├── infrastructure/   → REST, MongoDB, configuration
│   └── CheckoutApplication.java
├── src/test/java/com/checkout/
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

**Phase 2: Tasks** → Requires `/speckit-tasks` command (not part of plan phase)

---

## Artifacts Generated

```
specs/001-checkout-orders/
├── plan.md                           # This file (planning output)
├── research.md                       # Phase 0: Technology decisions & alternatives (9.6 KB)
├── data-model.md                     # Phase 1: Entities, MongoDB schema, relationships (10.7 KB)
├── quickstart.md                     # Phase 1: Validation scenarios, troubleshooting (14.8 KB)
└── contracts/
    └── rest-api.md                   # Phase 1: API contracts, error responses (11 KB)
```

---

## Next Steps

### For Implementation
1. Run `/speckit-tasks` to generate dependency-ordered task list in `tasks.md`
2. Create feature branch from `001-checkout-orders` for implementation
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

**Status**: Ready for task generation and implementation  
**Branch**: 001-checkout-orders  
**Review**: Constitution compliant; design complete; artifacts generated
