# Nissan GT-R Auto Unified Custom ERP & Omnichannel Commerce Platform
## Comprehensive Design Plan, Architecture Strategy, Migration Roadmap, Security Framework, and Cursor AI Audit Prompt

**Document status:** Master planning specification  
**Purpose:** Preserve the existing custom project, audit and harden it, then evolve it into one unified automotive commerce and ERP platform using proven architectural patterns from **Medusa**, **Bagisto**, **Odoo**, **OmniCart**, and the **GSF APK** as a UX and workflow reference.  
**Important principle:** These projects are **reference sources**, not independent systems to run in parallel and not codebases to blindly merge.

---

# 1. Executive Summary

The Nissan GT-R Auto project already contains substantial custom business ideas, workflows, web functionality, backend functionality, management requirements, inventory concepts, POS requirements, and automotive-specific logic. The objective is **not** to discard this work or restart from zero.

The objective is to perform a controlled **architecture audit, security hardening, modular refactor, UI redesign, and client rebuild** so that the final solution becomes a unified, maintainable, secure, scalable platform.

The final platform will support:

1. Customer web commerce
2. Customer Android application
3. Management web application
4. Management Android application
5. Tablet kiosk/POS application
6. Shared backend APIs
7. Shared identity, roles, permissions, and audit controls
8. Automotive parts catalogue and vehicle fitment
9. Inventory, warehouse, procurement, supplier, order, and financial workflows
10. Reporting, dashboards, notifications, and operational management

The platform will use the following projects as **architectural and UX references**:

| Reference | Primary value to extract |
|---|---|
| Medusa | Modular commerce domains, workflows, carts, checkout, order lifecycle, pricing, promotions, sales channels, API-first patterns |
| Bagisto | Complete e-commerce storefront structure, catalogue administration, customer commerce information architecture, web management patterns |
| Odoo | ERP domain modelling, inventory movements, warehouses, procurement, suppliers, accounting concepts, reporting, business workflows |
| OmniCart | Android commerce architecture, Jetpack Compose patterns, product discovery, search, cart, checkout, account and order flows |
| GSF APK | Automotive customer UX, vehicle identification, search, categories, promotions, navigation, product discovery, mobile interaction patterns |

The final product is **one custom platform**. Medusa, Bagisto, Odoo, OmniCart, and GSF will not be deployed as separate systems.

---

# 2. Core Strategic Decision

## 2.1 What will not be done

The project will not:

- Run Medusa, Bagisto, and Odoo as separate production systems with synchronization between them.
- Create multiple competing sources of truth for products, stock, pricing, customers, or orders.
- Copy entire repositories into the current project without understanding dependencies and licensing.
- Treat decompiled GSF code as production source code.
- Trust client-side role checks as security controls.
- Continue adding features to an un-audited architecture.
- Replace the current project before documenting and preserving its custom functionality.

## 2.2 What will be done

The project will:

- Preserve and inventory the existing custom functionality.
- Audit the current backend, web application, database, APIs, authentication, authorization, and UI.
- Extract proven architectural patterns from the reference projects.
- Define one modular domain architecture.
- Establish one authoritative database and one source of truth for each business entity.
- Build all clients against the same secured backend.
- Rebuild the Android layer using a shared architecture.
- Use GSF as a behavioural and UX reference while creating original Nissan GT-R Auto branding and implementation.
- Introduce automated security, quality, testing, and deployment controls.

---

# 3. Target Platform Vision

```text
                         NISSAN GT-R AUTO PLATFORM

 ┌──────────────────────────────────────────────────────────────┐
 │                         CLIENT CHANNELS                      │
 │                                                              │
 │ Customer Web     Customer Android     Management Web         │
 │ Management App   Tablet Kiosk/POS     Future Integrations    │
 └───────────────────────────┬──────────────────────────────────┘
                             │
                             ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                    UNIFIED PLATFORM API                      │
 │                                                              │
 │ Authentication • Authorization • Validation • Audit          │
 │ Rate Limits • API Versioning • Notifications • Media         │
 └───────────────────────────┬──────────────────────────────────┘
                             │
         ┌───────────────────┼────────────────────┐
         ▼                   ▼                    ▼
 ┌──────────────┐   ┌──────────────────┐  ┌───────────────────┐
 │ COMMERCE     │   │ AUTOMOTIVE       │  │ ERP OPERATIONS    │
 │ Catalogue    │   │ Vehicles         │  │ Inventory         │
 │ Pricing      │   │ Fitment          │  │ Warehouses        │
 │ Promotions   │   │ VIN/Chassis      │  │ Procurement       │
 │ Cart         │   │ OEM Numbers      │  │ Suppliers         │
 │ Checkout     │   │ Cross-reference  │  │ Finance           │
 │ Orders       │   │ EPC/Diagrams     │  │ Reporting         │
 └──────────────┘   └──────────────────┘  └───────────────────┘
                             │
                             ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                     AUTHORITATIVE DATA                        │
 │ Products • Parts • Vehicles • Fitment • Stock • Orders       │
 │ Customers • Suppliers • Payments • Users • Audit Logs        │
 └──────────────────────────────────────────────────────────────┘
```

---

# 4. Architectural Principles

## 4.1 One platform, multiple experiences

Customer, management, and POS applications are different interfaces over the same business platform. They must not implement conflicting versions of stock, pricing, permissions, order status, or financial logic.

## 4.2 Backend owns business rules

The backend is authoritative for:

- Pricing
- Discounts
- Tax
- Stock availability
- Stock reservations
- Order transitions
- Payment state
- Permissions
- Data ownership
- Financial calculations
- Approval workflows

Clients may display calculated information, but they must not be trusted to enforce critical business rules.

## 4.3 Modular monolith first

The recommended initial architecture is a **modular monolith**, not microservices.

A modular monolith provides:

- One deployable backend
- One database
- Clear internal domain boundaries
- Lower operational complexity
- Easier auditing
- Easier development with Cursor
- A future path to extracting services if scale requires it

Microservices should only be introduced when there is a demonstrated operational or scaling need.

## 4.4 Domain boundaries

The backend should be divided into modules with explicit responsibilities:

```text
backend/
├── core/
│   ├── authentication/
│   ├── authorization/
│   ├── users/
│   ├── roles/
│   ├── permissions/
│   ├── audit/
│   ├── notifications/
│   ├── media/
│   ├── configuration/
│   └── shared/
│
├── commerce/
│   ├── catalogue/
│   ├── categories/
│   ├── brands/
│   ├── pricing/
│   ├── promotions/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   ├── payments/
│   ├── fulfilment/
│   └── returns/
│
├── automotive/
│   ├── vehicle-makes/
│   ├── vehicle-models/
│   ├── generations/
│   ├── engines/
│   ├── transmissions/
│   ├── vin-chassis/
│   ├── fitment/
│   ├── oem-part-numbers/
│   ├── supersessions/
│   ├── cross-references/
│   ├── epc/
│   └── diagrams/
│
├── operations/
│   ├── inventory/
│   ├── stock-ledger/
│   ├── warehouses/
│   ├── branches/
│   ├── stock-transfers/
│   ├── stock-counts/
│   ├── purchasing/
│   ├── suppliers/
│   ├── goods-receiving/
│   └── replenishment/
│
├── pos/
│   ├── sales/
│   ├── tills/
│   ├── shifts/
│   ├── receipts/
│   ├── refunds/
│   ├── returns/
│   ├── cash-management/
│   └── barcode/
│
├── finance/
│   ├── invoices/
│   ├── expenses/
│   ├── payments/
│   ├── accounting/
│   ├── tax/
│   └── financial-reporting/
│
└── management/
    ├── dashboards/
    ├── analytics/
    ├── approvals/
    ├── tasks/
    └── reports/
```

## 4.5 Explicit ownership

Every important entity must have one authoritative owner. For example:

| Entity | Authoritative module |
|---|---|
| User | Core identity |
| Role and permission | Core authorization |
| Product | Commerce catalogue |
| OEM part number | Automotive |
| Vehicle fitment | Automotive |
| Price | Commerce pricing |
| Physical stock | Operations inventory |
| Cart | Commerce cart |
| Customer order | Commerce orders |
| POS sale | POS, with controlled order integration |
| Supplier | Operations procurement |
| Financial journal | Finance |
| Audit event | Core audit |

---

# 5. Reference Architecture Extraction Plan

## 5.1 Medusa patterns to adopt

Study and adapt:

- Domain-oriented modules
- Workflow orchestration
- Explicit order lifecycle transitions
- Cart and checkout separation
- Pricing and promotion modelling
- Sales-channel concepts where useful
- API-first design
- Event-driven internal communication
- Extensible commerce architecture

Do not copy Medusa wholesale. Recreate only the patterns that fit the existing stack and business requirements.

## 5.2 Bagisto patterns to adopt

Study and adapt:

- Customer storefront information architecture
- Product catalogue administration
- Category and attribute management
- Product detail structure
- Customer account workflows
- Web administration navigation
- Promotion and catalogue management UX
- Modular web-commerce organisation

Bagisto should be treated as a strong reference for web commerce and administration, not as a second production commerce engine.

## 5.3 Odoo patterns to adopt

Study and adapt:

- Inventory as a movement/ledger system rather than a single mutable quantity
- Warehouse and location modelling
- Procurement and supplier workflows
- Goods receiving
- Stock transfers
- Stock adjustments and counts
- Approval and state-machine workflows
- Accounting concepts
- Operational reporting
- ERP document relationships

The inventory design should preserve an immutable or append-only stock movement history where practical.

## 5.4 OmniCart patterns to adopt

Study and adapt:

- Feature-oriented Android structure
- Jetpack Compose UI patterns
- Product discovery
- Search and filtering
- Product details
- Wishlist
- Cart
- Checkout
- Address management
- Orders
- Customer account
- Repository and state-management patterns

The final Android architecture must be aligned to the existing backend and domain model rather than forced to match OmniCart exactly.

## 5.5 GSF APK patterns to observe

Use APKLab and JADX-GUI to inspect:

- Android manifest
- Application class
- Launcher and startup flow
- Activities, fragments, Compose indicators, and navigation
- Package structure
- Resource organization
- Strings, colours, icons, images, fonts
- Search flow
- Vehicle identification flow
- Category navigation
- Promotional layout
- Product listing and detail behaviour
- Cart and account flow
- Network layer indicators
- Third-party libraries
- Deep links and permissions

The GSF APK should be used to create a **UX and behaviour specification**, not copied into production.

---

# 6. GSF-Inspired Customer Experience

The customer application should be an original Nissan GT-R Auto experience with the following characteristics:

- Clean light background
- Premium automotive visual identity
- Dark brand navigation
- High-contrast action accents
- Compact but readable commerce layout
- Prominent search
- Vehicle identification near the top
- Horizontal automotive categories
- Promotional banners
- Product recommendations
- Persistent bottom navigation
- Cart badge
- Account and location access
- Responsive phone and tablet layouts

## 6.1 Vehicle identification

Customers should be able to identify a vehicle by:

1. Registration number, where supported
2. VIN/chassis number
3. Manual selection:
   - Make
   - Model
   - Generation
   - Year
   - Engine
   - Transmission
4. OEM/part number search

The selected vehicle should persist as a visible context:

> Current vehicle: Nissan GT-R R35 • 2017 • VR38DETT

Products should display fitment status:

- Fits selected vehicle
- May fit; VIN verification required
- Does not fit selected vehicle
- No fitment data available

## 6.2 Customer navigation

```text
Home
├── Vehicle Finder
├── Search
├── Categories
├── Promotions
├── Featured Products
└── Recommendations

Shop
├── Vehicle-filtered catalogue
├── Categories
├── Brands
├── Search and filters
└── Product details

Deals
├── Promotions
├── Bundles
├── Clearance
└── Featured offers

Orders
├── Current orders
├── Order tracking
├── Invoices
└── Returns

Account
├── Profile
├── Saved vehicles
├── Addresses
├── Payment preferences
└── Settings
```

---

# 7. Client Application Strategy

## 7.1 Customer Android application

Primary goals:

- GSF-inspired automotive UX
- OmniCart-inspired commerce structure
- Vehicle-aware catalogue
- Fast search
- Secure checkout
- Orders and account management

## 7.2 Management Android application

Primary goals:

- Operational dashboards
- Product and inventory management
- Orders and fulfilment
- Customer management
- Supplier and purchasing workflows
- Reports
- Role-based feature access

## 7.3 Tablet kiosk/POS application

Primary goals:

- Large touch targets
- Fast product and part-number search
- Barcode support
- Customer and vehicle lookup
- Fast cart creation
- Discounts with server-side authorization
- Multiple payment methods
- Receipts
- Returns and refunds
- Stock lookup
- Management functionality available according to role

The POS application should default to the selling workflow while allowing authorized users to access management features.

## 7.4 Shared Android foundation

```text
android/
├── core/
│   ├── common/
│   ├── design-system/
│   ├── networking/
│   ├── authentication/
│   ├── authorization/
│   ├── security/
│   ├── database/
│   ├── offline/
│   ├── sync/
│   └── testing/
│
├── domain/
│   ├── catalogue/
│   ├── vehicles/
│   ├── fitment/
│   ├── inventory/
│   ├── orders/
│   ├── customers/
│   ├── payments/
│   └── users/
│
├── feature/
│   ├── home/
│   ├── search/
│   ├── vehicle-finder/
│   ├── product-details/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   ├── account/
│   ├── inventory/
│   ├── management/
│   ├── reports/
│   └── pos/
│
├── customer-app/
├── management-app/
└── kiosk-pos-app/
```

---

# 8. Security and Quality Strategy

## 8.1 Security principle

Security is not inherited merely because a project uses a popular repository. The integrated platform must be independently reviewed and tested.

## 8.2 Backend security requirements

The audit must verify:

- Password hashing using a modern, appropriate algorithm
- Secure authentication flows
- Short-lived access tokens where applicable
- Safe refresh-token rotation and revocation
- Server-side authorization on every protected action
- Object-level authorization
- Role and permission checks
- Ownership checks
- Input validation at trust boundaries
- Parameterized database access or safe ORM use
- Protection against injection attacks
- Rate limiting
- Brute-force protection
- Secure file uploads
- Safe error handling
- No secrets in source code
- Environment-based secret management
- Secure CORS configuration
- Secure HTTP headers
- TLS in production
- Audit logs for sensitive actions
- Dependency vulnerability scanning
- Database backup and recovery procedures
- Logging that does not expose passwords, tokens, payment information, or sensitive personal data

## 8.3 Mobile security requirements

The Android applications should be reviewed against OWASP MASVS principles, including:

- Secure credential and token storage
- No secrets embedded in the APK
- Secure network communication
- Certificate and TLS configuration
- Secure local database handling
- Safe WebView use
- Minimal permissions
- Secure logging
- Release build hardening
- Dependency review
- Tamper and reverse-engineering risk assessment where justified
- Server-side authorization regardless of hidden UI

## 8.4 Security acceptance criteria

No feature is considered complete until:

- Authentication is tested
- Authorization is tested
- Invalid input is tested
- Ownership checks are tested
- Audit logging is verified where required
- Error handling is reviewed
- Automated tests pass
- Dependency scans pass or accepted risks are documented
- Relevant mobile and API security checks pass

---

# 9. Data and Workflow Design

## 9.1 Inventory

Do not rely only on:

```text
product.stock_quantity = 15
```

Use a movement model:

```text
StockMovement
- id
- product_id
- warehouse_id
- location_id
- movement_type
- quantity
- reference_type
- reference_id
- unit_cost
- created_at
- created_by
- reason
```

Movement types may include:

- Goods received
- Sale
- POS sale
- Customer return
- Supplier return
- Transfer out
- Transfer in
- Adjustment increase
- Adjustment decrease
- Stock count correction
- Reservation
- Reservation release

Current stock can be calculated or maintained as a controlled projection derived from movements.

## 9.2 Order lifecycle

Use explicit state transitions:

```text
DRAFT
→ PENDING_PAYMENT
→ PAID
→ CONFIRMED
→ PROCESSING
→ PARTIALLY_FULFILLED
→ FULFILLED
→ DELIVERED
```

Alternative paths:

```text
PENDING_PAYMENT → CANCELLED
PAID → REFUND_PENDING → REFUNDED
CONFIRMED → CANCELLED_WITH_APPROVAL
```

Every transition must be validated server-side and recorded in an order history/audit trail.

## 9.3 Automotive fitment

Core entities:

```text
VehicleMake
VehicleModel
VehicleGeneration
VehicleYear
Engine
Transmission
VehicleConfiguration
Part
PartNumber
OEMPartNumber
PartCrossReference
FitmentRule
FitmentEvidence
Diagram
DiagramHotspot
```

A part may fit multiple vehicle configurations. Fitment must not be represented as a simple text field.

---

# 10. Current Project Audit Strategy

The first implementation phase is an audit. Cursor must not refactor or rewrite before producing evidence-based documentation.

## 10.1 Audit inputs

Inspect:

- Repository structure
- All applications and packages
- Backend framework and runtime
- Web framework
- Android project status
- Database schema
- ORM models
- Migrations
- API routes
- Controllers/handlers
- Services
- Business logic
- Authentication
- Authorization
- Roles and permissions
- Environment configuration
- Secrets handling
- Dependency manifests and lock files
- Build scripts
- CI/CD configuration
- Tests
- Logging
- Error handling
- File uploads
- Payments
- Inventory
- POS
- Reporting
- Existing UI components
- Existing documentation

## 10.2 Required audit outputs

Create:

```text
docs/audit/
├── 00-executive-summary.md
├── 01-repository-map.md
├── 02-technology-stack.md
├── 03-current-architecture.md
├── 04-domain-model.md
├── 05-database-schema-analysis.md
├── 06-api-inventory.md
├── 07-authentication-analysis.md
├── 08-authorization-analysis.md
├── 09-security-findings.md
├── 10-business-feature-inventory.md
├── 11-ui-ux-audit.md
├── 12-android-audit.md
├── 13-code-quality-and-technical-debt.md
├── 14-test-coverage-and-gaps.md
├── 15-dependency-and-supply-chain-review.md
├── 16-reference-pattern-mapping.md
├── 17-target-architecture.md
├── 18-migration-roadmap.md
├── 19-risk-register.md
└── 20-decision-log.md
```

The audit must classify findings by:

- Critical
- High
- Medium
- Low
- Informational

Each finding must include:

- Evidence
- Affected files/modules
- Risk
- Business impact
- Recommended remediation
- Estimated effort
- Dependencies
- Whether remediation can be incremental

---

# 11. Cursor AI Master Audit Prompt

Copy the following prompt into Cursor at the root of the current project.

```text
You are acting as a senior software architect, application security engineer, ERP domain architect, e-commerce architect, Android architect, database architect, and UI/UX systems auditor.

PROJECT CONTEXT

This repository is an existing custom Nissan GT-R Auto platform containing a web application, backend, custom business features, and an Android application that is currently considered unsuitable for production. The project contains valuable custom ideas and workflows that must be preserved. Do not assume the project should be discarded or rewritten.

The target is one unified custom automotive commerce and ERP platform with:

1. Customer web commerce
2. Customer Android application
3. Management web application
4. Management Android application
5. Tablet kiosk/POS application
6. Shared backend APIs
7. Shared authentication, authorization, roles, permissions, and audit controls
8. Automotive parts catalogue
9. Vehicle make/model/year/engine/transmission fitment
10. VIN/chassis lookup
11. OEM part numbers, cross-references, supersessions, and future EPC diagrams
12. Inventory, warehouses, branches, stock movements, purchasing, suppliers, receiving, transfers, and stock counts
13. Cart, checkout, orders, fulfilment, returns, payments, promotions, and pricing
14. POS sales, receipts, returns, refunds, till and shift management
15. Finance, expenses, invoicing, accounting concepts, reporting, dashboards, and notifications

REFERENCE PATTERNS

Use the following only as architectural and UX references. Do not blindly copy their code, do not introduce them as independent production systems, and do not create multiple sources of truth.

- Medusa: modular commerce domains, workflows, carts, checkout, order lifecycle, pricing, promotions, sales-channel and API-first patterns.
- Bagisto: complete web-store information architecture, catalogue administration, customer commerce UX, category and product management, and web administration patterns.
- Odoo: inventory movement/ledger concepts, warehouses, locations, procurement, suppliers, goods receiving, operational workflows, approvals, accounting concepts, and reporting.
- OmniCart: Android commerce feature architecture, Jetpack Compose patterns, catalogue browsing, search, filters, product details, cart, checkout, orders, and account flows.
- GSF APK: automotive mobile UX, vehicle identification, search, categories, promotional layout, product discovery, navigation, cart, and account behaviour.

The GSF APK will be analyzed separately using APKLab and JADX-GUI. Any GSF analysis must be treated as a behavioural and UX reference. Do not copy proprietary code, branding, assets, or backend assumptions.

NON-NEGOTIABLE RULES

1. Do not modify production code during the first audit phase.
2. Do not delete files, replace frameworks, change dependencies, or perform broad refactors without an approved plan.
3. Do not invent missing functionality. Mark unknown items as UNKNOWN and identify the evidence required.
4. Do not claim security merely because a framework or repository is popular.
5. Treat backend authorization as authoritative. Client-side role checks are never sufficient.
6. Preserve existing custom business ideas and workflows unless evidence shows they are unsafe, duplicated, or technically unsound.
7. Prefer an incremental modular-monolith migration before considering microservices.
8. Every important business entity must have one authoritative owner.
9. Avoid duplicate implementations of pricing, stock, permissions, order state, and financial logic.
10. All findings must cite exact file paths, symbols, endpoints, tables, migrations, or configuration entries where available.

AUDIT TASKS

PHASE A — REPOSITORY DISCOVERY

Inspect the entire repository and produce:

- Repository map
- Application/module map
- Technology stack
- Runtime and framework versions
- Build and deployment architecture
- Dependency manifests and lock files
- Environment/configuration files
- Existing documentation
- Test structure
- CI/CD structure

PHASE B — CURRENT ARCHITECTURE

Determine:

- Current architectural style
- Backend layers and dependencies
- Domain boundaries
- Coupling and circular dependencies
- Data access patterns
- API architecture
- Event or queue usage
- Caching
- Background jobs
- File/media handling
- Error handling
- Logging and observability
- Configuration and secrets handling

Create a current architecture diagram in Mermaid and a written explanation.

PHASE C — BUSINESS FEATURE INVENTORY

Create a complete feature inventory. For every feature record:

- Feature name
- Description
- User roles
- Client applications
- Backend modules
- API endpoints
- Database entities
- Current status: complete, partial, broken, unknown
- Business value
- Security sensitivity
- Dependencies
- Preserve/refactor/replace recommendation

Do not omit custom features simply because they are incomplete.

PHASE D — DATABASE AND DOMAIN AUDIT

Inspect models, migrations, schema, constraints, indexes, foreign keys, transactions, and data integrity.

Identify:

- Duplicate entities
- Missing constraints
- Weak relationships
- Orphan risks
- Missing indexes
- Inconsistent naming
- Data ownership conflicts
- Inventory design weaknesses
- Order lifecycle weaknesses
- Financial data integrity risks
- Migration risks

Propose a target domain model without changing the current database.

PHASE E — API AUDIT

Inventory every API route and document:

- Method
- Path
- Authentication requirement
- Authorization requirement
- Request schema
- Response schema
- Controller/handler
- Service
- Database access
- Validation
- Rate limiting
- Audit logging
- Error handling
- Client consumers

Identify:

- Unprotected routes
- Missing object-level authorization
- Missing role/permission checks
- Inconsistent response formats
- Missing validation
- Overly broad data exposure
- Sensitive information leakage
- N+1 or inefficient queries
- Missing pagination
- Missing API versioning where relevant

PHASE F — AUTHENTICATION AND AUTHORIZATION SECURITY AUDIT

Review:

- Password hashing
- Login flows
- Session/token lifecycle
- Refresh tokens
- Token storage assumptions
- Logout and revocation
- Password reset
- Email/phone verification
- Multi-factor readiness
- Role assignment
- Permission enforcement
- Object ownership checks
- Administrative privilege boundaries
- Account enumeration
- Brute-force protection
- Rate limiting
- Audit trails

Create an authorization matrix for every role and major resource.

PHASE G — GENERAL SECURITY AUDIT

Review:

- Secret exposure
- Environment configuration
- CORS
- CSRF where applicable
- XSS
- SQL/NoSQL injection
- Command injection
- Path traversal
- Insecure deserialization
- SSRF
- File upload risks
- Open redirects
- Error leakage
- Logging of secrets
- Dependency vulnerabilities
- Insecure defaults
- Missing security headers
- TLS assumptions
- Backup and recovery
- Payment handling
- Privacy and sensitive-data exposure

Classify findings as Critical, High, Medium, Low, or Informational.

PHASE H — WEB UI/UX AUDIT

Review:

- Information architecture
- Navigation
- Design consistency
- Typography
- Spacing
- Colour system
- Component reuse
- Responsive behaviour
- Accessibility
- Loading states
- Empty states
- Error states
- Form validation
- Dashboard usability
- Customer shopping journey
- Management workflow efficiency

Create a proposed shared design-system specification.

PHASE I — ANDROID AUDIT

Review:

- Current project structure
- Kotlin/Java quality
- Compose/XML architecture
- Navigation
- State management
- Dependency injection
- Networking
- Authentication
- Secure storage
- Offline support
- Database usage
- Sync
- Error handling
- Testing
- Build variants
- Release configuration
- UI/UX quality

Determine whether the current Android code should be:
- Preserved
- Refactored incrementally
- Rebuilt as a new client while preserving backend contracts

Do not rebuild during the audit.

PHASE J — REFERENCE PATTERN MAPPING

Map each target module to useful patterns:

- Medusa
- Bagisto
- Odoo
- OmniCart
- GSF UX reference

For each mapping explain:

- Pattern to adopt
- Why it is useful
- How it fits the current stack
- Required adaptation
- Risks
- Licensing/attribution considerations
- Whether to implement from scratch or adapt compatible code

PHASE K — TARGET ARCHITECTURE

Propose a unified modular-monolith target architecture with:

- Core identity and authorization
- Commerce
- Automotive
- Operations/ERP
- POS
- Finance
- Management
- Shared infrastructure

Provide:

- Mermaid context diagram
- Container diagram
- Module dependency diagram
- Data ownership map
- API boundary map
- Client application map
- Recommended repository structure
- Database strategy
- Event strategy
- Caching strategy
- Background-job strategy
- Media strategy
- Observability strategy

PHASE L — MIGRATION STRATEGY

Create an incremental migration plan that:

- Preserves current functionality
- Avoids a big-bang rewrite
- Introduces tests before risky refactors
- Stabilizes security first
- Establishes module boundaries
- Allows the web app to continue operating
- Rebuilds Android clients against stable APIs
- Supports rollback

Divide the roadmap into phases with:

- Objective
- Scope
- Deliverables
- Dependencies
- Risks
- Acceptance criteria
- Rollback plan

PHASE M — TEST AND QUALITY STRATEGY

Propose:

- Unit tests
- Integration tests
- API contract tests
- Authorization tests
- End-to-end tests
- UI tests
- Android tests
- POS workflow tests
- Inventory integrity tests
- Financial calculation tests
- Performance tests
- Security tests

Define minimum quality gates for pull requests and releases.

REQUIRED OUTPUTS

Create the following files under docs/audit/:

00-executive-summary.md
01-repository-map.md
02-technology-stack.md
03-current-architecture.md
04-domain-model.md
05-database-schema-analysis.md
06-api-inventory.md
07-authentication-analysis.md
08-authorization-analysis.md
09-security-findings.md
10-business-feature-inventory.md
11-ui-ux-audit.md
12-android-audit.md
13-code-quality-and-technical-debt.md
14-test-coverage-and-gaps.md
15-dependency-and-supply-chain-review.md
16-reference-pattern-mapping.md
17-target-architecture.md
18-migration-roadmap.md
19-risk-register.md
20-decision-log.md

Also create:

docs/audit/diagrams/
docs/audit/inventories/
docs/audit/evidence/

Use Mermaid diagrams where appropriate.

FINAL RESPONSE FORMAT

After completing the audit, provide:

1. Executive summary
2. Top 10 risks
3. Top 10 strengths worth preserving
4. Security findings by severity
5. Architecture assessment
6. UI/UX assessment
7. Android assessment
8. Recommended target architecture
9. Incremental migration roadmap
10. Decisions requiring owner approval

Do not begin implementation. Stop after producing the audit and request approval for the proposed remediation roadmap.
```

---

# 12. APKLab and JADX-GUI Analysis Workflow

## 12.1 Workspace separation

Keep reverse-engineered reference material separate from production code:

```text
nissan-gtr-auto/
├── reference-analysis/
│   └── gsf-apk/
│       ├── original-apk/
│       ├── jadx-output/
│       ├── apktool-output/
│       ├── resources/
│       ├── screenshots/
│       └── findings/
│
├── production-platform/
│   ├── backend/
│   ├── web/
│   ├── android/
│   └── docs/
│
└── architecture/
```

Do not add decompiled GSF source to production modules.

## 12.2 APKLab process

1. Install APKLab in VS Code/Cursor.
2. Configure Java/JDK and required tools.
3. Open the GSF APK through APKLab.
4. Generate:
   - JADX output
   - Apktool resource output
   - Manifest view
   - Smali output where needed
5. Search for:
   - Launcher activity
   - Navigation
   - Vehicle search terms
   - Registration/VIN terms
   - Product and category terms
   - Cart and checkout terms
   - API base URLs
   - Retrofit/OkHttp or equivalent indicators
   - Analytics and notification SDKs
6. Document findings rather than copying code.

## 12.3 JADX-GUI process

Use JADX-GUI for:

- Easier class navigation
- Global search
- Call hierarchy inspection
- Usage search
- Manifest inspection
- Library identification
- Readable decompiled code review

## 12.4 GSF analysis deliverables

Create:

```text
docs/reference-analysis/gsf/
├── 00-scope-and-legal-notes.md
├── 01-technical-observations.md
├── 02-navigation-map.md
├── 03-screen-inventory.md
├── 04-vehicle-finder-analysis.md
├── 05-search-and-catalogue-analysis.md
├── 06-product-detail-analysis.md
├── 07-cart-and-checkout-analysis.md
├── 08-account-and-orders-analysis.md
├── 09-ui-design-tokens.md
├── 10-ux-patterns-to-adopt.md
├── 11-patterns-not-to-copy.md
└── 12-nissan-gtr-adaptation.md
```

---

# 13. Implementation Roadmap

## Phase 0 — Preservation and baseline

Deliverables:

- Full source backup
- Database backup
- Environment/configuration inventory
- Dependency lock files
- Current build verification
- Baseline screenshots
- Current API snapshot
- Feature inventory

Acceptance criteria:

- Existing project can be restored.
- Current functionality is documented.
- No production code has been deleted.

## Phase 1 — Architecture and security audit

Deliverables:

- Full Cursor audit package
- Security findings
- Current architecture map
- Domain and database analysis
- UI/UX audit
- Android audit
- Risk register

Acceptance criteria:

- Every major module is mapped.
- Every API is inventoried.
- High-risk security gaps are identified.
- Custom business features are documented.

## Phase 2 — Target architecture approval

Deliverables:

- Approved module boundaries
- Data ownership map
- Target repository structure
- API standards
- Security standards
- Design-system standards
- Migration roadmap

Acceptance criteria:

- No ambiguous ownership of products, stock, pricing, orders, or permissions.
- The target architecture supports all client applications.

## Phase 3 — Security stabilization

Priorities:

1. Secrets
2. Authentication
3. Authorization
4. Object-level access control
5. Validation
6. Rate limiting
7. Audit logging
8. Dependency vulnerabilities
9. Secure error handling
10. Backup and recovery

Acceptance criteria:

- Critical findings resolved.
- High findings resolved or formally accepted with mitigation.
- Security regression tests added.

## Phase 4 — Modular backend refactor

Deliverables:

- Clear domain modules
- Stable API contracts
- Transaction boundaries
- Event conventions
- Shared error format
- Validation standards
- Audit standards

Acceptance criteria:

- Existing features continue working.
- Module boundaries are enforced.
- Automated tests cover critical workflows.

## Phase 5 — Web UI redesign

Deliverables:

- Shared design system
- Customer storefront redesign
- Management UI redesign
- Responsive layouts
- Accessibility improvements
- Consistent loading/empty/error states

Acceptance criteria:

- Core customer and management workflows are usable and consistent.
- No major regression in existing functionality.

## Phase 6 — Android foundation

Deliverables:

- Shared Android core
- Secure networking
- Authentication
- Design system
- Navigation
- Offline foundation
- Testing foundation

Acceptance criteria:

- Customer, management, and POS apps can share common modules.
- APIs are consumed through stable contracts.

## Phase 7 — Customer Android application

Deliverables:

- GSF-inspired home
- Vehicle finder
- Search
- Catalogue
- Product details
- Fitment display
- Cart
- Checkout
- Orders
- Account

## Phase 8 — Management Android application

Deliverables:

- Dashboard
- Inventory
- Products
- Orders
- Customers
- Suppliers
- Reports
- Role-based access

## Phase 9 — Tablet kiosk/POS

Deliverables:

- Tablet-first layout
- Fast search
- Barcode support
- Customer and vehicle lookup
- Cart
- Payments
- Receipts
- Returns
- Stock lookup
- Authorized management features

## Phase 10 — Automotive EPC and advanced functions

Deliverables:

- Fitment rules
- OEM cross-reference
- Part supersession
- Exploded diagrams
- Interactive hotspots
- VIN-assisted identification

## Phase 11 — Production hardening

Deliverables:

- Load testing
- Security testing
- Monitoring
- Alerting
- Backup restore testing
- Release process
- Incident response plan
- Documentation
- Training

---

# 14. Definition of Done

A feature is complete only when:

- Business requirements are documented.
- Backend ownership is clear.
- Authorization is enforced server-side.
- Validation exists.
- Errors are handled consistently.
- Audit logging is added where required.
- Database migrations are safe.
- Tests are implemented.
- Web and Android UI states are complete.
- Loading, empty, offline, and error states are handled.
- Accessibility and responsive behaviour are considered.
- Security review passes.
- Documentation is updated.

---

# 15. Governance and Decision Rules

## 15.1 Architecture decisions

All major decisions must be recorded in:

```text
docs/audit/20-decision-log.md
```

Each decision should include:

- Context
- Options considered
- Decision
- Rationale
- Consequences
- Date
- Owner
- Review date

## 15.2 Change control

Cursor should not make broad architectural changes without:

1. An evidence-based finding
2. A written proposal
3. Impact analysis
4. Migration plan
5. Rollback plan
6. Approval

## 15.3 Reference-code governance

Before adapting code from any repository:

- Verify licence compatibility.
- Record source and version.
- Preserve required notices.
- Avoid copying proprietary GSF code or assets.
- Prefer implementing the architectural pattern independently where practical.

---

# 16. Final Recommended Strategy

The recommended path is:

1. Preserve the current project.
2. Run the Cursor audit prompt without allowing code modification.
3. Review the audit and approve the target architecture.
4. Stabilize security and API contracts.
5. Refactor the backend into a modular monolith.
6. Retain valuable custom workflows.
7. Use Medusa patterns for commerce architecture.
8. Use Bagisto patterns for web storefront and administration UX.
9. Use Odoo patterns for ERP, inventory, procurement, and operational workflows.
10. Use OmniCart patterns for Android commerce architecture.
11. Use APKLab and JADX-GUI to document GSF UX and behaviour.
12. Build original Nissan GT-R Auto web, customer Android, management Android, and tablet POS applications on the unified platform.
13. Validate the final system through security testing, automated testing, performance testing, and operational acceptance testing.

The result should be:

> **One secure, modular, automotive-aware, omnichannel commerce and ERP platform with one source of truth, multiple specialized client applications, and custom Nissan GT-R Auto business functionality preserved and strengthened.**
