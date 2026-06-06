# adr-orchestrated-order-saga: Coordinate order placement with an orchestrated saga in Order Service

## Status

Accepted

## Context

In UrbanCart, placing an order is not a single-service operation. A successful checkout must read the customer's cart from the `Cart Service`, secure a strong stock reservation from the `Inventory Service`, drive a payment intent through the `Payment Service` (which in turn talks to the external `Payment Provider`), and only then confirm the order and hand it off to the `Fulfilment Service`. These steps span multiple bounded contexts, each owning its own database — `Inventory DB`, `Payment DB`, and `Order DB` are separate `PostgreSQL 16` instances — so there is no shared transaction that can atomically commit the whole checkout.

Because the system is decomposed into independently deployable services, a distributed-transaction protocol like two-phase commit across `Inventory Service`, `Payment Service`, and `Order Service` is neither available nor desirable: it would couple the services, hurt availability, and conflict with the 99.9% target and 200–500ms p95 budget. The platform therefore needs a way to coordinate a multi-step, partially-failing workflow and to undo earlier steps when a later one fails — for example, releasing a stock reservation if the payment intent is declined.

The qualities in tension are consistency (no order is confirmed without both a reservation and a captured payment), operability (the team must be able to see and audit why a checkout failed), and latency (the consumer is waiting synchronously for a confirmation). The brief explicitly mandates an orchestrated saga with a central coordinator, which directs the design toward a single owner of the checkout workflow rather than a diffuse, emergent one.

## Decision

The `Order Service` acts as the saga orchestrator for order placement. When the `API Gateway` routes a validated checkout request to it, the `Order Service` starts a saga and persists pending order and saga state in the `Order DB` before doing any downstream work. It then drives the steps explicitly: it reads cart contents from the `Cart Service`, requests a strong reservation from the `Inventory Service`, and initiates a payment intent through the `Payment Service`, all over synchronous `HTTPS/REST`.

Each step transition is recorded in the `Order DB` so the saga's position is durable and recoverable. On success, the `Order Service` marks the order confirmed and publishes an `order-confirmed` event to the `Event Backbone` (`Amazon SNS + SQS`), which the `Fulfilment Service` and `Notification Service` consume asynchronously from their own queues. On failure of any step, the orchestrator runs the corresponding compensations — most importantly instructing the `Inventory Service` to release a reservation it previously secured — and cancels the order.

This makes the `Order Service` the single, auditable authority over the checkout lifecycle. The downstream services (`Inventory Service`, `Payment Service`) remain ignorant of the overall workflow; they expose narrow REST operations (reserve/release, create/confirm intent) and trust the orchestrator to sequence and compensate them. The synchronous calls inside the saga are governed by the platform's resilience policy — timeouts, retries with backoff, and circuit breakers — so a slow or failing dependency does not hang the saga indefinitely.

## Alternatives Considered

### Choreographed, event-only saga over the Event Backbone

Each service would react to events on the `Event Backbone` — for example, `Inventory Service` reserving stock on an `order-started` event, `Payment Service` charging on a `stock-reserved` event — with no central coordinator. Rejected because central orchestration gives clearer control, visibility, and compensation for the multi-step checkout, whereas a purely choreographed flow scatters that logic across the `Inventory Service`, `Payment Service`, and `Order Service`, making it hard to see the overall state, reason about compensation order, or audit why a given order failed. It also fits poorly with the synchronous consumer-facing confirmation the checkout flow requires, since the consumer is waiting for a result rather than fire-and-forget. The `Event Backbone`'s SNS+SQS semantics also offer weaker ordering guarantees, which complicates a choreography that depends on event sequence.

### Distributed transaction (two-phase commit) across the services

The `Order Service` would enroll `Inventory DB`, `Payment DB`, and the external `Payment Provider` in a single distributed transaction. Rejected because the `Payment Provider` is an external, provider-hosted system that cannot participate in a 2PC, and because holding locks across services during a multi-step checkout would damage availability and latency against the 99.9% / 200–500ms p95 targets. It would also tightly couple services that the bounded-context decomposition deliberately keeps independent.

### Order placement collapsed into a single service

Folding inventory and payment coordination into one service so the whole checkout commits in one local transaction. Rejected because it contradicts the bounded-context decomposition: `Inventory Service` and `Payment Service` have distinct ownership, scaling, and (for payment) compliance profiles, and merging them to simplify one workflow would sacrifice the independent deployability the platform is built around.

## Consequences

### Positive

- The entire checkout workflow has one owner, the `Order Service`, giving the team a single place to read, reason about, and audit order state and failure handling.
- Saga state persisted in the `Order DB` makes the workflow recoverable: after a crash the orchestrator can resume or compensate from a known position rather than leaving orphaned reservations or payments.
- Compensation is explicit and centralized — a declined payment deterministically triggers release of the `Inventory Service` reservation — which directly supports the no-overselling guarantee and keeps stock accurate.
- Downstream services stay simple and decoupled; `Inventory Service` and `Payment Service` expose narrow operations without knowing the checkout sequence, preserving bounded-context independence.

### Negative

- The `Order Service` becomes a coordination hotspot and a critical-path dependency for all order placement; its availability and latency directly bound the checkout SLA, so it must be carefully made idempotent and resilient.
- Every synchronous step (cart read, reservation, payment initiation) and every compensation must be idempotent, because retries with backoff and at-least-once delivery on the `Event Backbone` can cause duplicate invocations — this is real, ongoing implementation discipline the team now owns.
- Synchronous REST orchestration adds latency that accumulates across `Cart Service`, `Inventory Service`, and `Payment Service` calls, pressing against the 200–500ms p95 budget and making timeout, retry, and circuit-breaker tuning (Resilience4j) essential.
- Compensation logic is genuinely complex to get right: partial failures, in-flight payment confirmations, and the external `Payment Provider`'s webhook timing create edge cases (e.g. payment captured after the saga decided to cancel) that require careful reconciliation against the `Payment DB`.
- The orchestrator must be observable end to end — distributed tracing and structured logging across the saga steps are not optional, since without them a stuck or partially-compensated order is hard to diagnose.