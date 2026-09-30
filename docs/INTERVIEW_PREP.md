# ShopEase — Interview Preparation Guide

> Everything below is grounded in **this repository**. Every file path is real,
> every claim is something you can open and read. If you get asked "show me",
> you can.
>
> How to use this: read §1 (the 60-second pitch) and §2 (the request flow) the
> night before. Then work through your assigned topics in §3. Keep §6 (trap
> answers) open while you practise — those are the questions that separate a
> candidate who has used a framework from one who understands it.

---

## Table of contents

1. [The 60-second pitch](#1-the-60-second-pitch)
2. [How a request actually flows](#2-how-a-request-actually-flows)
3. [Topic-by-topic](#3-topic-by-topic)
   - [Microservices](#31-microservices)
   - [Onion Architecture](#32-onion-architecture)
   - [Dependency Injection](#33-dependency-injection)
   - [JWT Authentication](#34-jwt-authentication)
   - [Image Upload](#35-image-upload)
   - [AWS S3](#36-aws-s3)
   - [Logger](#37-logger)
   - [React Interceptors](#38-react-interceptors)
   - [PostgreSQL / SQL Server](#39-postgresql--sql-server)
   - [Vercel (UI)](#310-vercel-ui)
   - [Render (Backend)](#311-render-backend)
   - [GitHub Actions](#312-github-actions)
4. [Honest answers to "what would you improve?"](#4-honest-answers-to-what-would-you-improve)
5. [Questions they will ask](#5-questions-they-will-ask)
6. [Trap answers](#6-trap-answers)
7. [Code-walk order](#7-code-walk-order)
8. [Demo script](#8-demo-script)

---

## 1. The 60-second pitch

> "ShopEase is an e-commerce demo I built to learn microservices properly. It
> has five Spring Boot services — a Eureka registry, an API gateway, and three
> business services for products, orders and auth — plus a React frontend.
>
> The interesting part is the internal design. All three business services use
> **Onion Architecture**: the domain layer is plain Java with no Spring and no
> JPA, and everything it needs from the outside world — storage, a database, an
> HTTP call to another service — is declared as an **interface in the domain**
> and implemented in an outer layer. A build-time lint script fails CI if that
> rule is ever broken.
>
> Auth is a stateless JWT: auth-service issues it, the **gateway verifies it at
> the edge** and enforces the role rules, so a business service never holds the
> signing secret. Product images go to **AWS S3** behind a `FileStorage` port
> that also has a local-disk implementation, so the app runs with no cloud
> account at all. Every request carries a **correlation id** minted at the
> gateway, so one `grep` reconstructs the whole call chain across five services.
>
> The backend is five Dockerfiles on **Render**, the frontend is on **Vercel**,
> and **GitHub Actions** builds and tests all five services in parallel on every
> push."

### The numbers to know

| Fact | Value |
|---|---|
| Services | 5 (gateway, eureka, product, order, auth) |
| Databases | 3 logical DBs (`productdb`, `orderdb`, `authdb`) |
| Frontend | React 18 + Vite 6, axios, react-router |
| Java | 17, Spring Boot 4.1.1, Spring Cloud 2025.1.3 |
| Auth | HS256 JWT, 24 h expiry, stateless |
| Image storage | S3 via the AWS SDK v2, presigned-URL support |
| Unit tests | 60+, no Spring context, no database, no network |
| Cold start | 1–3 min on Render's free tier (0.1 CPU / 512 MB) |

---

## 2. How a request actually flows

This is the single most useful thing to have memorised. Be able to draw it.

### 2.1 "Buy now" — the request that touches every service

```
 BROWSER  (Vercel)
    │  POST /orders   { productId, quantity, username }
    │  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
    ▼
 API GATEWAY  (Render, reactive/Netty)
    │  1. CorrelationIdGlobalFilter  (order -100)
    │       inbound X-Correlation-Id or new UUID → MDC + header
    │  2. CorsWebFilter              preflight answer, allowed origins
    │  3. JwtAuthGlobalFilter        (order 0)
    │       parse + VERIFY the signature          → bad? 401
    │       rule: /orders/** needs any token      → none? 401
    │       add X-User-Name / X-User-Role downstream
    │  4. route match: Path=/orders/**  →  https://shopeease-order.onrender.com
    │  5. NettyRoutingFilter         the actual upstream call
    │  6. RequestLoggingGlobalFilter → one INFO line with status + duration
    ▼
 ORDER SERVICE  (Render, servlet/Tomcat)
    │  1. CorrelationIdFilter        (reads the forwarded header)
    │  2. RequestLoggingFilter
    │  3. OrderController.createOrder
    │       @Valid CreateOrderRequest        → 400 if malformed
    │       → CreateOrderCommand             (DTO → command)
    ▼
    │  4. OrderService.createOrder            APPLICATION LAYER
    │       ├─ ProductCatalog.findById(1)     ← the DOMAIN PORT, not Feign
    │       │     │
    │       │     ▼
    │       │   ProductCatalogFeignAdapter    INFRASTRUCTURE
    │       │     └─ ProductClient (Feign) → HTTPS
    │       │          404 → Optional.empty()        (no such product)
    │       │          5xx/timeout → ProductUnavailableException (→ 502)
    │       │
    │       │   ┌────────── PRODUCT SERVICE ──────────┐
    │       │   │ ProductController.getProduct        │
    │       │   │  → ProductService.getProduct        │
    │       │   │  → ProductRepository.findById       (port)
    │       │   │  → ProductRepositoryImpl            (JPA)
    │       │   │  → ProductJpaRepository             (Spring Data)
    │       │   │  → Postgres  productdb              SELECT
    │       │   └──────────────────────────────────────┘
    │       │
    │       ├─ existing PENDING order?  → merge quantities
    │       └─ else Order.place(...) → OrderRepository.save → INSERT
    ▼
    5. GlobalExceptionHandler maps any domain exception → status code
    6. JSON response  ──────────────────────────────────────────► BROWSER
                                                     │
                                                     └── response interceptor
                                                         reads the body
```

**The four things to say out loud about this flow:**

1. **The gateway is the only public entry point.** The browser never learns a
   service's hostname.
2. **Authorisation happens once, at the edge, before any downstream connection
   is opened.** A rejected request costs one JWT verification, not a database
   connection.
3. **Order-service does not know Feign exists.** It asks for a `ProductCatalog`
   — an interface declared in its own domain. That is why `OrderServiceTest` runs
   in milliseconds with no network.
4. **One correlation id ties all five log lines together.**

### 2.2 "Admin uploads a product image"

```
 BROWSER
    │  1. POST /products/images/presign?contentType=image/png
    │     Authorization: Bearer <ADMIN token>
    ▼
 GATEWAY  → admin-paths rule → role must be ADMIN, else 403
    ▼
 PRODUCT SERVICE
    ImageController.presignUpload
      → S3Presigner.presignPutObject(...)   ← SigV4-signed, 15-min expiry
    ◄──────────────────────────────────────  { uploadUrl, publicUrl, key, requiredHeaders }
    │
    │  2. PUT <uploadUrl>                   ← straight to S3, no API hop
    │
    │  3. POST /products  { …, imageUrl: publicUrl }
    ▼
 PRODUCT SERVICE → Postgres INSERT
```

With `STORAGE_TYPE=local` instead, step 2 disappears and the bytes go through
`POST /products/images` (multipart) into `./uploads`, served back at
`/uploads/**`.

---

## 3. Topic by topic

### 3.1 Microservices

**What it is.** An application split into independently deployable services that
communicate over the network and own their own data. The defining constraint is
not "multiple processes" — it is **no shared database and no shared code**.

**The five services in this repo**

| Service | Port | Owns | Key classes |
|---|---|---|---|
| `eureka-server` | 8761 | the registry | `EurekaServerApplication` |
| `api-gateway` | 8080 | routing, CORS, JWT verification | `JwtAuthGlobalFilter`, `CorrelationIdGlobalFilter` |
| `product-service` | 8081 | `productdb` | `ProductService`, `ImageService`, `S3FileStorage` |
| `order-service` | 8082 | `orderdb` | `OrderService`, `ProductCatalogFeignAdapter` |
| `auth-service` | 8083 | `authdb` | `AuthService`, `JwtTokenService` |

**Service discovery — `eureka-server/`**

`@EnableEurekaServer` starts a registry. Every other service has
`spring-cloud-starter-netflix-eureka-client` and `@EnableDiscoveryClient`, so it
registers itself on boot and renews a lease every 30 s. An instance that stops
renewing is evicted after 90 s.

Be ready for these follow-ups:

- *Why does the gateway not use the registry by default?* The default profile
  pins URLs (`application.yml`). On a free tier Eureka sleeps like everything
  else, so a cold registry plus a cold downstream service is the 1–3 minute cold
  start. The `discovery` profile (`application-discovery.yml`) switches every
  route to `lb://product-service` and sets `fetch-registry: true` — that is the
  real service-to-service routing, and it is a one-profile change.
- *What's a single point of failure here?* A standalone Eureka. In production
  you run an **odd** number of peers (3 or 5) so a majority always survives.
- *Why is the registry's fetch skipped by default?* `fetch-registry: false`.
  The Eureka client downloads the whole registry at boot and that call blocks
  startup until the server answers or times out. Services here never look each
  other up, so the download is pure cost.

**API gateway — `api-gateway/`**

A reverse proxy plus a security checkpoint. It owns no business logic and no
database.

Why centralise it:
- one origin → CORS becomes a non-issue;
- one place to enforce auth → a new service cannot be accidentally left open;
- one place for cross-cutting concerns (rate limiting, caching, size caps);
- service hostnames are never exposed to the client.

**Reactive, not servlet.** The pom uses
`spring-cloud-starter-gateway-server-webflux`. The gateway is I/O bound — it
spends its life waiting on the network — so a Netty event loop holds hundreds of
in-flight connections per thread where Tomcat uses one thread each. Business
services stay on `spring-boot-starter-webmvc` because JDBC *blocks*, and
blocking a reactive event loop would be a serious bug.

**Inter-service calls — `order-service/`**

`@FeignClient(name = "product-service", url = "${product.service.url:...}")`.
Feign reads the interface at startup and generates a proxy that builds the
request, sends it, deserialises the JSON, and converts non-2xx into an
exception. The address is a config value, not a literal in business logic.

**The two rules of microservices, and where you can point at them**

1. **No shared database.** `productdb`, `orderdb`, `authdb` are separate. If
   order-service could `SELECT` from the products table, releasing them together
   would become a single deployable unit and you would have paid all the
   operational cost of microservices for none of the benefit.
2. **No shared code.** `order-service/domain/model/Product.java` duplicates
   three fields of product-service's `Product`. That duplication is deliberate
   and the Javadoc says why: a shared `common` module couples release cycles and
   team roadmaps, and turns the system into a distributed monolith where
   everything ships together anyway but nobody can ship anything alone.

**Service-to-service vs shared-database, honestly**

| Shared DB | Microservices |
|---|---|
| one join is one transaction | a join is a network call that can fail |
| cheap to query across boundaries | must denormalise or aggregate |
| deploy all at once | independent deploys |
| scales as one unit | scales per service |
| failure domain is everything | failure domain is one service |

In this repo the "join" between orders and products is solved by
**denormalisation**: `OrderEntity` stores a `productName` snapshot taken at
purchase time, so listing orders needs one query and one service. The trade-off
is that renaming a product does not rewrite history — which for order history is
usually correct anyway.

---

### 3.2 Onion Architecture

**Where to look.** `product-service/` and `order-service/` and `auth-service/` —
all three are layered identically:

```
product-service/src/main/java/com/aditi/product_service/
├── presentation/          HTTP in, HTTP out
│   ├── controller/        ProductController, ImageController, HealthController
│   ├── dto/               ProductRequest, ImageUploadResponse
│   └── exception/         GlobalExceptionHandler
├── application/           use cases
│   └── service/           ProductService, ImageService
├── domain/                INNERMOST RING — no Spring, no JPA, no SDKs
│   ├── model/             Product, StoredFile, UploadRequest
│   ├── repository/        ProductRepository      (port)
│   ├── port/              FileStorage            (port)
│   └── exception/         ProductNotFoundException, …
├── infrastructure/        OUTERMOST RING — depends on everything
│   ├── persistence/       ProductEntity, ProductJpaRepository, ProductRepositoryImpl
│   ├── storage/           S3FileStorage, LocalFileStorage
│   ├── config/            BeanModule (composition root), StorageConfig, filters
│   └── logging/           CorrelationIdFilter, RequestLoggingFilter
└── package-info.java      ← the rules, written next to the code they apply to
```

**The one rule.** Dependencies point **inwards only**. The domain imports
nothing from the outside; every other layer may import the domain.

**Ports and adapters (hexagonal).** The domain declares what it *needs* as
interfaces; the outside supplies the implementation.

- **Driven / outbound ports** — the domain asks for something to happen:
  `ProductRepository`, `FileStorage`, `ProductCatalog`, `PasswordHasher`,
  `TokenService`.
- **Driving / inbound ports** — the outside asks the domain to do something. In
  this project the HTTP controller is the driver; the use case is the hexagon.

**Dependency inversion, concretely.** `AuthService` (application) needs to hash a
password. It does not import `BCryptPasswordEncoder`. It imports
`PasswordHasher`, which is declared in the domain:

```java
// domain/port/PasswordHasher.java — the domain states WHAT it needs
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String storedHash);
}

// infrastructure/security/BCryptPasswordHasher.java — the outside supplies HOW
public class BCryptPasswordHasher implements PasswordHasher { … }
```

At compile time the dependency arrow points from BCrypt **towards** the domain.
At source level the domain has no idea BCrypt exists. Swapping BCrypt for
Argon2id or a secrets service is one new class and zero edits above it.

**How it is enforced, not just claimed.** `tools/java-lint.mjs` scans every Java
file and **fails** if:

- anything under `domain/` imports `org.springframework.*`, `jakarta.*`,
  `software.amazon.*`, `io.jsonwebtoken.*` or Jackson;
- anything under `application/` imports `infrastructure` or `presentation`;
- any service imports another service's `com.aditi.*` package.

It runs as the `structure` job in `.github/workflows/ci.yml`. Say this out
loud: *the architecture rule is a build failure, not a code-review comment.*

> It found a real violation while this repo was being built:
> `OrderService` used to import `ProductClient` (a Feign interface in
> `infrastructure`) directly. That is exactly the leak the port now prevents.

**What Onion buys you, stated as benefits rather than vibes**

1. **Testability.** `ImageServiceTest` runs with no Spring context, no database,
   no AWS account, in milliseconds — because the two collaborators are
   interfaces.
2. **Database independence.** Switching to SQL Server changed three YAML files
   and zero Java files. Nothing in the domain or application layers moved.
3. **Vendor independence.** S3 → Cloudflare R2 is a new class implementing
   `FileStorage`, plus a property.
4. **Transport independence.** REST → gRPC means rewriting `presentation`.
   `AuthService` and `ProductService` are untouched.
5. **Parallel work.** Two people can work on the same service without
   conflicting, and CI builds them in parallel.

**Onion vs the other layered architecture — the answer that gets you hired**

Classic three-layer (`controller → service → dao`) looks similar but the service
layer imports the DAO, so the arrow points outward. Onion differs because:

| | 3-layer | Onion | Clean / Hexagonal |
|---|---|---|---|
| Domain depends on | nothing | nothing | nothing |
| Service depends on | the DAO interface (still infrastructure) | domain **ports** | inbound use cases |
| Entities | are the JPA entities | separate from JPA entities | separate |
| Rule enforcement | convention | a port, declared inwards | ports declared inwards |

**Why a separate `ProductEntity` from `Product`**

`Product` is immutable and validates itself in the constructor — Hibernate needs
a no-arg constructor and setters, so it cannot use it. That constraint is a
*storage* concern, so the storage-shaped class lives in `infrastructure` and two
mapping methods translate. The cost is boilerplate; the benefit is that the
domain survives a move to MongoDB, and Hibernate can never construct a
half-valid aggregate.

**Object persistence vs mapping** (if they push on "why not just use the entity")

The textbook alternative stores the domain object graph directly, keyed by an id,
and can persist any structure with no mapping. Its costs, which is why this
project did not choose it: no relational querying, no referential integrity, no
SQL reporting, no indexes you control, and lazy-loading hazards. The mapping
approach costs boilerplate and buys the entire relational toolset. For an
e-commerce catalogue, mapping wins.

---

### 3.3 Dependency Injection

**The three kinds, and the one to recommend**

| Kind | Looks like | Verdict |
|---|---|---|
| Field injection | `@Autowired` on a field | Avoid. Hides the dependency, forces a no-arg constructor, and cannot be used outside Spring. |
| **Constructor injection** | dependency in the constructor, `final` field | **Recommended.** Explicit, testable, immutable, works with plain `new`. |
| Setter injection | `@Autowired` on a setter | Only for genuinely optional/late-bound dependencies. |

Every service in this repo uses constructor injection. Compare:

```java
// before — the dependency is invisible until you read the field
@Service
public class OrderService {
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductClient productClient;   // also a layering violation
}

// after — the whole dependency list is on one line, and it is testable
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductCatalog productCatalog;

    public OrderService(OrderRepository orderRepository, ProductCatalog productCatalog) {
        this.orderRepository = orderRepository;
        this.productCatalog = productCatalog;
    }
}
```

Because the constructor is the only way in, the unit test can build it with
hand-written fakes and no Spring at all.

**The composition root — `BeanModule`**

`infrastructure/config/BeanModule.java` is the single place where
infrastructure classes are attached to domain ports:

```java
@Bean
@ConditionalOnMissingBean(UserRepository.class)
public UserRepository userRepository(UserJpaRepository jpaRepository) {
    return new UserRepositoryImpl(jpaRepository);
}

@Bean
@ConditionalOnMissingBean(PasswordHasher.class)
public PasswordHasher passwordHasher() {
    return new BCryptPasswordHasher();
}
```

Two things an interviewer will notice:

1. **The return type is the port, not the implementation.** Returning
   `BCryptPasswordHasher` would make every consumer depend on the concrete class
   and undo the inversion.
2. **`@ConditionalOnMissingBean` makes it a default.** A
   `@TestConfiguration` can declare its own `PasswordHasher` and it replaces the
   production one with no edit here. That is how a real integration test swaps
   BCrypt (slow, 50–100 ms per hash) for a fast deterministic hasher.

**Bean scopes**

`singleton` (default) — one instance per container, created once, shared by
every thread. That is why the injected fields are `final`: a singleton that
mutates a field is a data race. `request` / `prototype` exist; neither is used
here because the services are stateless. `prototype` on an injected dependency is
a classic trap: you get *one* prototype, not a new one per use.

**Environment-dependent wiring — `StorageConfig`**

`ImageService` declares `FileStorage`. It has no `if (useS3)`. Which
implementation arrives is decided once, at startup, by
`@ConditionalOnProperty`:

```java
@Bean @ConditionalOnProperty(name="app.storage.type", havingValue="local", matchIfMissing=true)
public FileStorage localFileStorage(…) { … }

@Bean(destroyMethod="close") @ConditionalOnProperty(name="app.storage.type", havingValue="s3")
public FileStorage s3FileStorage(StorageProperties p) { … }
```

`matchIfMissing = true` on the local one means a typo like `type=amazon` falls
back to local with a warning instead of producing a context with no
`FileStorage` bean. Had both matched, injection would fail fast with
`NoUniqueBeanDefinitionException` — a good failure, but a confusing one.

**Two dependency-injection traps worth naming**

- *Field injection hides a missing bean until that code path runs.* Constructor
  injection fails at startup instead.
- *`@Autowired(required = false)` hides a missing bean forever.* A null that is
  never exercised is a latent `NullPointerException`.
- *Don't reach past a port to a concrete class.* An earlier version of
  `ImageController` injected `S3FileStorage` through an `ObjectProvider` so it
  could offer presigned uploads conditionally. Two problems: a presentation
  class depending on a concrete infrastructure class is a layering leak, and
  resolving a concrete type from a `@Bean` method whose declared return type is
  an interface leans on Spring's type prediction — when that cannot answer, the
  provider quietly returns `null` and the feature silently disappears. The fix
  was to move the capability onto the **port** as two default methods
  (`supportsDirectUpload()`, `presignPutUrl(...)`), so the controller asks an
  abstraction and the local backend inherits a sensible "no" for free.

---

### 3.4 JWT Authentication

**Files**

| What | Where |
|---|---|
| Issue tokens | `auth-service/…/infrastructure/security/JwtTokenService.java` |
| Verify in the gateway | `api-gateway/…/security/GatewayTokenService.java` |
| Populate the security context | `auth-service/…/infrastructure/security/JwtAuthenticationFilter.java` |
| Enforce the rules | `api-gateway/…/config/GatewaySecurityProperties.java` + `application.yml` |
| Attach the token | `frontend/src/api/axiosInstance.js` |
| Read the claims in the UI | `frontend/src/utils/decodeToken.js` |

**Anatomy.** Three Base64URL segments: `header.payload.signature`.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhZGl0aSIsInJvbGUiOiJBRE1JTiJ9 . 4pcPyMD09olPSyXn...
{"alg":"HS256","typ":"JWT"}   {"sub":"aditi","role":"ADMIN",…}   HMAC-SHA256(secret, header + "." + payload)
```

**The property that makes JWT microservices work: the payload is not encrypted.**
Anyone holding the token can read it — `JwtTokenServiceTest.payloadIsReadable`
proves it by Base64-decoding and asserting the username is visible. So nothing
secret may ever go in a payload. The frontend reads `role` from there *for
convenience only*; the real decision is made server-side.

**How verification works.** The server recomputes the signature over the first
two segments with its own copy of the secret. If the payload was altered, the
recomputed signature no longer matches. Combined with the `exp` claim, the token
is tamper-evident and self-expiring, **with no server-side session to look up** —
which is exactly why any of the five instances can validate any request, and why
the system scales horizontally.

**Why stateless matters, specifically here.** `SecurityConfig` sets
`SessionCreationPolicy.STATELESS`. Without it, a login creates a session cookie
meaningful only on that one instance — and users are load-balanced across
several. That is the single most important property in a horizontally scaled
microservice system.

**Flow**

```
POST /auth/login  { username, password }
  → AuthController.login → AuthService.login
     → UserRepository.findByUsername          (port → JPA)
     → PasswordHasher.matches(raw, hash)      (port → BCrypt, constant-time)
     → TokenService.issue(user)               (port → JJWT, HS256)
  ← 200 { token, tokenType:"Bearer", expiresInMs, username, role }

frontend: localStorage.setItem('token', …)
  → axios REQUEST interceptor adds `Authorization: Bearer …`
  → gateway JwtAuthGlobalFilter verifies → adds X-User-Name / X-User-Role
```

**Authorisation matrix — `app.security` in `api-gateway/src/main/resources/application.yml`**

| Path | Method | Requirement |
|---|---|---|
| `/auth/login`, `/auth/register` | any | public |
| `/products` | GET | public (browsable signed-out) |
| `/orders/**`, `/products/**` (writes) | any | any valid token |
| `/products/images/**`, `/products/with-image`, `/products/*/image` | any | **ADMIN** |
| anything else matching a rule | any | 401/403 |

Read the file — the rule engine is in
`GatewaySecurityProperties` and it evaluates **top to bottom, first match wins**,
like Spring Security's matcher chain. A broad rule above a narrow one silently
swallows it.

**Why 401 vs 403, stated precisely**

- **401 Unauthorized** — I do not know who you are. Missing, malformed, or
  expired token. Actionable: get a new token.
- **403 Forbidden** — I know exactly who you are and you still may not do this.
  Retrying will never help.

`JwtAuthGlobalFilterTest.customerCannotReachAdminPath` asserts 403, not 401,
precisely for this reason.

**The role-escalation bug the gateway closes.** Before this change,
`AdminPanel.jsx` gated itself with `localStorage.getItem('role') === 'ADMIN'`. That
is a UI hint, not a control — anyone can open devtools and set it. Now the
mutating endpoints are ADMIN-only **server-side**, and the React check is a
convenience. This is a great answer if asked *"what security issue did you find
and fix?"*

**JWT vs session cookies, honestly**

| | JWT in localStorage | httpOnly cookie |
|---|---|---|
| CSRF | Not vulnerable (the header is not sent automatically) | Vulnerable — needs protection |
| XSS | **Vulnerable** — any script on the page can read it | Not vulnerable — JS cannot read it |
| Revocation | Hard — valid until `exp` | Easy — delete the session |
| Server state | None | Full session store |

The honest answer: *this project uses localStorage because it is a demo. For
anything handling real money, the token belongs in an httpOnly, Secure,
SameSite cookie with a refresh-token rotation flow, and short-lived access
tokens.* The gateway's `csrf(...disable)` is only safe *because* it uses a
stateless Bearer token — say that explicitly, because disabling CSRF without
saying why is the most common security mistake in this stack.

**HS256 vs RS256** — if they ask why symmetric:

- HS256: one shared secret signs *and* verifies. Simple, fast. Every verifier
  needs the signing key, so revoking a compromised key means rotating it
  everywhere.
- RS256 (asymmetric): a private key signs, a public key verifies. Anyone can
  verify without being able to mint tokens — the shape OAuth/JWT access tokens
  use, and what you want if third parties ever validate your tokens.

This project uses HS256 because all five services are under one trust boundary.

**Other answers worth having ready**

- *`exp`* expiry (24 h here) — why not forever? A leaked token would be valid
  forever. *`iat`* issued-at. *`iss`* issuer — checked here with
  `requireIssuer`, so a token minted by a *different* system sharing the secret
  is rejected (`JwtTokenServiceTest.rejectsWrongIssuer`).
- *What about logout?* A stateless token cannot be revoked. Options: a short
  expiry, a token-version claim checked against the database, or a denylist of
  revoked JTIs.
- *Refresh tokens?* A long-lived refresh token stored in an httpOnly cookie,
  exchanged for a short-lived access token. Never store the refresh token in
  localStorage.
- *Where is the secret?* An environment variable (`JWT_SECRET`), never in git.
  `tools/java-lint.mjs` plus the `structure` CI job fail the build if
  `JWT_SECRET=…` appears with a real-looking value.

---

### 3.5 Image Upload

**Endpoint** — `POST /products/images`, `consumes = multipart/form-data`.

**What multipart actually is.** A body of named *parts*, each with its own
`Content-Disposition` and `Content-Type`:

```
------boundary123
Content-Disposition: form-data; name="file"; filename="phone.png"
Content-Type: image/png

<binary bytes>
------boundary123--
```

The boundary is generated by the browser. Spring maps a part to `MultipartFile`.

**The three endpoints**

| Endpoint | Purpose |
|---|---|
| `POST /products/images` | upload one image, get a URL back |
| `POST /products/with-image` | upload **and** create the product — one request |
| `POST /products/{id}/image` | upload and attach to an existing product |

**The frontend path** — `frontend/src/api/imageApi.js`:
validate → `new FormData()` → `form.append('file', file)` → POST.
`createProductWithImage` appends a second part:
`new Blob([JSON.stringify(product)], { type: 'application/json' })` — because
FormData only carries text or files, and `@RequestPart("product")` needs a part
Spring will parse as JSON.

**Three things that break image upload, all handled here**

1. **Do not set `Content-Type` manually.** The request interceptor deletes it
   for `FormData` (`axiosInstance.js`). A hand-written
   `Content-Type: multipart/form-data` without the boundary is unparseable, and
   `application/json` makes the server see a corrupt file. The browser must set
   it.
2. **Never trust the client's filename.** `ImageService` derives the extension
   from the declared *content type* and generates the key as
   `products/<uuid>.<ext>`. A file called `evil.html` or `../../etc/passwd` is
   stored as a random `.png`. `ImageServiceTest.ignoresClientFilenameForExtension`
   asserts it. This is the classic upload vulnerability.
3. **Whitelist content types, never blacklist.** `ImageService` allows
   `image/jpeg|png|webp|gif|avif`. SVG is **excluded on purpose** — it is XML
   that can carry inline script, so serving one from your own origin is stored
   XSS. A blacklist is always one extension behind an attacker.

**Size limits — two of them, and why**

- `app.upload.max-size-bytes: 5 MB` — the business rule, producing a friendly
  413.
- `spring.servlet.multipart.max-file-size: 6 MB` — the container's hard backstop.

The container limit is deliberately *larger*. If it fired first, Tomcat would
abort the request before any of our code ran and the user would get a raw error
with no explanation. As configured, a 5.5 MB file is rejected by our code with
"use the /products/images endpoint" — and a 50 MB file is still stopped.

**Validation order in `ImageService.validate`**

1. empty → `EmptyUploadException` → 400
2. too large → `ImageTooLargeException` → 413
3. type not whitelisted → `UnsupportedImageTypeException` → 415

Types are normalised first (`split(";")[0]`, lowercased) because some proxies
send `image/png; charset=binary` and some clients uppercase.

**The delete path.** `ProductService.deleteProduct` removes the **row first**,
then the image. If the image cleanup failed first and the transaction rolled
back, the product would point at a file that no longer exists. Removing the row
first means a failed cleanup leaves an orphaned object in the bucket —
invisible, and reclaimable with an S3 lifecycle rule. A product with a missing
image is visible to every customer. The cleanup is also best-effort, so an S3
outage does not make the catalogue undeletable
(`ProductServiceTest.deleteSurvivesStorageOutage`).

An externally hosted `imageUrl` is ignored on delete —
`FileStorage.keyFromUrl` returns `null` when the URL was not produced by us, so
we never issue a delete for a key we never wrote.

---

### 3.6 AWS S3

**What S3 is.** A flat key/value object store over HTTP. There is no directory
tree — `products/8f2c/phone.png` is one key containing slashes, which is why the
console renders folders. An upload is one `PUT` of raw bytes.

**Implementation** — `product-service/…/infrastructure/storage/S3FileStorage.java`
(behind the `FileStorage` port). Dependencies from the AWS SDK v2 BOM, so `s3`,
auth, regions and the presigner can never drift out of version.

**Why the SDK instead of a hand-rolled PUT.** SigV4 request signing is a
multi-step canonicalisation algorithm — sign the headers, build a string to
sign, HMAC it four times, add the signature. The SDK also gives automatic
retries with backoff and jitter, connection pooling, and endpoint resolution.
Hand-rolling it is code that works until AWS changes something.

**Credentials — the priority chain**

1. `DefaultCredentialsProvider` when `AWS_ACCESS_KEY_ID` is unset. It walks env
   vars → shared profile → ECS task role → EC2 instance metadata. **Use this in
   production** — no long-lived key to leak or rotate.
2. `StaticCredentialsProvider` from config, only for local development against a
   real bucket.

A long-lived access key in a config file is a credential waiting to be leaked;
an instance role has a lifetime managed by the platform.

**The S3 bucket policy** (not in code, and that is the point):

```json
{
  "Effect": "Allow", "Principal": "*", "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::shopease-product-images/*"
}
```

Public **read** on objects, no public **write**, and — this is the detail worth
saying — the `Principal: "*"` is safe *only because* there is no
`PutObject`/`DeleteObject` in the statement. A wildcard principal on write would
let anyone overwrite your images. Also: modern S3 blocks public ACLs by default
and disables public access blocking per account, so this policy works only once
that block is turned off deliberately.

**Presigned URLs — the production path, and the best talking point in this
section.**

`POST /products/images/presign` returns a URL the browser `PUT`s **directly** to
S3. The service only signs a ticket.

Why it matters: routing a 5 MB image through the API means 5 MB up through the
edge, 5 MB into the JVM heap as a byte array, then 5 MB back down to S3 — on a
0.1-CPU instance, a request that occupies a worker thread for seconds. With a
presigned URL the browser uploads straight to S3, so the service stays small, the
upload runs at the user's connection speed, and the API never holds image bytes.

What the signature does: SigV4 signs the request *and* a validity window
(default 15 min). S3 recomputes it with the caller's credentials and rejects a
mismatch or a late request. No secret reaches the browser — **possessing the
URL is the entire authorisation**, which is why a presigned URL must never be
logged.

The catch, and it is worth saying: the `Content-Type` sent with the `PUT` must
match what was signed, or S3 answers `SignatureDoesNotMatch`. That is why the
response includes `requiredHeaders`.

**The `local` implementation.** `LocalFileStorage` writes to `./uploads` and
`WebMvcConfigurer` serves it at `/uploads/**`. It is a **development** backend:
a container filesystem is ephemeral, so every deploy and every scale-up starts
clean. "Containers are disposable, state lives outside" is the foundation of the
entire twelve-factor argument — this class is the proof.

**The ImageStore API in one table**

| Operation | HTTP | Notes |
|---|---|---|
| `store(key, upload)` | `PUT` | content type, size and cache-control set |
| `delete(key)` | `DELETE` | best-effort; a missing key is not an error |
| `urlFor(key)` | — | base URL + key |
| `presignPutUrl(...)` | — | S3 only |

---

### 3.7 Logger

**Files.** `logback-spring.xml` in every service, plus:
`infrastructure/logging/CorrelationIdFilter.java` and `RequestLoggingFilter.java`
(gateway equivalents are `GlobalFilter`s, because the gateway is reactive).

**The pattern, and what each field buys you**

```
product-service 14:22:07.481  INFO [http-nio-8081-exec-3] [corr=7f3c1a2e-…] c.a.p.i.persistence.ProductRepositoryImpl - findAll
```

| Field | Why |
|---|---|
| service name | one aggregated log stream still tells you the source |
| `HH:mm:ss.SSS` | on a 0.1-CPU instance the **gap** between lines is often the finding |
| `%-5level` | padded so `grep ERROR` is readable |
| `[thread]` | Tomcat serves ~200 requests on ~200 threads; without it you cannot tell a slow request from a slow query |
| `[corr=…]` | the MDC value — one `grep` rebuilds the whole call chain |
| `%logger{40}` | abbreviated package path |

**Levels as a debugging ladder.** `ERROR` something broke · `WARN` unexpected
but handled (a 4xx, a failed token) · `INFO` business events (order placed) ·
`DEBUG` developer detail · `TRACE` very fine-grained. In `logback-spring.xml`,
Spring Data is pinned to `WARN` because at DEBUG it prints every SQL statement —
thousands of lines nobody reads.

**Why console-only on Render.** Render captures a container's stdout/stderr and
shows it in the Logs tab, so on Render the console appender *is* the log file. A
size-and-time rolling file appender is included but disabled, for running
somewhere with a real filesystem (a VM, Kubernetes).

**MDC — the mechanism behind the correlation id.** A thread-local map that
logging frameworks expose to the pattern as `%X{correlationId}`. It adds context
to every line without threading a parameter through every method.

It is thread-local, which is *why* it must be cleared in a `finally` block:
servlet containers reuse threads, so a leaked value would silently label an
unrelated later request. The gateway's reactive version uses
`Mono.doFinally(...)` for the same reason.

**Who mints the id.** The **gateway**, and only the gateway — it is the first
thing every request touches, whether from a browser or another service, so one
id per inbound request is guaranteed. The servlet filter reuses an inbound
`X-Correlation-Id` if present, else generates one.

**Log injection is a real vulnerability.** An inbound id is attacker-controlled,
so the gateway filters it to `[A-Za-z0-9_-]` and 64 characters max
(`CorrelationIdGlobalFilter.sanitise`). Without that, a client could inject
newlines and forge log entries, or paste megabytes into every line of five
services' logs.

**What is deliberately never logged:** request bodies (passwords, card numbers)
and `Authorization` headers (the token itself). `RequestLoggingFilter` logs
method, path, status, duration and remote address — and nothing else. The
`feign.Logger.Level` is `BASIC`, never `FULL`, because `FULL` prints headers
including the forwarded token and bodies containing password hashes.

**How you actually use it in an incident**

1. User reports "checkout is slow", quotes the `X-Correlation-Id` from the
   response header.
2. `grep 7f3c1a2e` in the gateway log → 2.4 s, route `/orders/**`.
3. `grep 7f3c1a2e` in the order log → 2.3 s, so it is us.
4. `grep 7f3c1a2e` in the product log → 2.3 s on `findById`, with
   `hibernate.SQL` at DEBUG showing a full table scan on `products`.
5. Fix: add the missing index. One trace, five services, no guesswork.

---

### 3.8 React Interceptors

**File.** `frontend/src/api/axiosInstance.js`. **Docs.** `frontend/src/api/authApi.js`.

**What an interceptor is.** A function that runs on every request or every
response of an axios instance.

```js
axios.interceptors.request.use(onSuccess, onError)   // before sending
axios.interceptors.response.use(onSuccess, onError)  // after
```

Interceptors run in **registration order** for requests and in **reverse
registration order** for responses.

**Why they exist.** Without them, each of the 8 components calling the API must
remember to attach a token and handle a 401. That is duplicated logic, and
duplicated logic drifts: someone adds a call in a new component, forgets the
token, and the bug appears only for logged-in users on that one screen.

**What the request interceptor does here**

1. attach `Authorization: Bearer <token>`;
2. attach `X-Request-Id` for correlation;
3. **delete `Content-Type` for `FormData`** — the browser must set it so it can
   include the multipart boundary;
4. increment an in-flight counter so a global spinner is possible
   (`subscribeToPendingRequests`).

**What the response interceptor does here**

1. **unwrap `response.data`** so callers write `products` not `res.data`. A
   decision like this must be made once, in one place — mixing the two
   conventions is the fastest way to ship a bug. (Every call site was updated:
   `App.jsx`, `Home.jsx`, `AdminPanel.jsx`.)
2. **401** → sign out and redirect to `/login`.
3. **403** → show a message, **do not sign out**. The user is still logged in;
   bouncing them to `/login` for a permission error is wrong, and the admin
   panel depends on this distinction.
4. **409 / 413 / 415** → pass the backend's human-readable message through.
5. **404** → log method + URL + correlation id, since it usually means a
   frontend/backend version mismatch.
6. **5xx / network error** → a friendly message, the full detail to the console.
7. **decrement the in-flight counter in both branches** — the `onRejected` path
   is the one people forget, and the spinner then sticks forever.

**Three details that are easy to get wrong**

- *The redirect loop.* A 401 while already on `/login` would `navigate` to
  `/login` again, forever. `signOut` checks the current path first, and skips the
  call for `/auth/login` and `/auth/register` — failing to log in is a 401 by
  design, and redirecting would wipe the error message the user needs to read.
- *A rejected interceptor must return `Promise.reject`.* Returning the `error`
  value instead is swallowed and the caller hangs.
- *`localStorage` can throw.* Private browsing and blocked storage make
  `getItem` throw, so `getToken()` wraps it in try/catch and returns `null` —
  an exception escaping into a render is a blank page.

**Why there are two instances.** `authApi` is a separate instance on purpose: a
failed login is a 401, and routing it through the shared instance would clear
storage and redirect — wiping the page at the exact moment the user submitted
the form. The general rule: put **global** side effects in a shared instance, and
keep anything that would be *wrong* for some calls in its own instance. Two small
named instances beat one full of `if (url !== '/auth/login')`.

**`decodeToken.js` and its limits.** It Base64-decodes the payload for the UI to
show a name and an `isAdmin` flag. It is **not** a security control: it does not
verify the signature, and anyone can edit the payload. The real check is the
gateway's. Say this before they say it.

---

### 3.9 PostgreSQL / SQL Server

**PostgreSQL (default).** Three logical databases on one Render instance:
`productdb`, `orderdb`, `authdb`. One instance hosting several databases is far
cheaper; a dedicated instance per database is the production choice because it
isolates blast radius and scales independently.

**Connection pooling — HikariCP.** Datasource → Hikari pool → JDBC connection.
Pooling exists because opening a PostgreSQL connection is a TCP handshake **plus
a TLS handshake plus auth** — tens to hundreds of milliseconds — and doing that
per request is ruinous.

The pool is deliberately small here:

```yaml
hikari:
  maximum-pool-size: 5      # a 0.1-CPU instance: each connection is a TLS handshake
  minimum-idle: 1           # don't eagerly open ten connections at boot
  connection-timeout: 10000  # fail fast instead of pinning a request thread
```

Sizing rule of thumb: **connections ≈ cores × 2 + effective_spindle_count**.
On a small instance, more connections than that make throughput *worse*, because
they contend rather than queue.

**JPA / Hibernate.** `@Entity` maps a class to a table; `ProductJpaRepository`
extends `JpaRepository` and Spring Data generates the SQL from the method name
(`findByCategoryIgnoreCase`). `ddl-auto: update` is convenient for a demo and
**wrong for production** — it never drops or renames a column and offers no
rollback. The production answer is `validate` plus Flyway or Liquibase
migrations.

`open-in-view: false` is set, which closes the default behaviour of keeping a
database connection open for the whole HTTP request. That default causes
connection-pool exhaustion under load and is worth naming as a known
Spring Boot performance trap.

**SQL Server — the profile.** Each service ships
`src/main/resources/application-sqlserver.yml`. Run the **same jar**:

```bash
java -jar app.jar --spring.profiles.active=sqlserver
```

**Zero Java files changed.** That is the payoff of the repository port: the
domain and application layers know nothing about SQL, so moving to another
RDBMS is configuration.

```yaml
url: jdbc:sqlserver://localhost:1433;databaseName=productdb;encrypt=true;trustServerCertificate=true
driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
database-platform: org.hibernate.dialect.SQLServerDialect
```

Note the SQL Server URL uses **semicolons** for parameters, not `?`.

**The real differences — this is what the follow-up question is actually about**

| Area | PostgreSQL | SQL Server | Gotcha |
|---|---|---|---|
| Identity | `IDENTITY` / `BIGSERIAL` | `IDENTITY(1,1)` | none — `@GeneratedValue(IDENTITY)` covers both |
| Strings | `VARCHAR` | `NVARCHAR` | SQL Server defaults to `NVARCHAR(255)` and **silently truncates** longer values unless `@Column(length=…)` is set. This project sets lengths everywhere. |
| Boolean | real `BOOLEAN` | `BIT` | `WHERE active = true` works on both; `= 'true'` does not |
| **Collation** | case-**sensitive** | case-**insensitive** by default | `findByUsername("aditi")` matches `Aditi` on SQL Server. A genuine "worked in dev, failed in prod" bug. |
| Paging | `LIMIT n OFFSET m` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` | irrelevant with Spring Data; bites the first hand-written `@Query` |
| Migrations | one Flyway path | a separate one | `ddl-auto` is not a migration strategy |

**Where JPA stops being portable.** Hibernate abstracts the *CRUD* and mapping
well. It does **not** abstract hand-written JPQL/SQL, native queries, or
dialect-specific DDL. The moment you write `@Query(nativeQuery = true)` you have
left the abstraction — which is exactly why this project has no such query.

**Try it locally**

```bash
docker run -d --name sqlserver \
  -e 'ACCEPT_EULA=Y' -e 'MSSQL_SA_PASSWORD=Your_strong@Passw0rd' \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

Then `SPRING_DATASOURCE_URL=… SPRING_PROFILES_ACTIVE=sqlserver java -jar app.jar`.

---

### 3.10 Vercel (UI)

**What it is.** A static host + CDN + build service for frontends. It detects
Vite, runs `npm run build`, and serves `dist/` from an edge network.

**Why the frontend is on Vercel and the backend is not.** The frontend is a
**static bundle** — there is no server process to keep alive, and the CDN serves
it from a data centre near the user. A Spring Boot service is the opposite: a
long-running JVM that needs memory, a filesystem and a database connection.
Vercel would have to boot that JVM on every request. Trying to host a Spring
service on a static host means rebuilding the backend per request — which is why
nobody does it.

**`vercel.json` — the two things that matter**

1. **The SPA rewrite.** Without it, refreshing `/admin` gives a 404, because
   the browser asks Vercel for a file at that path. The rewrite serves
   `index.html` for any path that is not a real file, and React Router resolves
   it client-side.

   ```json
   "rewrites": [{ "source": "/((?!api/).*)", "destination": "/index.html" }]
   ```

   It is a **rewrite, not a redirect** — the URL does not change, which is what
   keeps the back button and relative links working.

2. **Cache headers.** Vite fingerprints filenames (`index-a1b2c3.js`), so
   `/assets/*` can be cached for a year with `immutable`. `index.html` must be
   `no-cache`, or a deploy would never reach anyone who already visited — the
   classic stale-frontend bug.

Also in that file: `X-Content-Type-Options`, `X-Frame-Options: DENY`,
`Referrer-Policy`, `Permissions-Policy`.

**The one Vite fact that bites everyone.** `import.meta.env.VITE_*` is inlined at
**build** time, not read at runtime. Changing `VITE_API_URL` requires a
**rebuild**, not a restart. This is why `deploy-frontend.yml` passes it as a
build `env:` in the workflow.

**CORS and the `/api` option.** Two valid setups:

- **Direct** (shipped here): `VITE_API_URL=https://gateway.onrender.com`. The
  gateway's `allowedOriginPatterns` must include the Vercel origin — which
  supports `https://*.vercel.app`, so every preview deployment works
  automatically.
- **Same-origin proxy**: `VITE_API_URL=/api` plus a Vercel rewrite forwarding
  `/api/*` to the gateway. The browser then makes a same-origin request, so
  **CORS disappears entirely** — no preflight, no `Allow-Origin`, no risk of the
  two lists drifting apart. The cost is an extra network hop.

**Preview deployments.** Every push to a non-main branch gets its own URL, so a
PR can be reviewed as a working site. That is the strongest argument for CI
deploying the frontend on pull requests.

**Environment variables in CI.** `VERCEL_TOKEN` (account access),
`VERCEL_ORG_ID` and `VERCEL_PROJECT_ID` (which project). The workflow uses the
Vercel **CLI** rather than a marketplace action: no third-party action runs with
your token, and the CLI is versioned by npm rather than by a git tag.

---

### 3.11 Render (Backend)

**What it is.** A PaaS for long-running containerised services. Each service is
a **Docker web service** whose Root Directory is the service folder; Render
builds the Dockerfile and runs the container.

**Why Docker rather than Render's native Java build.** A Dockerfile pins the
JDK, the base image and the JVM flags in version control. That matters here
because the free tier is **0.1 CPU / 512 MB**, where these flags are the
difference between a 90-second and a 3-minute cold start:

| Flag | Why |
|---|---|
| `-XX:TieredStopAtLevel=1` | C1-only JIT: far less bytecode to compile at boot |
| `-XX:+UseSerialGC` | cheapest collector for a single CPU and a small heap |
| `-XX:MaxRAMPercentage=50` | heap ≤ 256 MB, leaving headroom so we are never OOM-killed |
| `-Djava.security.egd=file:/dev/./urandom` | never block on entropy |

The Dockerfile is also two-stage: Maven builds, and the runtime image is
`eclipse-temurin:17-jre-alpine` (~100 MB smaller, so the image pull is faster).

**`render.yaml` — the infrastructure as code.** The whole backend is declared in
one reviewed file: services, their plans, their health-check paths, and their
environment variables. `generateSecret: true` makes Render mint `JWT_SECRET` and
keep it in its secret store — **never in git**. `fromDatabase` wires the DB
password; `fromService` wires service URLs, so a hostname is never copy-pasted.

**`server.port: ${PORT:8081}`** — Render injects `PORT` (10000 by default).
Binding to it directly means Render routes traffic the moment Tomcat is up,
instead of waiting for its port scanner to find the process.

**The free-tier reality, and what to do about it.** A free workspace gets
**750 instance-hours/month**. A service that never sleeps uses ~720 — so exactly
one service can stay warm around the clock. Everything else spins down after 15
idle minutes and takes **1–3 minutes** to return on a 0.1-CPU instance, printing
nothing for the first part of that, which makes the log tab look empty while it
is in fact booting.

Three options, in order of value:

1. **Put the gateway and product-service on the Starter plan.** They are on the
   critical path of every page load, so this removes the cold start from the
   user's experience. The highest-value single change available.
2. **Ping to keep warm** (UptimeRobot / cron-job.org every ~10 min) — but that
   burns the quota fast, so roughly one service can be kept alive around the
   clock before Render suspends the workspace.
3. **Accept it and design the UI for it.** `Home.jsx` already retries and shows
   a "waking up" hint — a cold start is a UX state, not just an infrastructure
   fact.

**Health check paths.** `product-service` uses `/health`, a deliberately cheap
liveness endpoint that touches no database, so the check passes the instant
Tomcat is up. The other services use `/actuator/health`.

**Liveness vs readiness** — a distinction worth stating because mixing them up
causes outages:

- **Liveness** (`/health`) — "is the JVM alive?" If it fails, the platform
  should **restart** the container. It must **not** depend on the database, or a
  brief database outage would restart every healthy service in the fleet.
- **Readiness** (`/actuator/health/readiness`) — "can this instance serve
  traffic?" If it fails, the load balancer should stop routing to **this**
  instance. This one legitimately checks the database.

**`eureka.instance.lease-expiration-duration-in-seconds: 90`** — an instance that
stops renewing is evicted after 90 s, so a crashed instance leaves the registry
within ~90 s. There is a real trade-off here: shortening it makes discovery
faster but risks evicting healthy instances on a brief network blip, and
`enable-self-preservation` exists to trade staleness for availability during a
mass timeout.

---

### 3.12 GitHub Actions

**Files.** `.github/workflows/ci.yml`, `deploy-frontend.yml`,
`docker-publish.yml`.

**What GitHub Actions is.** CI/CD as code. Workflows are YAML in
`.github/workflows/`; each workflow is a graph of **jobs**, each job a sequence of
**steps** on a runner. A red X blocks the merge, so a broken service can never
reach `main` and therefore can never be deployed.

**Anatomy**

```yaml
on:
  push: { branches: [main] }
  pull_request: { branches: [main] }
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true      # a new push cancels the old run
permissions:
  contents: read                 # least privilege by default
```

**The pipeline**

```
backend   (matrix: 5 services, parallel) ─┐
frontend  (npm ci && npm run build)       ─┼─> all green ─> docker
structure (onion rule + secret scan)      ─┘
```

**Six decisions worth being able to justify**

1. **A matrix, not five duplicated workflows.** `matrix.service` runs the same
   steps five times. Add a service to the list and it is built and tested with no
   new YAML. This is the microservices payoff made concrete: the services have no
   parent aggregator POM, so they are genuinely independent and CI runs them in
   parallel.
2. **`fail-fast: false`.** One broken service must not cancel the other four —
   you want the full picture in one run, not "rerun to find the other errors".
3. **`mvn verify`, not `mvn test`.** `verify` also packages the jar, so a
   packaging failure is caught here rather than during a deploy.
4. **`npm ci`, not `npm install`.** `ci` installs exactly what
   `package-lock.json` pins and **fails if the lock file is out of sync** with
   `package.json`. That is the difference between a reproducible build and
   "works until someone publishes a patch".
5. **Caching keyed on `hashFiles('**/pom.xml')`.** Changing a dependency
   invalidates the cache; changing a `.java` file does not. Without it every run
   re-downloads ~200 MB.
6. **`chmod +x ./mvnw`.** The wrapper ships without the exec bit, so `mvnw
   verify` fails with `EACCES` on a fresh checkout. A real bug this pipeline hit.

**The `structure` job — the one that is genuinely unusual**

- Runs `node tools/java-lint.mjs .`, which fails if a domain package imports
  Spring/JPA/Feign/AWS, or the application layer imports infrastructure. *The
  dependency rule is only worth something if it is checked.*
- Greps for committed credentials. A committed `DB_PASSWORD` or `JWT_SECRET` is
  compromised the moment it is pushed, **even if the commit is later deleted** —
  git history keeps it. The fix is to **rotate the credential**, not to remove
  the line.

**Action pinning.** `actions/checkout@v4`,
`actions/setup-java@v4`, `actions/cache@v4`,
`actions/setup-node@v4`, `actions/upload-artifact@v4`,
`docker/build-push-action@v6`. Pinning a **major** tag is the usual compromise:
`@v4` is auditable and stays current, whereas a full commit SHA is immutable but
needs manual Dependabot bumps, and `@master` is unreviewable code with write
access to your repository.

**`GITHUB_TOKEN` for GHCR.** `docker-publish.yml` logs in with the automatic,
repo-scoped `GITHUB_TOKEN`. There is no long-lived registry password to store or
rotate — the same reasoning as preferring an AWS instance role over an access
key.

**The CI/CD split, and why the publish is separate.** CI proves the images build
(and fails fast on a PR). Publishing happens only for something already trusted,
gated on a tag. Conflating them means a broken main branch can push images that
something already deployed.

---

## 4. Honest answers to "what would you improve?"

Interviewers ask this to see whether you know where your own work is weak.
Volunteering specifics beats being asked.

| # | Limitation | Fix | Why it is the honest answer |
|---|---|---|---|
| 1 | **Services are publicly reachable on Render.** Anyone can bypass the gateway and call product-service directly, skipping JWT verification. | Put services on Render **private services** (only reachable from inside the workspace), or add the same verification filter inside each service. | It is a genuine hole, and I have already scoped both fixes. |
| 2 | `ddl-auto: update` | Flyway migrations + `validate` | `update` never drops or renames a column and has no rollback. |
| 3 | Token in `localStorage` | httpOnly cookie + rotating refresh tokens | Any XSS becomes a credential theft. |
| 4 | No refresh tokens, 24 h tokens, no revocation | Short access token + refresh rotation, or a token-version claim | A stateless token is valid until `exp`, full stop. |
| 5 | `Double` for money | `BigDecimal` + `NUMERIC(19,4)` | `Double` cannot represent 0.10 exactly. The domain rounds to 2 dp, which mitigates but does not fix it. |
| 6 | No rate limiting on `/auth/login` | Gateway filter (Redis token bucket) | Credential stuffing is the first thing any attacker tries. |
| 7 | No circuit breaker on the Feign call | Resilience4j | One slow product-service call currently pins an order-service thread for up to 90 s. |
| 8 | Images are served from S3 with no CDN or resize | CloudFront + Lambda resize | Original-size images on the critical path. |
| 9 | No distributed tracing | OpenTelemetry | Correlation ids trace one request; spans would show timing *within* a service. |
| 10 | `show-sql: true` was on | left at `false` by default | Password hashes and emails in logs is a data leak. |
| 11 | Local storage is not durable | already handled — `STORAGE_TYPE=s3` | The abstraction already made this a config change. |
| 12 | Single Eureka | peer cluster of 3 | A standalone registry is a single point of failure. |

---

## 5. Questions they will ask

### "Walk me through the architecture."

Use §2. Draw the boxes. Name the ports at each boundary.

### "Why microservices? Why not a monolith?"

Not "microservices are better" — give the actual trade:

> A monolith is the right default. I split this one because it is a learning
> project and the boundaries are genuinely separable: products, orders and auth
> have different data and different change rates. The costs are real — network
> calls instead of a join, more deployment surface, distributed tracing, harder
> local debugging. I would not split a system just because it is "big"; I would
> split it where the boundaries are real.

### "How do you handle a failure in one service?"

`ProductUnavailableException` → **502**, not 404. The distinction matters: 404
says "this will never work", 502 says "retry later may work". The Feign adapter
catches `NotFound` separately from other `FeignException`s, because treating a
5xx as "not found" would let the service accept orders for products that exist.
There is **no automatic retry** — a naive retry of a POST after a timeout can
create the order twice, which is why retries need an idempotency key.

### "How would you debug a slow request?"

Follow the correlation id across all five logs (§3.7, step 5). Then: the gateway
timing is end-to-end, each service's request line is the time *including* its
database and its downstream calls, so subtracting tells you which hop is slow.
The thread name in the log tells you whether it is a slow query or thread
contention.

### "How do you scale it?"

- **Stateless everywhere** (`SessionCreationPolicy.STATELESS`) → add instances
  behind the load balancer.
- **Gateway is reactive** → many concurrent connections per thread.
- **Horizontal, per service** → product-service and order-service scale
  independently; orders being slower does not need more catalogue capacity.
- **Vertically** → the connection pool is currently sized for 0.1 CPU; on a real
  instance, `cores × 2 + spindles` and a real pool.
- **Caching** → products change rarely, so a Redis cache in front of
  `findById` is the obvious next step, plus a CDN for images.

### "What is your weakest part?"

Pick one and show the fix. Never "I have none."

### "If the JWT secret leaked?"

1. Rotate `JWT_SECRET` on auth-service **and** the gateway (they are symmetric —
   both must change together).
2. Redeploy both.
3. Every token signed with the old secret is now invalid — which is the *good*
   news: the exposure window closes instantly rather than waiting for `exp`.
4. Then reduce the blast radius: shorter access-token lifetime, `RS256` so
   verifiers never hold the signing key, and rate limiting on `/auth/login`.

---

## 6. Trap answers

Things that sound right and are wrong. Each of these has been asked in a real
interview.

| ❌ Wrong | ✅ Right |
|---|---|
| "JWTs are encrypted so the payload is safe." | The payload is **Base64, not encrypted** — anyone can read it. Never put a secret in one. `JwtTokenServiceTest.payloadIsReadable` proves it. |
| "Spring `@Service` + `@Transactional` in the use case is fine." | Defensible, because the class stays free of framework *types*, and the test is that it still compiles and runs with every Spring annotation deleted. Say the test, not the excuse. |
| "I disabled CSRF because we use JWT." | Safe **because** the token is a header, which a browser never sends automatically. The moment you move to a cookie, CSRF must come back. |
| "The admin panel checks `role === 'ADMIN'` so it is secure." | That is a UI hint, editable in devtools. The gateway enforces ADMIN server-side; the React check is a convenience. |
| "A 403 means re-authenticate." | 401 means re-authenticate. 403 means you are known and still not allowed — retrying will never help. |
| "We retry the Feign call if it fails." | Retries without an idempotency key can create an order twice. The default here is `Retryer.NEVER_RETRY` deliberately. |
| "N+1 queries are fine at this scale." | They are the classic cause of "the endpoint got slow after we added a field". Use a join fetch or a projection. |
| "`ddl-auto: update` is fine for a demo and later we will switch to Flyway." | True but weak. Name the concrete cost: it never drops or renames a column, and has no rollback. |
| "Microservices let us deploy independently." | Only if there is no shared database and no shared code. Otherwise you have a distributed monolith: all the operational cost, none of the benefit. |
| "The gateway caches responses." | It does not, unless a cache filter is configured. Do not claim caching you have not built. |
| "We use SQL Server." | You support **either** Postgres or SQL Server from the same jar, via a profile. That is a stronger claim and it is true. |
| "The token is stored in localStorage." | Accurate — then add that this is demo-only and the production answer is an httpOnly cookie, with the XSS reason. |
| "`@Autowired` is dependency injection." | It is, but field injection specifically. Constructor injection is the form you want, and you can say why in one line. |
| "Correlation IDs are tracing." | They correlate. Tracing (spans, parent-child timing within a service) is OpenTelemetry, which this project does not have. |
| "S3 gives us a folder structure." | S3 is flat. `products/a/b.png` is one key with slashes in it; the console renders folders as a convenience. |
| "We use AWS access keys in production." | No — the default provider chain resolves an instance/task role, so there is no long-lived key in config. |
| "The bucket is public, so anyone can upload." | Public **read** on `GetObject` only. A wildcard principal with `PutObject` would let anyone overwrite your images. |

---

## 7. Code-walk order

If they say "show me the code", go in this order — it tells a story rather than
looking like a folder listing.

1. **`api-gateway/filter/JwtAuthGlobalFilter.java`** — the security story.
   30 seconds on why the edge, then the three rules.
2. **`auth-service/…/domain/port/TokenService.java`** — 10 lines, the whole
   port idea. Then `JwtTokenService` as the implementation.
3. **`auth-service/…/application/service/AuthService.java`** — 60 seconds. Point
   out: no `HttpServletRequest`, no `@Entity`, no `Jwts.builder()`, no `BCrypt`.
4. **`auth-service/…/infrastructure/config/BeanModule.java`** — the composition
   root. Everything wired on one screen.
5. **`product-service/…/domain/port/FileStorage.java`** — the second port, and
   the one that makes S3 swappable.
6. **`product-service/…/infrastructure/config/StorageConfig.java`** — the
   `@ConditionalOnProperty` choice. This is the Dependency Injection answer.
7. **`order-service/…/domain/port/ProductCatalog.java`** then
   **`infrastructure/client/ProductCatalogFeignAdapter.java`** — the microservice
   boundary, and the 404-vs-5xx distinction.
8. **`infrastructure/logging/CorrelationIdFilter.java`** + a `logback-spring.xml`
   pattern line.
9. **`frontend/src/api/axiosInstance.js`** — scroll to the response interceptor.
10. **`.github/workflows/ci.yml`** — the matrix, and the `structure` job.

If you only have time for three: **1, 3, 6**.

---

## 8. Demo script

Four minutes, in this order. Have the URLs open before you start.

**0:00 — the registry**
`https://<eureka>.onrender.com` — five instances registered, heartbeats
arriving. One sentence: discovery is here, but the deployed routes pin URLs
because a sleeping Eureka on the free tier is a 1–3 minute cold start; the
`discovery` profile switches to `lb://`.

**0:30 — trace a request**
Pick any `X-Correlation-Id` from a response, `grep` it in the gateway, order and
product logs. Show the same id in three files. This is the most impressive 30
seconds in the whole project.

**1:15 — image upload**
Open `/admin`, upload a PNG, watch the progress bar, submit, and show the row in
the products list with the S3 URL. Then explain: the browser asks for a
presigned URL and `PUT`s straight to S3, so the API never carries image bytes —
and with `STORAGE_TYPE=local` the same code path writes to `./uploads`, which is
the port doing its job.

**2:15 — the rules are real**
Open devtools, set `localStorage.role = 'ADMIN'`, and try to delete a product.
The **gateway** rejects it with 403, because the React check was never the
control. This is the "found and fixed a real security issue" story.

**3:00 — the build**
Show the GitHub Actions run: five backend jobs in parallel, the frontend build,
the structure job, the Docker build. Mention that the structure job is what keeps
the architecture from decaying.

---

### One last thing

If they ask *"what would you do differently?"*, the strongest answer is to name
the single biggest thing you would change **and why it is a trade, not a
mistake** — for example:

> "The gateway is stateless, which is what makes the whole thing scale — but it
> also means a token cannot be revoked before it expires. If this were handling
> real money I would move to short-lived access tokens plus rotating refresh
> tokens, so 'log out everywhere' actually works."

That shows you understand your own design, not just that you built it.
