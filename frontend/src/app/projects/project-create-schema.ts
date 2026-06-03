export const DECIDE = '__decide__';
export const OTHER = '__other__';

export type QuestionType = 'text' | 'longtext' | 'number' | 'single' | 'multi';

interface BaseQuestion {
  id: string;
  label: string;
  helpText?: string;
  required?: boolean;
}

export interface TextQuestion extends BaseQuestion {
  type: 'text' | 'longtext' | 'number';
  placeholder?: string;
}

export interface ChoiceQuestion extends BaseQuestion {
  type: 'single' | 'multi';
  options: readonly string[];
}

export type Question = TextQuestion | ChoiceQuestion;

export interface Section {
  id: string;
  title: string;
  subtitle?: string;
  questions: readonly Question[];
}

export type Answer =
  | { kind: 'text'; value: string }
  | { kind: 'single'; selected: string; otherText: string }
  | { kind: 'multi'; selected: readonly string[]; otherText: string };

export function defaultAnswerForQuestion(q: Question): Answer {
  switch (q.type) {
    case 'text':
    case 'longtext':
    case 'number':
      return { kind: 'text', value: '' };
    case 'single':
      return { kind: 'single', selected: '', otherText: '' };
    case 'multi':
      return { kind: 'multi', selected: [], otherText: '' };
  }
}

export const SECTIONS: readonly Section[] = [
  {
    id: 'basics',
    title: 'Project basics',
    subtitle: 'The foundation for the C4 Context diagram and the system’s purpose.',
    questions: [
      {
        id: 'name',
        label: 'Project name',
        type: 'text',
        required: true,
        placeholder: 'e.g. Acme Orders Platform'
      },
      {
        id: 'description',
        label: 'Description',
        type: 'longtext',
        required: true,
        placeholder: 'What does this system do? Who is it for? What problem does it solve?'
      },
      {
        id: 'domain',
        label: 'Business domain',
        type: 'single',
        options: [
          'E-commerce',
          'Fintech',
          'Healthcare',
          'EdTech',
          'SaaS / Productivity',
          'IoT',
          'Media / Streaming',
          'Social',
          'Gaming',
          'Enterprise / B2B',
          'Government / Public sector'
        ]
      },
      {
        id: 'projectStage',
        label: 'Project stage',
        type: 'single',
        options: [
          'Greenfield (brand new)',
          'Rewrite of an existing system',
          'Extracting services from a monolith',
          'Adding to an existing microservice landscape',
          'Proof of concept'
        ]
      },
      {
        id: 'actors',
        label: 'Primary external actors',
        helpText: 'Who or what interacts with the system from the outside?',
        type: 'multi',
        options: [
          'End consumers (web)',
          'End consumers (mobile)',
          'Internal employees / back-office',
          'Partner / B2B systems',
          'Third-party APIs',
          'Admin / operators',
          'IoT devices'
        ]
      }
    ]
  },
  {
    id: 'scale',
    title: 'Scale & performance',
    subtitle: 'Drives autoscaling, multi-region, CQRS, caching and consistency choices.',
    questions: [
      {
        id: 'expectedUsers',
        label: 'Expected users',
        type: 'single',
        options: [
          '< 1k',
          '1k – 10k',
          '10k – 100k',
          '100k – 1M',
          '1M – 10M',
          '> 10M'
        ]
      },
      {
        id: 'peakLoad',
        label: 'Peak load (requests per second)',
        type: 'single',
        options: [
          '< 10 RPS',
          '10 – 100 RPS',
          '100 – 1k RPS',
          '1k – 10k RPS',
          '> 10k RPS'
        ]
      },
      {
        id: 'availability',
        label: 'Availability target',
        type: 'single',
        options: [
          '99% (~3.65 days/yr downtime)',
          '99.9% (~8.76 hrs/yr)',
          '99.95% (~4.4 hrs/yr)',
          '99.99% (~52 min/yr)',
          '99.999% (~5 min/yr)'
        ]
      },
      {
        id: 'latency',
        label: 'p95 latency target for user-facing requests',
        type: 'single',
        options: [
          '< 50 ms',
          '50 – 200 ms',
          '200 – 500 ms',
          '500 ms – 1 s',
          '> 1 s (batch / async OK)'
        ]
      },
      {
        id: 'consistency',
        label: 'Data consistency model',
        helpText: 'The core CAP-theorem decision — affects sagas vs 2PC, CQRS, event sourcing.',
        type: 'single',
        options: [
          'Strong consistency everywhere',
          'Eventual consistency is fine',
          'Mixed (strong for some flows, eventual for others)'
        ]
      },
      {
        id: 'geo',
        label: 'Geographic distribution',
        type: 'single',
        options: [
          'Single region',
          'Single region, multi-AZ',
          'Multi-region (active-passive)',
          'Multi-region (active-active)',
          'Edge / global CDN'
        ]
      }
    ]
  },
  {
    id: 'data',
    title: 'Data & storage',
    subtitle: 'Determines DB-per-service choices and storage technology.',
    questions: [
      {
        id: 'dataTypes',
        label: 'Primary data types',
        type: 'multi',
        options: [
          'Relational / transactional',
          'Document / JSON',
          'Key-value / cache',
          'Time-series / metrics',
          'Search (full-text)',
          'Graph',
          'Vector / embeddings',
          'Blob / files / media',
          'Streaming events'
        ]
      },
      {
        id: 'dataVolume',
        label: 'Estimated data volume after 1 year',
        type: 'single',
        options: ['< 10 GB', '10 GB – 100 GB', '100 GB – 1 TB', '1 – 10 TB', '> 10 TB']
      },
      {
        id: 'rwProfile',
        label: 'Read / write profile',
        type: 'single',
        options: [
          'Read-heavy',
          'Write-heavy',
          'Balanced',
          'Bursty writes, steady reads',
          'Append-only / event log'
        ]
      },
      {
        id: 'dataSensitivity',
        label: 'Data sensitivity',
        helpText: 'Drives encryption, access control and audit needs.',
        type: 'multi',
        options: [
          'PII (personally identifiable info)',
          'PHI (protected health info)',
          'PCI (payment card data)',
          'Financial / accounting records',
          'Authentication credentials',
          'Trade secrets / IP',
          'Public data only'
        ]
      }
    ]
  },
  {
    id: 'integrations',
    title: 'Integrations & communication',
    subtitle: 'Defines external boxes in the Context diagram and internal arrows in the Container diagram.',
    questions: [
      {
        id: 'thirdParty',
        label: 'Third-party integrations',
        type: 'multi',
        options: [
          'Payments (Stripe, Adyen, …)',
          'Identity (Auth0, Okta, Cognito, …)',
          'Email / SMS (SendGrid, Twilio, …)',
          'Push notifications',
          'Maps / geocoding',
          'Analytics / product telemetry',
          'CRM / marketing',
          'AI / LLM providers',
          'Search (Algolia, Elastic Cloud)'
        ]
      },
      {
        id: 'auth',
        label: 'Authentication & authorization',
        type: 'single',
        options: [
          'OAuth 2.0 / OIDC',
          'SAML / enterprise SSO',
          'API keys',
          'mTLS between services',
          'JWT (custom)',
          'Username / password only',
          'No authentication required'
        ]
      },
      {
        id: 'interService',
        label: 'Preferred inter-service communication',
        type: 'multi',
        options: [
          'Synchronous REST',
          'Synchronous gRPC',
          'GraphQL federation',
          'Async messaging (queues)',
          'Async events (pub/sub)',
          'Event sourcing'
        ]
      },
      {
        id: 'realtime',
        label: 'Real-time features',
        type: 'multi',
        options: [
          'WebSockets',
          'Server-Sent Events',
          'Long-polling',
          'Push notifications',
          'No real-time needs'
        ]
      },
      {
        id: 'apiStyle',
        label: 'Public API style',
        type: 'multi',
        options: [
          'REST (JSON)',
          'GraphQL',
          'gRPC',
          'Webhooks (outbound)',
          'BFF per client',
          'No public API'
        ]
      }
    ]
  },
  {
    id: 'tech',
    title: 'Tech stack, cloud & operations',
    subtitle: 'Constrains LLM suggestions to your team’s stack and ops maturity.',
    questions: [
      {
        id: 'backendLangs',
        label: 'Preferred backend languages',
        type: 'multi',
        options: [
          'Java / Kotlin',
          'C# / .NET',
          'Go',
          'Python',
          'Node.js / TypeScript',
          'Rust',
          'Ruby',
          'PHP',
          'Elixir'
        ]
      },
      {
        id: 'cloud',
        label: 'Cloud provider',
        type: 'single',
        options: [
          'AWS',
          'Azure',
          'GCP',
          'On-premises',
          'Hybrid',
          'Multi-cloud'
        ]
      },
      {
        id: 'deployment',
        label: 'Deployment model',
        type: 'single',
        options: [
          'Kubernetes',
          'Managed container service (ECS, Cloud Run, ACA)',
          'Serverless functions (Lambda, Functions, …)',
          'PaaS (Heroku, Render, Railway, …)',
          'Virtual machines',
          'Bare-metal'
        ]
      },
      {
        id: 'observability',
        label: 'Observability requirements',
        type: 'multi',
        options: [
          'Structured logging',
          'Metrics & dashboards',
          'Distributed tracing',
          'SLO / error budgets',
          'Alerting & on-call',
          'Centralized log search'
        ]
      },
      {
        id: 'budget',
        label: 'Budget sensitivity',
        type: 'single',
        options: [
          'Tight — minimize cost above all',
          'Moderate — balance cost and effort',
          'Generous — prefer managed services',
          'Not a concern'
        ]
      }
    ]
  },
  {
    id: 'compliance',
    title: 'Compliance, security & team',
    subtitle: 'Hard constraints + human context that shape what is realistic.',
    questions: [
      {
        id: 'compliance',
        label: 'Compliance frameworks',
        type: 'multi',
        options: [
          'GDPR',
          'CCPA',
          'HIPAA',
          'PCI-DSS',
          'SOC 2',
          'ISO 27001',
          'FedRAMP',
          'PSD2',
          'None required'
        ]
      },
      {
        id: 'residency',
        label: 'Data residency',
        type: 'single',
        options: [
          'No restriction',
          'EU only',
          'US only',
          'UK only',
          'Per-customer region',
          'On-premises only'
        ]
      },
      {
        id: 'teamSize',
        label: 'Team size',
        helpText: 'Bounds how many services are realistic (“2-pizza” teams).',
        type: 'single',
        options: [
          'Solo / 1 engineer',
          '2 – 5',
          '6 – 15',
          '16 – 50',
          '> 50'
        ]
      },
      {
        id: 'teamExperience',
        label: 'Team experience with microservices',
        type: 'single',
        options: [
          'None — first time',
          'Some — a few services in production',
          'Comfortable — multiple production systems',
          'Expert — large distributed systems'
        ]
      },
      {
        id: 'notes',
        label: 'Anything else',
        helpText: 'Constraints, preferences, integrations or context the questions did not capture.',
        type: 'longtext',
        placeholder: 'Free-form notes for the architect'
      }
    ]
  }
];
