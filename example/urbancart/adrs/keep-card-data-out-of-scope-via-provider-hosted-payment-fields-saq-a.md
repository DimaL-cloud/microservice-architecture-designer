# adr-pci-provider-hosted: Keep card data out of scope via provider-hosted payment fields (SAQ A)

## Status

Accepted

## Context

UrbanCart accepts online card payments from `Web Consumer` and `Mobile Consumer` actors as part of the orchestrated checkout flow. Any system that stores, processes, or transmits cardholder data falls under PCI-DSS, and the depth of that obligation scales sharply with how directly the system touches a primary account number. For a greenfield EU-only marketplace at 100k–1M users with a moderate budget, taking on full PCI-DSS scope would impose a recurring compliance, audit, and infrastructure cost out of proportion to the team's size and risk appetite.

The qualities in tension are compliance scope, operational cost, and architectural simplicity against control over the payment user experience and dependency on a third party. If raw card data flowed through the `Payment Service` or any UrbanCart-controlled component, the platform would need a segmented cardholder data environment (CDE), with isolated network zones, hardened key management, and a substantially larger SAQ — a heavy burden the brief explicitly wants to avoid by stating no network segmentation is needed.

The components under pressure are the `Payment Service` and its boundary with the external `Payment Provider` (Stripe/Adyen). In the place-order saga, `Order Service` initiates a payment intent through the `Payment Service`, which in turn talks to the `Payment Provider`. The decision is about where the card data actually lands during that exchange and, consequently, what compliance scope the rest of UrbanCart inherits — including the `Payment DB`, the `Order Service`, and the shared AWS EKS environment.

The brief mandates provider-hosted fields/redirect so that no card data ever touches UrbanCart. This pins the architecture to SAQ A, the lightest PCI-DSS self-assessment tier, available precisely to merchants who fully outsource cardholder data handling.

## Decision

UrbanCart captures card data exclusively through the `Payment Provider`'s hosted fields, never through any UrbanCart-controlled surface. The `Payment Service` only creates, confirms, and refunds payment intents against the `Payment Provider` over `HTTPS/REST`; it persists payment intent and refund records in `Payment DB`, but those records hold provider references and intent metadata, not raw card numbers.

Concretely, when `Order Service` initiates payment during the order-placement saga, the `Payment Service` requests a payment intent from the `Payment Provider` and returns the client-side artifacts the consumer's browser or app needs to render the provider's hosted fields. The card details are submitted directly from the `Web Consumer` or `Mobile Consumer` device to the `Payment Provider`, bypassing the `API Gateway`, the `Payment Service`, and every other UrbanCart container. The `Payment Service` then confirms the intent status and publishes payment events to the `Event Backbone` so the saga can proceed.

This keeps UrbanCart at PCI-DSS SAQ A scope. Because no UrbanCart component ever stores, processes, or transmits cardholder data, no segmented cardholder data environment is required across the EKS cluster, and the `Payment DB` is not a CDE asset.

- `Payment Service` handles intents, captures, and refunds — never PANs.
- Hosted fields move card data device-to-provider, out of UrbanCart's trust boundary.
- `Payment DB` stores only provider references and non-sensitive payment state.
- No network segmentation is introduced into the AWS EKS / eu-central-1 environment for PCI reasons.

## Alternatives Considered

### Handle raw card data internally in the Payment Service

UrbanCart would accept card details through its own forms, pass them through the `API Gateway` to the `Payment Service`, and forward them to the `Payment Provider` server-side. Rejected because any component that transmits a PAN enters the cardholder data environment, pushing the platform into full PCI-DSS scope (SAQ D or a formal Report on Compliance). This would force network segmentation of the EKS environment, dedicated key management, expanded audit obligations, and significant ongoing cost — directly contradicting the brief's stated goal of no segmentation and conflicting with the platform's moderate budget.

### Tokenize cards in a self-hosted vault

UrbanCart would stand up its own tokenization service to exchange PANs for internal tokens before reaching the `Payment Provider`. Rejected because the vault itself receives raw card data at the moment of tokenization, so it remains inside the cardholder data environment and still requires a segmented, audited CDE. It adds a stateful security-critical component to own and operate while delivering little benefit over the provider's own vaulting, which is already available through hosted fields.

### Full off-site redirect to a provider-hosted payment page

Instead of inline hosted fields, UrbanCart would redirect consumers to a fully provider-hosted payment page. This also achieves SAQ A and was a close contender, but it was not chosen as the primary approach because it fragments the checkout experience for the `Web Consumer` and `Mobile Consumer` and complicates the synchronous return path in the order saga. Hosted fields keep payment entry within UrbanCart's own checkout UI while still keeping card data out of scope.

## Consequences

### Positive

- UrbanCart stays at PCI-DSS SAQ A, the lightest self-assessment tier, dramatically reducing compliance and audit burden.
- No segmented cardholder data environment is needed in the EKS / eu-central-1 deployment, simplifying networking and infrastructure as the brief intends.
- The `Payment DB` holds only provider references and non-sensitive intent/refund metadata, lowering the blast radius of any database compromise.
- The team avoids building and operating security-critical card-handling code, freeing capacity for core marketplace features.

### Negative

- UrbanCart becomes dependent on the `Payment Provider`'s hosted UI: outages, breaking changes, or styling limitations in the hosted fields directly affect the checkout experience and the order saga's ability to progress.
- Payment confirmation relies on the provider's webhook and intent-status reliability; the `Payment Service` must handle delayed, duplicated, or missing provider callbacks idempotently, and the saga in `Order Service` must tolerate the resulting latency and uncertainty.
- SAQ A is contingent on never touching card data; any future feature that routes a PAN through a UrbanCart component would silently expand PCI scope, so the team must guard this boundary in design reviews and treat it as a standing constraint.
- Control over the payment flow's behavior and appearance is partially ceded to the provider, limiting customization of the on-device card-entry experience for `Web Consumer` and `Mobile Consumer`.