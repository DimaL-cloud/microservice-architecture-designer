# adr-sns-sqs-event-backbone: Use Amazon SNS + SQS as the asynchronous event backbone with idempotent consumers

## Status

Accepted

## Context

UrbanCart decomposes into nine bounded-context services that must collaborate without coupling their availability or deployment cadence to one another. While request/response paths use synchronous `HTTPS/REST` through the `API Gateway`, several workflows are inherently asynchronous and fan an event out to multiple independent consumers: order confirmation drives both the `Fulfilment Service` and the `Notification Service`, catalog changes drive the `Search Indexing Service`, the order saga emits payment and inventory acknowledgements back to the `Order Service`, and GDPR erasure requests fan out from the `Customer / Identity Service` to every PII-owning service. These flows need durable delivery, retries, and per-consumer isolation so that a slow or failing subscriber does not stall the publisher or its siblings.

The platform runs entirely on AWS in `eu-central-1` at a read-heavy, moderate scale (roughly 100–1k RPS) with a moderate operational budget and a team that has only some microservice experience. The dominant qualities in tension are operability and cost against ordering and replay guarantees. A log-based broker offers strong ordering and long retention but demands cluster sizing, partition management, and ongoing operational ownership that this team would have to absorb directly.

The fan-out shape of the workloads matters: a single domain event such as order-confirmed is consumed by several services that each progress at their own pace and must be able to fail and retry in isolation. The same `Event Backbone` also carries the saga acknowledgements the `Order Service` consumes and the erasure-completed acks the `Customer / Identity Service` tracks for audit, so the transport must support both broadcast and reliable point-to-point delivery with dead-lettering.

This decision concerns only the asynchronous transport. Synchronous saga coordination between the `Order Service`, `Inventory Service`, and `Payment Service` remains REST and is governed separately.

## Decision

UrbanCart uses Amazon SNS topics for publication and per-subscriber SQS queues for consumption as the `Event Backbone`. Publishers — including the `Order Service`, `Payment Service`, `Inventory Service`, `Catalog Service`, and `Customer / Identity Service` — emit domain events to SNS topics over the `AWS SNS API/HTTPS`. SNS fans each event out to dedicated SQS queues owned by each consuming service.

Each consumer reads from its own queue:

- The `Fulfilment Service` and `Notification Service` each consume order-confirmed and other domain events from their own queues, so one can lag or fail without affecting the other.
- The `Search Indexing Service` consumes catalog outbox events emitted by the `Catalog Service` and updates the `Managed Search Service`.
- The `Order Service` consumes payment and inventory saga acknowledgements from its queue to advance saga state.
- The `Order Service`, `Payment Service`, and `Fulfilment Service` consume erasure-request events, and the `Customer / Identity Service` consumes the erasure-completed acks.

Every consumer is built to be idempotent, keyed on an event or business identifier, because SNS+SQS provides at-least-once delivery and no global ordering. Each SQS queue is paired with a dead-letter queue so that messages exceeding the redrive limit are quarantined for inspection and replay rather than silently lost or retried forever. This combination gives durable delivery, per-consumer backpressure, and bounded retries without any broker for the team to operate.

## Alternatives Considered

### Apache Kafka on Amazon MSK

A log-based broker would provide strong per-partition ordering, long retention, and native replay of historical events. It is rejected because it is heavier and costlier than this workload justifies: at 100–1k RPS with a moderate budget and a team holding only partial microservice experience, MSK introduces broker sizing, partition and consumer-group management, and rebalancing operability that the SNS+SQS managed model removes entirely. The fan-out and retry needs here are satisfied by topics and queues without the operational weight of a log.

### Direct synchronous REST fan-out from publishers

Publishers could call each interested service synchronously instead of emitting events. This is rejected because it couples publisher availability to every consumer: an order-confirmed notification path failing in the `Notification Service` would otherwise be able to fail the publisher's request, and adding a new consumer would require changing the publisher. It also pushes retry and backpressure into the publisher, defeating the isolation the asynchronous flows require.

### A single shared SQS queue without SNS fan-out

A single queue consumed by multiple services would force competing consumption, where one service dequeuing a message starves the others, breaking the broadcast semantics that order-confirmed and erasure-request events require. SNS fan-out to per-service queues is necessary precisely so each consumer receives its own copy and manages its own retries and DLQ independently.

## Consequences

### Positive

- Fully managed pub/sub with no broker to size, patch, or operate, matching the team's moderate operational capacity and budget.
- Per-consumer SQS queues isolate failures and backpressure, so a lagging `Search Indexing Service` or `Notification Service` cannot block publishers or sibling consumers.
- Dead-letter queues bound retries and quarantine poison messages for inspection and controlled replay, improving operability of the fan-out flows.
- SNS fan-out naturally supports broadcast events such as order-confirmed and erasure-request reaching multiple owners simultaneously, and point-to-point saga acks back to the `Order Service`.

### Negative

- At-least-once delivery and the absence of global ordering require every consumer — `Fulfilment Service`, `Notification Service`, `Search Indexing Service`, `Order Service`, and the erasure handlers — to implement and test idempotency keyed on a stable identifier; this is non-trivial, recurring work the team now owns on every consumer.
- SQS lacks the long retention and arbitrary replay of a log-based broker, so reprocessing historical events (for example, rebuilding the `Managed Search Service` index from scratch) is not natively supported and must be solved out of band.
- Messages exceeding redrive limits accumulate in DLQs that the team must monitor and manually triage, adding an operational surface area and alerting requirement.
- Ordering-sensitive flows must be designed to tolerate out-of-order and duplicate delivery rather than relying on the transport, which constrains how event-driven state machines like the saga acknowledgement handling can be implemented.