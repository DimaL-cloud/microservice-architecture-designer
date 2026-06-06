# UrbanCart — System Design Document

## 1. Overview & Purpose

UrbanCart is a greenfield, EU-only online marketplace where web and mobile consumers browse a catalog, build carts, place orders, and pay online, while back-office staff manage inventory, fulfilment, and refunds. The platform is decomposed into bounded-context microservices running on AWS EKS behind a single API gateway, combining synchronous REST for request/response with SNS+SQS for asynchronous domain events. Order placement is coordinated by an orchestrated saga, payment is provider-hosted under PCI SAQ A scope, and the system honors GDPR through a central erasure orchestrator with all data resident in eu-central-1.

The system serves three classes of people — the **Web Consumer**, the **Mobile Consumer**, and **Back-office Staff** — and integrates with five external systems: the **Payment Provider**, the **Identity Provider**, the **Managed Search Service**, the **Email/SMS Provider**, and the **Analytics Provider**.

## 2. Architecture Overview

UrbanCart follows a microservices architecture: nine bounded-context services run on Amazon EKS behind a single **API Gateway** (Amazon API Gateway / AWS ALB), which is the sole ingress point and the OIDC authentication boundary. Latency-sensitive, request/response traffic uses synchronous HTTPS/REST between the gateway and services and between services (for example, **Order Service** to **Inventory Service** and **Payment Service**). Asynchronous fan-out flows through the **Event Backbone** (Amazon SNS + SQS), which delivers domain events from publishers to per-service subscriber queues with retries and dead-letter handling.

State is distributed: each service owns its own datastore (per-context PostgreSQL on Amazon RDS, plus DynamoDB for carts), with a Redis cache for hot catalog reads. Order placement is an **orchestrated saga** centered on **Order Service**, strong inventory reservation prevents overselling at checkout, and a central erasure orchestrator in **Customer / Identity Service** coordinates GDPR right-to-erasure. All datastores reside in AWS eu-central-1 with no cross-region replication.

### Actors

| Name | Type | Description |
| --- | --- | --- |
| Web Consumer | Person | End customer browsing and ordering through the web storefront. |
| Mobile Consumer | Person | End customer browsing and ordering through the mobile app. |
| Back-office Staff | Person | Internal employee managing inventory, fulfilment, and approving refunds. |
| Payment Provider | External system | Provider-hosted payment service (Stripe/Adyen) handling card data via hosted fields under SAQ A. |
| Identity Provider | External system | External OAuth 2.0 / OIDC provider issuing and validating access tokens. |
| Managed Search Service | External system | Hosted full-text search backend (Algolia / Elastic Cloud) serving catalog search queries. |
| Email/SMS Provider | External system | Third-party email and SMS provider (SendGrid/Twilio) for transactional notifications and push. |
| Analytics Provider | External system | Product telemetry and analytics platform receiving usage events. |

## 3. Containers & Responsibilities

### API Gateway

- **Kind / Technology:** Gateway — Amazon API Gateway / AWS ALB.
- Routes external REST traffic to internal services and enforces OIDC token validation and rate limiting. It is the single ingress for **Web Consumer**, **Mobile Consumer**, and **Back-office Staff**, validates access tokens against the **Identity Provider** over HTTPS/OIDC, and routes to **Catalog Service**, **Cart Service**, **Order Service**, **Payment Service**, **Inventory Service**, **Fulfilment Service**, and **Customer / Identity Service** over HTTPS/REST.

### Catalog Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Manages product catalog data and emits catalog change outbox events. It reads/writes **Catalog DB** over JDBC, caches hot reads in **Catalog Cache** over the Redis protocol, publishes outbox events to the **Event Backbone** over SNS, delegates full-text queries to the **Managed Search Service** over HTTPS/REST, and emits product telemetry to the **Analytics Provider**. It also serves product prices to **Cart Service**.

### Cart Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Maintains customer shopping carts and computes cart totals. It reads/writes cart documents in the **Cart Store** over the DynamoDB API, fetches product prices from **Catalog Service** over HTTPS/REST, and exposes cart contents to **Order Service**.

### Order Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Orchestrates the order-placement saga and owns the order lifecycle state. It persists orders and saga state in **Order DB** over JDBC, reads cart contents from **Cart Service**, reserves stock via **Inventory Service**, initiates payment via **Payment Service**, publishes order events to the **Event Backbone** over SNS, and consumes payment and inventory saga events from its SQS queue.

### Payment Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Coordinates provider-hosted payment intents, captures, and refunds without touching card data. It persists payment and refund records in **Payment DB** over JDBC, creates payment intents and refunds with the **Payment Provider** over HTTPS/REST, and publishes payment events to the **Event Backbone** over SNS.

### Inventory Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Tracks stock levels and performs strong reservations at checkout to prevent overselling. It reads/writes stock and reservations in **Inventory DB** over JDBC and publishes inventory events to the **Event Backbone** over SNS. It is called synchronously by **Order Service** during the saga.

### Fulfilment Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Manages shipment and fulfilment workflows for confirmed orders. It persists shipment records in **Fulfilment DB** over JDBC, and consumes order events from and publishes fulfilment events to the **Event Backbone** over SQS.

### Customer / Identity Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Manages customer profiles and coordinates GDPR right-to-erasure across services. It reads/writes profiles in **Customer DB** over JDBC, and publishes erasure-request events to and consumes acknowledgements from the **Event Backbone** over SNS/SQS.

### Notification Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Consumes domain events and sends email, SMS, and push notifications. It consumes from the **Event Backbone** over SQS and sends messages via the **Email/SMS Provider** over HTTPS/REST.

### Search Indexing Service

- **Kind / Technology:** Service — Kotlin 2 / Spring Boot 3 (EKS).
- Consumes catalog outbox events and keeps the managed search index in sync. It consumes catalog outbox events from the **Event Backbone** over SQS and updates the index in the **Managed Search Service** over HTTPS/REST.

### Catalog DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores product and pricing data plus the catalog outbox, owned by **Catalog Service**.

### Cart Store

- **Kind / Technology:** Database — Amazon DynamoDB (eu-central-1).
- Stores customer cart documents, owned by **Cart Service**.

### Order DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores order aggregates and saga state, owned by **Order Service**.

### Payment DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores payment intent and refund records, owned by **Payment Service**.

### Inventory DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores stock levels and reservations, owned by **Inventory Service**.

### Fulfilment DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores shipment and fulfilment records, owned by **Fulfilment Service**.

### Customer DB

- **Kind / Technology:** Database — PostgreSQL 16 (Amazon RDS, eu-central-1).
- Stores customer profiles and PII, owned by **Customer / Identity Service**.

### Catalog Cache

- **Kind / Technology:** Cache — Redis 7 (Amazon ElastiCache, eu-central-1).
- Caches hot catalog reads to meet the read-heavy latency budget; used by **Catalog Service**.

### Event Backbone

- **Kind / Technology:** Queue — Amazon SNS + SQS (eu-central-1).
- Carries asynchronous domain events between publishers and per-service subscriber queues, fanning SNS topics out to dedicated SQS queues per consumer.

## 4. Data Management

Each bounded context owns a private datastore, enforcing the database-per-service pattern; no service reads another service's store directly — cross-context data is obtained via REST or via events.

- **Catalog DB (PostgreSQL 16)** is owned by **Catalog Service**, persisting product and pricing data plus a transactional **outbox** that drives catalog change events. Writes are strongly consistent within the service; downstream search indexing is eventually consistent.
- **Cart Store (DynamoDB)** is owned by **Cart Service**, persisting cart documents with the single-digit-millisecond, high-throughput access profile suited to frequently mutated cart state.
- **Order DB (PostgreSQL 16)** is owned by **Order Service**, persisting order aggregates and saga state. Strong, transactional consistency anchors the orchestrated saga and its compensation bookkeeping.
- **Payment DB (PostgreSQL 16)** is owned by **Payment Service**, persisting payment intent and refund records. No card data is stored — only provider references — keeping the store within SAQ A scope.
- **Inventory DB (PostgreSQL 16)** is owned by **Inventory Service**, persisting stock levels and reservations. Reservation rows are updated transactionally and strongly consistently to guarantee no overselling at checkout.
- **Fulfilment DB (PostgreSQL 16)** is owned by **Fulfilment Service**, persisting shipment and fulfilment records created in response to order-confirmed events.
- **Customer DB (PostgreSQL 16)** is owned by **Customer / Identity Service**, persisting customer profiles and PII; it is the primary target of GDPR erasure.
- **Catalog Cache (Redis 7)** is owned by **Catalog Service** and holds hot catalog reads to satisfy the read-heavy latency budget. It is a read-through/look-aside cache and is best-effort consistent with Catalog DB.

Overall, individual datastores are strongly consistent within their owning service, while cross-service state propagated over the **Event Backbone** is **eventually consistent**. All stores run in eu-central-1 with no cross-region replication to satisfy EU data residency.

## 5. Communication & Integration

UrbanCart mixes synchronous request/response with asynchronous pub/sub. Synchronous HTTPS/REST handles the order-capture critical path and inter-service calls — most notably **Order Service** calling **Cart Service**, **Inventory Service**, and **Payment Service**, and **Payment Service** calling the **Payment Provider**. JDBC connections to the PostgreSQL stores and the DynamoDB API for the **Cart Store** are also synchronous. The **API Gateway** validates OIDC tokens against the **Identity Provider** synchronously per request.

Asynchronous integration flows through the **Event Backbone** (SNS publish, SQS consume). Publishers — **Catalog Service**, **Order Service**, **Payment Service**, **Inventory Service**, **Fulfilment Service**, and **Customer / Identity Service** — emit domain events to SNS topics, which fan out to per-service SQS queues consumed by **Notification Service**, **Fulfilment Service**, **Search Indexing Service**, **Order Service** (saga acks), and the erasure handlers. Consumers are idempotent with dead-letter queues for retries.

| From | To | Interaction | Protocol |
| --- | --- | --- | --- |
| Web Consumer | API Gateway | Browses, carts, and orders via | HTTPS/REST (sync) |
| Mobile Consumer | API Gateway | Browses, carts, and orders via | HTTPS/REST (sync) |
| Back-office Staff | API Gateway | Manages inventory, fulfilment, and refunds via | HTTPS/REST (sync) |
| API Gateway | Identity Provider | Validates access tokens against | HTTPS/OIDC (sync) |
| API Gateway | Catalog Service | Routes catalog requests to | HTTPS/REST (sync) |
| API Gateway | Cart Service | Routes cart requests to | HTTPS/REST (sync) |
| API Gateway | Order Service | Routes order requests to | HTTPS/REST (sync) |
| API Gateway | Payment Service | Routes payment requests to | HTTPS/REST (sync) |
| API Gateway | Inventory Service | Routes inventory requests to | HTTPS/REST (sync) |
| API Gateway | Fulfilment Service | Routes fulfilment requests to | HTTPS/REST (sync) |
| API Gateway | Customer / Identity Service | Routes customer requests to | HTTPS/REST (sync) |
| Catalog Service | Catalog DB | Reads and writes catalog data in | JDBC (sync) |
| Catalog Service | Catalog Cache | Caches catalog reads in | Redis protocol (sync) |
| Catalog Service | Event Backbone | Publishes catalog outbox events to | AWS SNS API/HTTPS (async) |
| Cart Service | Cart Store | Reads and writes carts in | DynamoDB API/HTTPS (sync) |
| Cart Service | Catalog Service | Fetches product prices from | HTTPS/REST (sync) |
| Order Service | Order DB | Persists orders and saga state in | JDBC (sync) |
| Order Service | Inventory Service | Reserves stock via | HTTPS/REST (sync) |
| Order Service | Payment Service | Initiates payment via | HTTPS/REST (sync) |
| Order Service | Cart Service | Reads cart contents from | HTTPS/REST (sync) |
| Order Service | Event Backbone | Publishes order events to | AWS SNS API/HTTPS (async) |
| Order Service | Event Backbone | Consumes payment and inventory saga events from | AWS SQS API/HTTPS (async) |
| Payment Service | Payment DB | Persists payment records in | JDBC (sync) |
| Payment Service | Payment Provider | Creates payment intents and refunds via | HTTPS/REST (sync) |
| Payment Service | Event Backbone | Publishes payment events to | AWS SNS API/HTTPS (async) |
| Inventory Service | Inventory DB | Reads and writes stock and reservations in | JDBC (sync) |
| Inventory Service | Event Backbone | Publishes inventory events to | AWS SNS API/HTTPS (async) |
| Fulfilment Service | Fulfilment DB | Persists shipment records in | JDBC (sync) |
| Fulfilment Service | Event Backbone | Consumes order events from and publishes fulfilment events to | AWS SQS API/HTTPS (async) |
| Customer / Identity Service | Customer DB | Reads and writes customer profiles in | JDBC (sync) |
| Customer / Identity Service | Event Backbone | Publishes erasure-request events to and consumes acks from | AWS SNS/SQS API/HTTPS (async) |
| Notification Service | Event Backbone | Consumes domain events from | AWS SQS API/HTTPS (async) |
| Notification Service | Email/SMS Provider | Sends email, SMS, and push via | HTTPS/REST (sync) |
| Search Indexing Service | Event Backbone | Consumes catalog outbox events from | AWS SQS API/HTTPS (async) |
| Search Indexing Service | Managed Search Service | Updates the search index in | HTTPS/REST (sync) |
| Catalog Service | Managed Search Service | Delegates full-text search queries to | HTTPS/REST (sync) |
| Catalog Service | Analytics Provider | Emits product telemetry to | HTTPS/REST (sync) |

## 6. Key Flows

### Consumer places and pays for an order via orchestrated saga

**Participants:** Web Consumer, API Gateway, Order Service, Cart Service, Inventory Service, Payment Service, Payment Provider, Order DB, Event Backbone.

1. Web Consumer submits checkout through the API Gateway over HTTPS/REST after token validation.
2. API Gateway routes the request to Order Service, which starts a saga and persists pending order state in Order DB.
3. Order Service reads the cart contents from Cart Service.
4. Order Service requests a strong stock reservation from Inventory Service.
5. Order Service initiates a payment intent via Payment Service, which creates it with the Payment Provider using provider-hosted fields.
6. On confirmed payment, Order Service marks the order confirmed and publishes an order-confirmed event to the Event Backbone; on failure it compensates the reservation and cancels.
7. API Gateway returns the order confirmation to the Web Consumer.

### Confirmed order triggers fulfilment and notification

**Participants:** Event Backbone, Fulfilment Service, Notification Service, Email/SMS Provider, Fulfilment DB.

1. Fulfilment Service consumes the order-confirmed event from its SQS queue on the Event Backbone.
2. Fulfilment Service creates a shipment record in Fulfilment DB and begins the fulfilment workflow.
3. Notification Service consumes the same event from its own queue.
4. Notification Service sends an order-confirmation email/SMS/push via the Email/SMS Provider.
5. Both services acknowledge their messages to commit consumption.

### Consumer browses and searches the catalog

**Participants:** Mobile Consumer, API Gateway, Catalog Service, Catalog Cache, Catalog DB, Managed Search Service, Search Indexing Service, Event Backbone.

1. Catalog Service publishes catalog change outbox events to the Event Backbone whenever products change.
2. Search Indexing Service consumes those events and updates the Managed Search Service.
3. Mobile Consumer requests product listings or search through the API Gateway.
4. API Gateway routes the request to Catalog Service, which serves browse reads from Catalog Cache on a hit or Catalog DB on a miss.
5. For full-text queries, Catalog Service delegates to the Managed Search Service and returns ranked results.
6. API Gateway returns the catalog or search results to the Mobile Consumer.

### Back-office staff issues an approved refund

**Participants:** Back-office Staff, API Gateway, Order Service, Payment Service, Payment Provider, Payment DB, Event Backbone, Notification Service.

1. Back-office Staff requests a refund for an order through the API Gateway.
2. API Gateway routes the request to Order Service, which records the refund as pending single-approver authorization.
3. Once approved, Order Service instructs Payment Service to process the refund.
4. Payment Service issues the refund through the Payment Provider and records it in Payment DB.
5. Order Service publishes an order-refunded event to the Event Backbone, which Notification Service consumes to notify the customer.

### Customer requests GDPR right-to-erasure

**Participants:** Web Consumer, API Gateway, Customer / Identity Service, Event Backbone, Order Service, Payment Service, Fulfilment Service.

1. Web Consumer submits an erasure request through the API Gateway.
2. API Gateway routes it to Customer / Identity Service, which records the request and publishes an erasure-request event to the Event Backbone.
3. Order Service, Payment Service, and Fulfilment Service each consume the event from their queues and delete or anonymize their owned PII.
4. Each per-service handler publishes an erasure-completed acknowledgement back to the Event Backbone.
5. Customer / Identity Service tracks all acknowledgements, finalizes its own profile deletion, and marks the erasure complete for audit.

## 7. Cross-Cutting Concerns

### Security & Authentication

The **API Gateway** is the authentication boundary: it validates OAuth 2.0 / OIDC access tokens against the **Identity Provider** and propagates claims to downstream services, while enforcing rate limiting on ingress. All external traffic uses HTTPS/REST. Payment security is handled by keeping card data entirely out of scope — the **Payment Provider** captures cards directly via provider-hosted fields, leaving **Payment Service** and **Payment DB** at PCI SAQ A scope with only provider references stored. GDPR-sensitive PII is concentrated in **Customer DB** (and minimized elsewhere), simplifying audit.

### Observability

Per the observability decision, all services emit OpenTelemetry distributed traces, structured JSON logs, and Prometheus metrics to managed AWS observability backends. Trace context is initiated at the **API Gateway** and propagated through synchronous REST calls and across asynchronous hops over the **Event Backbone**, so a single order or erasure request can be followed end to end across the saga and its event-driven follow-on flows — important given PII handling and the distributed, multi-service topology.

### Scalability & Availability

Services are stateless Kotlin/Spring Boot workloads on EKS and scale horizontally behind the **API Gateway**. The read-heavy browse path is absorbed by **Catalog Cache** (Redis) and offloaded to the **Managed Search Service** for full-text queries, protecting **Catalog DB**. The **Event Backbone** (SNS + SQS) smooths bursts and decouples fulfilment, notification, search indexing, and erasure from request-path latency, with dead-letter queues for retries. Synchronous inter-service calls are protected with timeouts, retries with backoff, and circuit breakers (Resilience4j) to meet the 99.9% availability and 200–500ms p95 targets. **Order Service** is a coordination hotspot for the saga and must be made idempotent and resilient; strong inventory reservations are transactionally consistent in **Inventory DB**. Single-region eu-central-1 residency caps availability at the region level by design.

### Deployment

All containers deploy to Amazon EKS in eu-central-1, with the **API Gateway** (Amazon API Gateway / AWS ALB) as the only externally exposed endpoint; services and datastores run inside the trust boundary. Datastores are managed AWS services — PostgreSQL 16 on Amazon RDS, DynamoDB for carts, Redis 7 on Amazon ElastiCache, and SNS + SQS for the event backbone — all pinned to eu-central-1 with no cross-region replication to enforce EU data residency.

## 8. Architecture Decisions

The decisions below are summarized; full ADRs are maintained separately.

| ID | Title | Decision |
| --- | --- | --- |
| adr-bounded-context-decomposition | Decompose into nine launch bounded-context services | Build each named bounded context as its own EKS-deployed Kotlin/Spring Boot service fronted by a single API Gateway. |
| adr-orchestrated-order-saga | Coordinate order placement with an orchestrated saga in Order Service | Order Service acts as saga orchestrator, calling Inventory and Payment synchronously over REST and persisting saga state in Order DB with compensations on failure. |
| adr-strong-inventory-reservation | Strong stock reservation at checkout in Inventory Service | Inventory Service performs synchronous, transactionally strong reservations during the saga and releases them via compensation on failure. |
| adr-pci-provider-hosted | Keep card data out of scope via provider-hosted payment fields (SAQ A) | Payment Service only creates and confirms payment intents with the Payment Provider; cards are captured by hosted fields, keeping UrbanCart at SAQ A. |
| adr-sns-sqs-event-backbone | Use Amazon SNS + SQS as the asynchronous event backbone with idempotent consumers | Publishers emit events to SNS topics fanned out to per-service SQS queues, with idempotent consumers and dead-letter queues for retries. |
| adr-auth-resilience-observability | OIDC auth at the gateway with resilience and full observability | API Gateway validates OIDC tokens; inter-service calls use timeouts, retries, and circuit breakers; all services emit OpenTelemetry traces, JSON logs, and Prometheus metrics. |
| adr-gdpr-erasure-residency | EU-only data residency with central GDPR erasure orchestrator | All datastores run in eu-central-1 with no cross-region replication; Customer / Identity Service orchestrates erasure via SNS/SQS handlers that delete or anonymize owned PII and acknowledge completion. |