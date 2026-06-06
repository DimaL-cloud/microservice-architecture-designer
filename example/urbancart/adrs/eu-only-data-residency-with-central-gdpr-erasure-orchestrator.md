# ADR: EU-only data residency with central GDPR erasure orchestrator

## Status

Accepted

## Context

UrbanCart is an EU-only marketplace that stores personal data across several bounded contexts. The `Customer / Identity Service` holds profiles and PII in the `Customer DB`, but personal data does not stop there: the `Order Service` retains order aggregates in the `Order DB`, the `Payment Service` keeps payment and refund records in the `Payment DB`, and the `Fulfilment Service` stores shipment details (names, addresses) in the `Fulfilment DB`. GDPR's right-to-erasure therefore cannot be satisfied by a single delete in one database — it requires every PII-owning service to act on its own data while a single party can prove the request was fully honored.

Two forces dominate. First, **data residency**: GDPR and the brief constrain personal data to remain within the EU, and the system explicitly delegates geographic distribution rather than spreading data across regions. Second, **auditability of erasure completion**: a regulator-facing erasure must be demonstrably complete across a distributed system whose data ownership is deliberately fragmented across nine services (per the bounded-context decomposition). Without a coordinating mechanism, proving that every owner deleted or anonymized its share becomes an untracked, error-prone manual exercise.

These forces sit in tension with availability. Keeping all data in a single region simplifies residency and audit but caps the platform's resilience to a single AWS region's fate. The asynchronous nature of erasure across many services also conflicts with the desire for a simple, synchronous "delete now" semantic — erasure inherently becomes a multi-step, eventually-consistent workflow.

The system already operates the `Event Backbone` (`Amazon SNS + SQS`) as its asynchronous fan-out mechanism, with per-service queues, retries, and dead-letter queues. This gives the team a durable, auditable transport that maps naturally onto a one-request-to-many-handlers erasure pattern.

## Decision

All UrbanCart datastores — `Customer DB`, `Order DB`, `Payment DB`, `Inventory DB`, `Fulfilment DB`, `Catalog DB`, the `Cart Store`, and the `Catalog Cache` — run in AWS `eu-central-1` with no cross-region replication, guaranteeing personal data never leaves the EU.

Right-to-erasure is coordinated by the `Customer / Identity Service` acting as a central erasure orchestrator:

- A `Web Consumer` submits an erasure request through the `API Gateway`, which routes it to the `Customer / Identity Service`. That service records the request and publishes an erasure-request event to the `Event Backbone` over SNS.
- The PII-owning services — `Order Service`, `Payment Service`, and `Fulfilment Service` — each consume the event from their own SQS queue and delete or anonymize the personal data they own in their respective databases.
- Each handler publishes an erasure-completed acknowledgement back to the `Event Backbone`. The `Customer / Identity Service` consumes these acknowledgements, tracks completion across all expected handlers, finalizes deletion of its own profile in the `Customer DB`, and marks the overall erasure complete for audit.

This makes erasure a tracked saga of its own: one authoritative coordinator, durable event delivery, and an explicit acknowledgement per data owner. Idempotent consumers and dead-letter queues — already mandated for the `Event Backbone` — apply directly, so a redelivered erasure event causes no harm and a stuck handler surfaces in its DLQ rather than silently dropping an obligation.

## Alternatives Considered

### Multi-region data distribution

Replicating datastores across multiple AWS regions for higher availability and proximity. Rejected because it complicates GDPR residency guarantees (data would cross or be copied between regions, requiring per-region controls) and the brief explicitly delegates geographic distribution. For an EU-only platform, single-region `eu-central-1` keeps residency trivially provable and avoids cross-region replication lag and cost.

### Ad-hoc per-service deletion endpoints

Exposing a synchronous delete endpoint on each PII-owning service that the `Customer / Identity Service` (or an operator) calls directly over REST. Rejected because completion tracking becomes the caller's burden across many synchronous calls, partial failures leave the system in an unknown state with no durable retry, and there is no central, auditable record that every owner finished. Synchronous fan-out also couples erasure availability to every downstream service being up at request time, whereas the `Event Backbone` absorbs transient outages and retries.

### Choreographed erasure with no central tracker

Letting each service react to an erasure event independently with no coordinator confirming overall completion. Rejected because GDPR requires demonstrable, end-to-end completion. Without the `Customer / Identity Service` collecting acknowledgements, the team could not answer "is this person's data fully erased?" with confidence, and stalled handlers would go unnoticed.

## Consequences

### Positive

- Data residency is trivially provable: with all stores in `eu-central-1` and no cross-region replication, personal data demonstrably never leaves the EU.
- Erasure completion is auditable end to end. The `Customer / Identity Service` holds a single authoritative record of which services acknowledged, satisfying regulator-facing evidence requirements.
- The pattern reuses the existing `Event Backbone` (`SNS + SQS`) with its per-service queues, idempotent-consumer requirement, retries, and DLQs, so a transient service outage delays rather than drops an erasure obligation.
- Each PII owner deletes or anonymizes only its own data, preserving the bounded-context ownership model — no service reaches into another's database to erase data.

### Negative

- Every PII-owning service (`Order Service`, `Payment Service`, `Fulfilment Service`, plus the `Customer / Identity Service` itself) must build and maintain an erasure handler and keep it correct as its schema evolves — ongoing, distributed work that is easy to let drift out of sync with what PII a service actually stores.
- Erasure is eventually consistent and asynchronous, not instantaneous. The orchestrator must define and enforce completion SLAs and escalate handlers whose acknowledgements never arrive (e.g., messages landing in a DLQ).
- Single-region residency caps availability: a regional outage in `eu-central-1` takes the platform down with no cross-region failover, a direct trade against the 99.9% availability target that must be managed through in-region redundancy and backups.
- Anonymization-versus-deletion semantics must be decided per service — e.g., the `Payment Service` and `Order Service` may need to retain financial records for legal obligations while stripping identifying PII — adding policy complexity to each handler.
- Adding any new PII-owning service in future requires extending the erasure fan-out and the orchestrator's expected-acknowledgement set, or erasure silently becomes incomplete.