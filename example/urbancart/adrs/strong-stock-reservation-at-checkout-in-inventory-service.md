# adr-strong-inventory-reservation: Strong stock reservation at checkout in Inventory Service

## Status

Accepted

## Context

UrbanCart serves a read-heavy marketplace where `Web Consumer` and `Mobile Consumer` actors spend most of their time browsing the catalog, with a comparatively small fraction of traffic converting to checkout. The browse path is explicitly optimized for low latency and scale: `Catalog Service` serves listings from `Catalog Cache` (`Redis 7`) on a hit and `Catalog DB` on a miss, and full-text queries are delegated to the `Managed Search Service`. These read paths tolerate eventual consistency by design — a product or its stock hint can be momentarily stale without harming correctness.

Checkout is a different regime. When `Order Service` runs the order-placement saga, it must guarantee that the stock it commits to a customer is genuinely available, because overselling a physical inventory creates downstream fulfilment failures, refunds, and customer harm. The point of contention is the stock row in `Inventory DB` (`PostgreSQL 16`): under concurrent checkouts for the same SKU, two saga instances must not both believe they hold the last unit.

The brief requires strong reservation at checkout only to prevent overselling while keeping browse paths fast and read-heavy. The forces in tension are consistency versus latency and throughput. Applying strong consistency everywhere would crush the read-heavy browse budget; applying eventual consistency everywhere would risk overselling at the one moment correctness is non-negotiable. The architecture therefore needs a sharp boundary: cheap, cacheable, eventually-consistent reads for browsing, and a strongly-consistent reservation only at the checkout step inside the order saga.

This decision is also constrained by the orchestrated-saga model: `Order Service` calls `Inventory Service` synchronously over `HTTPS/REST` during the saga, and on saga failure (for example a declined payment via `Payment Service`) it must be able to compensate and release the held stock so units are not stranded.

## Decision

`Inventory Service` performs synchronous, transactionally strong stock reservations against `Inventory DB` during the order-placement saga, and releases them via compensation if the saga fails. When `Order Service` reaches the reservation step, it calls `Inventory Service` over `HTTPS/REST` with the order line items; `Inventory Service` decrements available stock and writes a reservation record in a single database transaction, relying on `PostgreSQL 16` row-level locking (or a conditional update guarded by an availability predicate) so that concurrent reservations for the same SKU are serialized and cannot oversell.

The reservation is a first-class, owned resource of `Inventory Service` keyed to the order. If the saga later fails — a stock shortfall, a declined payment intent at the `Payment Provider`, or a timeout — `Order Service` invokes a compensating release, and `Inventory Service` returns the held units to available stock transactionally. This keeps the strong-consistency cost confined to the checkout critical section and the saga lifecycle.

Browse and search remain untouched by this guarantee. `Catalog Service` continues to serve reads from `Catalog Cache` and `Catalog DB` without coordinating with `Inventory Service` on every request, and any stock indications surfaced during browsing are treated as advisory and eventually consistent. The authoritative availability check happens only once, at reservation time, inside the saga.

To make the reservation step safe in a distributed system, the reservation call carries an idempotency key tied to the saga, so a `Resilience4j` retry from `Order Service` after a timeout does not double-reserve, and a compensation can be issued exactly once.

## Alternatives Considered

Eventual reservation via events was rejected because it risks overselling during the strongly-consistent checkout step.

### Eventual reservation via domain events

`Order Service` would publish a reservation-intent event to the `Event Backbone` (`Amazon SNS + SQS`), and `Inventory Service` would adjust stock asynchronously when it consumed the message. This was rejected because the checkout step demands a definitive yes/no on availability before payment is captured. With asynchronous, eventually-consistent reservation, two concurrent checkouts for the last unit could both be accepted before either decrement was applied, producing the exact overselling the brief forbids. The weaker ordering and at-least-once delivery of `SNS + SQS` makes it unsuitable for the one step in the system that requires strong consistency.

### Optimistic browse-time stock check with no formal reservation

The system could rely on the stock figures already cached in `Catalog Cache` and only re-check at fulfilment time, skipping a reservation entirely. This was rejected because cached stock is intentionally stale, and deferring the truth to `Fulfilment Service` moves the failure to after payment has been captured at the `Payment Provider` — turning an oversell into a forced refund flow and a poor customer experience, rather than a clean rejection during checkout.

### Distributed lock in Catalog Cache (Redis)

A Redis-based lock or counter in `Catalog Cache` could gate reservations. This was rejected because it splits the source of truth for stock away from `Inventory DB`, the datastore `Inventory Service` owns, and introduces lock-expiry and cache-eviction failure modes that can silently permit overselling. Keeping the reservation transactional in `PostgreSQL 16` next to the stock data preserves a single authoritative owner and atomic decrement.

## Consequences

Guaranteed no overselling at checkout with strong consistency on the reservation row, while catalog browsing remains eventually consistent and cacheable.

### Positive

- Overselling is structurally prevented at checkout: the atomic decrement-and-reserve transaction in `Inventory DB` serializes concurrent attempts on the same SKU.
- The strong-consistency cost is confined to the checkout critical section, so the read-heavy browse path via `Catalog Service`, `Catalog Cache`, and the `Managed Search Service` keeps its low-latency, cacheable, eventually-consistent profile.
- Reservations are owned, addressable resources that fit the orchestrated saga cleanly: `Order Service` can compensate and release stock deterministically when payment or another step fails.
- The reservation has a single authoritative owner (`Inventory Service` over `Inventory DB`), avoiding split-brain stock counts across caches or event streams.

### Negative

- `Inventory Service` and `Inventory DB` become a contention point for hot SKUs; row-level locking under heavy concurrency can serialize checkouts and add latency to the saga's reservation step.
- The synchronous `HTTPS/REST` call places `Inventory Service` directly on the checkout critical path, so its availability and latency now bound the order saga and must be protected by the `Resilience4j` timeouts, retries, and circuit breakers — adding the burden of idempotent, exactly-once reservation handling.
- Stranded reservations are a new failure mode: if a saga crashes after reserving but before compensating, held stock is unavailable until released. `Inventory Service` must own reservation expiry/timeout and reconciliation to reclaim abandoned holds.
- The advisory stock shown during browsing can diverge from reservable stock, so a customer may add an item to a cart and still be rejected at checkout — a UX gap the storefront must handle gracefully.