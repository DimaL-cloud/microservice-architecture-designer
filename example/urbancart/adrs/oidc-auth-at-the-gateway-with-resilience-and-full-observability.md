# adr-auth-resilience-observability: OIDC auth at the gateway with resilience and full observability

## Status

Accepted

## Context

UrbanCart must authenticate every request from `Web Consumer`, `Mobile Consumer`, and `Back-office Staff` using OAuth 2.0 / OIDC against the external `Identity Provider`, while meeting a 99.9% availability target with a 200–500ms p95 latency budget. These goals are in tension: the platform is a distributed system of nine `Kotlin 2 / Spring Boot 3` services on EKS, and a single user-facing flow — such as the order-placement saga in `Order Service` — chains synchronous REST calls through `Cart Service`, `Inventory Service`, and `Payment Service`. Without deliberate failure isolation, a slow or failing downstream dependency would consume threads, breach the latency budget, and erode availability.

Authentication is itself a cross-cutting concern. Validating OIDC tokens independently in each of the nine services would duplicate token-handling logic, multiply the round-trip cost to the `Identity Provider`, and produce inconsistent enforcement that is difficult to reason about during a security review. Because several services — `Customer / Identity Service`, `Order Service`, `Payment Service`, and `Fulfilment Service` — hold PII and participate in the GDPR erasure flow, inconsistent or unauditable access control is a direct compliance liability.

The brief also mandates first-class observability: metrics, structured logging, and distributed tracing across all services. Given that business operations span both synchronous chains and the asynchronous `Event Backbone` (`Amazon SNS + SQS`), the team needs to follow a single request — for example, a refund initiated by `Back-office Staff` that crosses `Order Service`, `Payment Service`, the `Payment Provider`, and `Notification Service` — end to end. Diagnosing a latency or correctness problem without correlated traces across these hops is not feasible at this scale.

These forces — uniform auth, SLA-grade resilience on synchronous calls, and auditable end-to-end traceability over a PII-handling distributed system — drive a single, coordinated decision about how authentication, failure handling, and telemetry are applied platform-wide.

## Decision

Authentication is enforced centrally at the `API Gateway` (`Amazon API Gateway / AWS ALB`). The gateway validates OIDC access tokens against the `Identity Provider` over HTTPS/OIDC before routing any request to an internal service, and propagates the verified identity claims downstream so that services receive trusted, pre-validated context rather than re-validating tokens themselves. This applies uniformly to every routed path — catalog, cart, order, payment, inventory, fulfilment, and customer.

Inter-service synchronous REST calls are hardened with `Resilience4j`. Every outbound call — notably the `Order Service` calls to `Cart Service`, `Inventory Service`, and `Payment Service` during the saga, and the `Cart Service` call to `Catalog Service` for pricing — is wrapped with explicit timeouts, retries with exponential backoff, and circuit breakers. This bounds the blast radius of a degraded dependency: a stalled downstream is failed fast and, where applicable, triggers saga compensation rather than holding the user request open past the p95 budget.

For observability, all services emit `OpenTelemetry` distributed traces, structured JSON logs, and `Prometheus` metrics to managed AWS observability backends. The `API Gateway` originates a trace context that is propagated through synchronous calls and carried across `Event Backbone` messages, so a single trace links the synchronous portion of a flow to its asynchronous consumers such as `Fulfilment Service` and `Notification Service`. Structured logs carry the propagated identity claims and correlation IDs, giving an auditable record of who accessed PII-bearing services.

In short:

- **Auth:** OIDC validation and claim propagation centralized at the `API Gateway`.
- **Resilience:** Timeouts, backoff retries, and circuit breakers via `Resilience4j` on all synchronous inter-service calls.
- **Observability:** `OpenTelemetry` traces, structured JSON logs, and `Prometheus` metrics across every service.

## Alternatives Considered

### Per-service OIDC token validation without a central gateway

Each service would validate OIDC tokens directly against the `Identity Provider`. Rejected because it duplicates token-handling logic across nine services, increases load and latency against the external provider, and — most importantly — produces inconsistent enforcement that is hard to audit. With PII spread across `Customer / Identity Service`, `Order Service`, `Payment Service`, and `Fulfilment Service`, the GDPR requirement for demonstrable, uniform access control makes inconsistent per-service validation an unacceptable compliance risk.

### Ad-hoc, per-service logging without distributed tracing

Each team would log in its own format with no shared trace context. Rejected because UrbanCart's business flows cross both synchronous REST chains and the asynchronous `Event Backbone`; without correlated traces, diagnosing a p95 breach or following a refund or erasure flow across services would require manual log correlation that does not scale and undermines the auditability the brief demands.

### No resilience layer (naive synchronous calls)

The `Order Service` and other callers would issue plain REST calls with default client behavior. Rejected because a single slow dependency — for example `Payment Service` waiting on the `Payment Provider` — would exhaust caller threads and cascade failure, making the 99.9% availability target and the 200–500ms p95 budget unattainable.

### Service mesh (e.g. Istio/AWS App Mesh) for mTLS, retries, and telemetry

A mesh could provide transport security, retries, and telemetry at the sidecar layer without application changes. A reasonable option, but heavier to operate for a team with only partial microservice experience, and it does not by itself satisfy OIDC token validation or application-level circuit-breaking with saga-aware fallbacks. The gateway-plus-`Resilience4j` approach keeps control in the application where compensation logic lives, at lower operational cost.

## Consequences

### Positive

- Authentication is enforced and audited in exactly one place — the `API Gateway` — giving consistent OIDC validation and a single point to reason about access to PII-handling services.
- Synchronous failure isolation via `Resilience4j` bounds the latency impact of any single degraded dependency, directly supporting the 99.9% availability and 200–500ms p95 targets and feeding clean failure signals into the order saga's compensation logic.
- End-to-end `OpenTelemetry` traces link synchronous flows to asynchronous `Event Backbone` consumers, so flows like order placement, refunds, and GDPR erasure can be followed across `Order Service`, `Payment Service`, `Fulfilment Service`, and `Notification Service`.
- Structured JSON logs carrying propagated claims and correlation IDs produce the auditable access trail required for GDPR over PII-bearing services.
- Standardized `Prometheus` metrics across all services enable consistent SLA alerting and capacity insight without per-team reinvention.

### Negative

- The `API Gateway` becomes a critical availability dependency and a potential single point of failure for all authenticated traffic; it must be highly available and itself observable.
- Downstream services trust gateway-propagated claims, so the internal network boundary must be protected — a request that bypasses the gateway would carry unvalidated identity, requiring controls to ensure services are only reachable through it.
- Every service now carries instrumentation overhead: `OpenTelemetry` SDK integration, structured logging conventions, and `Prometheus` exporters add code, configuration, and a small runtime cost that the team must maintain consistently.
- Correctly tuning `Resilience4j` timeouts, retry counts, and circuit-breaker thresholds per call is non-trivial; misconfiguration can amplify load through retries or prematurely trip breakers, and these settings must be revisited as traffic patterns evolve.
- Managed AWS observability backends introduce ongoing cost and data-retention considerations, and trace/log data derived from PII-handling flows must itself be governed under the same EU residency and GDPR constraints as the source data.