# adr-bounded-context-decomposition: Decompose into nine launch bounded-context services

## Status

Accepted

## Context

UrbanCart is a greenfield EU-only marketplace targeting 100k–1M users, serving both `Web Consumer` and `Mobile Consumer` browse/order traffic alongside `Back-office Staff` inventory, fulfilment, and refund workflows. The brief explicitly enumerates the contexts the platform must support: catalog, cart, order, payment, inventory, fulfilment, customer/identity, notification, and search indexing. These are not arbitrary modules — each maps to a distinct business capability with its own data ownership, scaling curve, and compliance obligations.

The pressure that forces a decomposition decision is the divergence in non-functional profiles across these contexts. Catalog browsing is read-heavy and latency-sensitive (served via `Catalog Cache` on `Redis 7` and delegated full-text to the `Managed Search Service`), while checkout coordinated by the `Order Service` is a strongly-consistent, multi-step transactional flow involving the `Inventory Service` and `Payment Service`. Payment carries PCI-DSS scope considerations, and the `Customer / Identity Service` plus several other services own PII subject to GDPR right-to-erasure. A single deployable unit would force all of these into one scaling, release, and risk envelope.

The contexts also differ in their datastore needs: the `Cart Store` runs on `Amazon DynamoDB` for high-volume document-shaped cart writes, whereas order, payment, inventory, fulfilment, customer, and catalog data live in separate `PostgreSQL 16` (Amazon RDS) instances per service. Co-locating these in one process would couple unrelated persistence concerns and prevent each team from tuning its own store.

The qualities in tension are independent scalability and clear ownership against operational simplicity. The team has only some microservice experience, so any decomposition must be matched by investment in observability and operational tooling to remain tractable.

## Decision

Each of the nine named bounded contexts is built as its own separately deployable service — `Catalog Service`, `Cart Service`, `Order Service`, `Payment Service`, `Inventory Service`, `Fulfilment Service`, `Customer / Identity Service`, `Notification Service`, and `Search Indexing Service` — all running as `Kotlin 2 / Spring Boot 3` workloads on AWS EKS. Each service owns its own datastore exclusively: the `Catalog Service` owns the `Catalog DB`, the `Order Service` owns the `Order DB`, the `Inventory Service` owns the `Inventory DB`, and so on, with no shared database between contexts.

A single `API Gateway` (`Amazon API Gateway / AWS ALB`) fronts all externally reachable services, routing `HTTPS/REST` traffic from the `Web Consumer`, `Mobile Consumer`, and `Back-office Staff` to the appropriate service and enforcing a consistent ingress boundary. Synchronous request/response interactions between services — for example the `Order Service` reading cart contents from the `Cart Service`, reserving stock via the `Inventory Service`, and initiating payment via the `Payment Service` — use REST. Asynchronous fan-out across contexts (fulfilment, notification, search indexing, saga acknowledgements, erasure) flows over the `Event Backbone` on `Amazon SNS + SQS`.

This structure lets each context scale, deploy, and fail independently. The read-heavy `Catalog Service` can scale horizontally and lean on its `Catalog Cache` without affecting the transactional `Order Service`; the `Notification Service` and `Search Indexing Service` consume events at their own pace from their own SQS queues without back-pressuring publishers.

## Alternatives Considered

### Modular monolith

A single deployable application with internal module boundaries for each context. Rejected because the team wants independent scaling and the contexts have distinct read/write and compliance profiles — a cacheable, read-heavy catalog versus a strongly-consistent order saga versus event-driven notification — and a monolith forces them to share one scaling unit, one release cadence, and one failure domain. It would also blur the per-service data ownership (separate `PostgreSQL 16` instances plus a `DynamoDB`-backed `Cart Store`) that the team wants in order to tune persistence per context, and would complicate isolating PCI and GDPR concerns to the services that actually carry them.

### Coarser-grained service grouping (fewer than nine services)

Merging related contexts — for example combining `Order Service`, `Payment Service`, and `Inventory Service` into one "checkout" service. Rejected because it recombines exactly the boundaries the orchestrated order saga relies on: the `Order Service` must coordinate the `Inventory Service` and `Payment Service` as distinct, independently compensatable participants, and the `Payment Service` benefits from being the only component that talks to the `Payment Provider` to keep PCI scope narrow. Merging them would re-couple compliance and scaling concerns that the brief deliberately separates.

### Function-as-a-service / serverless functions per capability

Implementing each capability as Lambda functions rather than long-running EKS services. Rejected because the platform relies on stateful, long-lived saga coordination in the `Order Service`, persistent JDBC connections to RDS, and steady SQS consumers in the `Fulfilment Service`, `Notification Service`, and `Search Indexing Service`. A consistent `Kotlin 2 / Spring Boot 3` on EKS runtime gives the team one operational model and connection-management story rather than fragmenting it across a function platform.

## Consequences

### Positive

- Each bounded context has clear single-team ownership of both its code and its datastore (e.g. the `Payment Service` owns `Payment DB`, the `Customer / Identity Service` owns `Customer DB`), preventing cross-context schema coupling.
- Services scale independently to their own load profile — the read-heavy `Catalog Service` and its `Catalog Cache` scale separately from the transactional `Order Service`.
- Compliance concerns are localized: PCI scope concentrates in the `Payment Service` that talks to the `Payment Provider`, and GDPR erasure handlers live in the specific PII-owning services coordinated by the `Customer / Identity Service`.
- Independent deployment lets teams release a fix to the `Notification Service` or `Search Indexing Service` without redeploying checkout, reducing blast radius per change.
- A single `API Gateway` provides one consistent ingress point for OIDC validation, routing, and rate limiting across all consumer-facing services.

### Negative

- The platform inherits full distributed-system complexity — partial failures, network timeouts, and cross-service consistency — which a team with only some microservice experience must actively manage with strong observability. This makes distributed tracing, structured logging, and metrics a prerequisite rather than an optional add-on.
- Flows that were a single in-process call in a monolith now span network boundaries: the order saga involves synchronous REST hops to the `Cart Service`, `Inventory Service`, and `Payment Service`, each needing timeouts, retries, and circuit breakers to meet the latency and availability targets.
- Nine services plus their datastores, SNS topics, and SQS queues multiply the operational surface — deployments, dashboards, alerting, and on-call runbooks must exist for each context.
- Cross-context data flows must rely on the `Event Backbone` and per-service queues for eventual consistency (fulfilment, search indexing, erasure), introducing eventual-consistency windows and the need for idempotent consumers that a single shared database would not require.
- Operating EKS, RDS-per-service, `DynamoDB`, `Redis 7`, and SNS+SQS together raises baseline infrastructure cost and demands platform/IaC discipline the team must build up front.