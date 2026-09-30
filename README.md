# ShopEase

A microservices e-commerce demo built to be *explained*, not just run.

Five Spring Boot services, a React frontend, three databases, an S3-backed image
pipeline, stateless JWT auth verified at the edge, structured logs with
end-to-end correlation ids, and a CI pipeline that fails the build if the
architecture decays.

> **Interview preparation:** [`docs/INTERVIEW_PREP.md`](docs/INTERVIEW_PREP.md) is
> the main guide — every topic, mapped to the exact file that implements it, with
> Q&A and the wrong answers to avoid.

---

## Contents

- [Architecture](#architecture)
- [Services](#services)
- [Onion Architecture](#onion-architecture)
- [Run it locally](#run-it-locally)
- [Deploy](#deploy)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [Repository layout](#repository-layout)

---

## Architecture

```
                            ┌───────────────────────────┐
   BROWSER ───────────────► │      Vercel  (CDN)        │
   (React 18 + Vite)        │   static SPA, no server  │
                            └─────────────┬─────────────┘
                                          │  Authorization: Bearer <jwt>
                                          ▼
                            ┌───────────────────────────┐
                            │       API GATEWAY         │
                            │  Spring Cloud Gateway     │
                            │  · routing                │
                            │  · CORS                   │
                            │  · JWT verification  ◄── the only place the
                            │  · correlation id     ◄──  secret is trusted
                            │  (WebFlux / Netty)         │
                            └──┬──────────┬──────────┬───┘
              /products/**     │          │          │   /auth/**
                               ▼          │          ▼
                 ┌──────────────────┐   │   ┌──────────────────┐
                 │ product-service  │   │   │  auth-service    │
                 │ · catalogue      │   │   │  · register      │
                 │ · image upload   │   │   │  · login         │
                 │ · S3 / local     │   │   │  · issue JWT     │
                 └────┬─────────┬───┘   │   └────────┬─────────┘
                      │         │       │            │
                 productdb   AWS S3     │        authdb
                             (or local)│
                                        ▼
                              ┌──────────────────┐
                              │  order-service   │
                              │  · order lifecycle│
                              │  · calls product  │
                              │    via a PORT     │
                              └────────┬─────────┘
                                       │
                                   orderdb

                  ┌────────────────────────────────┐
                  │  eureka-server  :8761          │
                  │  service registry / discovery  │
                  │  (dashboard at /)              │
                  └────────────────────────────────┘
```

**Data flow for one click** ("Buy now" → `POST /orders`):

1. React's axios **request interceptor** attaches the Bearer token.
2. The gateway's `CorrelationIdGlobalFilter` mints (or reuses) an id, puts it in
   the MDC, and forwards it.
3. The gateway's `JwtAuthGlobalFilter` **verifies the signature** and applies the
   role rules. An unauthorised request dies here, before any database connection
   or upstream socket.
4. The gateway routes to `order-service`.
5. `OrderService` calls a **domain port** (`ProductCatalog`) — not a Feign
   client. The Feign adapter lives in infrastructure and translates a 404 into
   `Optional.empty()` and a 5xx into `ProductUnavailableException` (502).
6. `order-service` writes to `orderdb`.
7. Every step logs the same correlation id.

---

## Services

| Service | Port | Database | Responsibility |
|---|---:|---|---|
| `eureka-server` | 8761 | — | Service registry and discovery dashboard |
| `api-gateway` | 8080 | — | Routing, CORS, JWT verification, correlation ids |
| `product-service` | 8081 | `productdb` | Catalogue CRUD, image upload, S3 |
| `order-service` | 8082 | `orderdb` | Order lifecycle, product-service calls |
| `auth-service` | 8083 | `authdb` | Registration, login, JWT issuance |
| `frontend` | 5173 | — | React SPA (Vite dev server) |

Each Java service is a standalone Maven project with its own `Dockerfile` and
`.dockerignore`. There is **no aggregator POM** — that is what lets CI build them
in parallel and lets each deploy independently.

---

## Onion Architecture

All three business services use the same four rings. Dependencies point
**inwards only**.

```
        ┌──────────────────────────────────────────────┐
        │  presentation/   controllers · DTOs · errors │   HTTP in, HTTP out
        ├──────────────────────────────────────────────┤
        │  application/    use cases (ProductService…)  │   business orchestration
        ├──────────────────────────────────────────────┤
        │  domain/         Product · PORTS · exceptions│   ← no Spring · no JPA
        ├──────────────────────────────────────────────┤
        │  infrastructure/ JPA · S3 · security · wiring │   everything else
        └──────────────────────────────────────────────┘
```

**Ports** — the domain declares what it needs; the outside supplies it:

| Port | Domain need | Implementations |
|---|---|---|
| `ProductRepository` | "load and save products" | `ProductRepositoryImpl` → JPA |
| `FileStorage` | "put bytes somewhere retrievable" | `S3FileStorage`, `LocalFileStorage` |
| `OrderRepository` | "load and save orders" | `OrderRepositoryImpl` → JPA |
| `ProductCatalog` | "ask the product service" | `ProductCatalogFeignAdapter` → Feign |
| `UserRepository` | "load and save users" | `UserRepositoryImpl` → JPA |
| `PasswordHasher` | "hash and verify a password" | `BCryptPasswordHasher` |
| `TokenService` | "issue and verify a token" | `JwtTokenService` (JJWT) |

**The rule is enforced, not just documented.** `tools/java-lint.mjs` fails the
build if a `domain` package imports Spring, JPA, Feign, the AWS SDK or Jackson;
if an `application` package imports `infrastructure` or `presentation`; or if any
service imports another service's package. It runs as the `structure` job in
CI.

```bash
node tools/java-lint.mjs          # run it locally
```

> This check found a real violation while the project was being built:
> `OrderService` used to import the Feign `ProductClient` directly from
> infrastructure. Introducing the `ProductCatalog` port is what fixed it.

**The payoff, concretely.** `ImageServiceTest` and `OrderServiceTest` run in
milliseconds with no Spring context, no database, no network and no AWS account —
because the collaborators are interfaces. And switching the database from
PostgreSQL to SQL Server changed three YAML files and zero Java files.

---

## Run it locally

### Prerequisites

JDK 17 · Node 20+ · Docker (for PostgreSQL)

### 1. Databases

```bash
docker compose up -d postgres
```

Creates `productdb`, `orderdb` and `authdb` in one local Postgres instance
(`infra/sql/init-databases.sql`).

Optional extras:

```bash
docker compose --profile minio up -d       # S3-compatible store on :9000 / :9001
docker compose --profile sqlserver up -d   # SQL Server on :1433
```

### 2. Environment

```bash
cp .env.example .env
# Generate a real JWT secret — HS256 needs at least 32 bytes:
openssl rand -base64 48
```

### 3. Start the services

In five terminals (or run them in the background):

```bash
cd eureka-server    && ./mvnw spring-boot:run    # :8761
cd api-gateway      && ./mvnw spring-boot:run    # :8080
cd auth-service     && ./mvnw spring-boot:run    # :8083
cd product-service  && ./mvnw spring-boot:run    # :8081
cd order-service    && ./mvnw spring-boot:run    # :8082
```

`chmod +x */mvnw` once if the wrapper is not executable.

### 4. Frontend

```bash
cd frontend
cp .env.local.example .env.local    # VITE_API_URL=/api
npm install
npm run dev                         # :5173
```

`/api` is proxied to `http://localhost:8080` by `vite.config.js`, so the browser
only makes same-origin requests and CORS never comes up in development.

### 5. Check it works

```bash
curl localhost:8761                 # registry: 5 instances registered
curl localhost:8080/health          # gateway up
curl localhost:8080/products        # proxied to product-service
```

Log in at `http://localhost:5173` with **admin / admin123** (created by
`AdminSeeder`) to reach the admin panel and upload images.

### Using real service discovery locally

```bash
# terminal 1
cd eureka-server && ./mvnw spring-boot:run
# terminal 2
cd api-gateway   && ./mvnw spring-boot:run --spring.profiles.active=discovery
```

The gateway then routes via `lb://product-service` instead of fixed URLs.

### Running against SQL Server instead

Same jar, one profile — no code change:

```bash
docker compose --profile sqlserver up -d
SPRING_DATASOURCE_URL='jdbc:sqlserver://localhost:1433;databaseName=productdb;encrypt=true;trustServerCertificate=true' \
DB_USERNAME=sa DB_PASSWORD='Your_strong@Passw0rd' \
./mvnw spring-boot:run --spring.profiles.active=sqlserver
```

### Using S3 instead of local disk

With MinIO running:

```bash
STORAGE_TYPE=s3 S3_BUCKET=product-images \
AWS_REGION=us-east-1 AWS_ACCESS_KEY_ID=minioadmin AWS_SECRET_ACCESS_KEY=minioadmin \
S3_PATH_STYLE_ACCESS=true S3_PUBLIC_BASE_URL=http://localhost:9000/product-images/ \
./mvnw spring-boot:run
```

Leave `AWS_ACCESS_KEY_ID` blank to use the default credential chain (instance
role, task role, `~/.aws/credentials`) — the production path.

---

## Deploy

| Half | Host | Config |
|---|---|---|
| Frontend | **Vercel** | `frontend/vercel.json`, `VITE_API_URL` at build time |
| Backend | **Render** | `render.yaml` (Blueprint) |

### Render

`render.yaml` declares all five services as Docker web services, their plans,
health-check paths, and their environment variables. `generateSecret: true`
makes Render mint `JWT_SECRET`; `fromDatabase` and `fromService` wire passwords
and hostnames, so nothing is copy-pasted.

```bash
git push          # Render auto-deploys
```

Deploy: **Render dashboard → Blueprints → New Blueprint Instance**.

**Secrets to set once in the dashboard:** `CORS_ALLOWED_ORIGINS` (your Vercel
URL), `ADMIN_PASSWORD`, and the `S3_*` values if you use S3.

**Health check paths**

| Service | Path | Why |
|---|---|---|
| `product-service` | `/health` | Cheapest possible: no DB, no registry |
| others | `/actuator/health` | Standard actuator health |

**The free-tier reality.** 750 instance-hours per month; a service that never
sleeps uses ~720, so roughly one service can stay warm around the clock.
Everything else spins down after 15 idle minutes and takes **1–3 minutes** to
return on a 0.1-CPU instance. Putting the **gateway** on the Starter plan is the
highest-value change, because every page load starts there.

### Vercel

Push to `main` and the `deploy-frontend.yml` workflow builds and deploys. Pull
requests get an isolated preview URL.

Required repository secrets: `VERCEL_TOKEN`, `VERCEL_ORG_ID`,
`VERCEL_PROJECT_ID`, `VITE_API_URL`.

Remember `VITE_*` variables are inlined at **build** time — changing one needs a
rebuild, not just a restart.

### GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | push / PR | Builds and tests all 5 services in a parallel matrix, builds the frontend, enforces the architecture rules, scans for committed secrets, then builds all 5 Docker images |
| `deploy-frontend.yml` | push to `main`, PR | Verifies the build, then deploys to Vercel |
| `docker-publish.yml` | tag `v*` | Pushes the images to GitHub Container Registry |

A red X on a pull request blocks the merge, so a broken service can never reach
`main` and therefore can never be deployed.

---

## API reference

All paths are relative to the gateway. `🔓` public · `🔑` any valid token ·
`👑` ADMIN only.

### Auth — `:8083`

| Method | Path | Auth | Body / response |
|---|---|---|---|
| `POST` | `/auth/register` | 🔓 | `{username, password, role?}` → `201 {message}` |
| `POST` | `/auth/login` | 🔓 | `{username, password}` → `{token, tokenType, expiresInMs, username, role}` |
| `GET` | `/auth/me` | 🔑 | → `{username, role}` |
| `GET` | `/auth/health` | 🔓 | → `{status, service}` |

### Products — `:8081`

| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/products` | 🔓 | List all |
| `GET` | `/products/{id}` | 🔓 | `404` if absent |
| `GET` | `/products/category/{name}` | 🔓 | Case-insensitive |
| `GET` | `/products/images/limits` | 🔓 | Max size + whether direct S3 upload is available |
| `POST` | `/products` | 🔑 | → `201 Product` |
| `PUT` | `/products/{id}` | 🔑 | |
| `DELETE` | `/products/{id}` | 🔑 | → `204`, also deletes the image |
| `POST` | `/products/images` | 👑 | `multipart/form-data`, part `file` → `201 {key, url, contentType, sizeBytes}` |
| `POST` | `/products/with-image` | 👑 | Parts `file` + `product` (JSON) → `201 Product` |
| `POST` | `/products/{id}/image` | 👑 | Part `file` → `Product` |
| `POST` | `/products/images/presign` | 👑 | → `{key, uploadUrl, publicUrl, method, requiredHeaders, expiresInSeconds}`. `409` when not on S3. |

### Orders — `:8082`

| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/orders` | 🔑 | |
| `GET` | `/orders/{id}` | 🔑 | |
| `GET` | `/orders/customer/{username}` | 🔑 | |
| `GET` | `/orders/status/{status}` | 🔑 | One of `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `POST` | `/orders` | 🔑 | Merges into an existing PENDING order for the same product |
| `PUT` | `/orders/{id}` | 🔑 | |
| `PUT` | `/orders/{id}/status/{status}` | 🔑 | `409` on an illegal transition |
| `DELETE` | `/orders/{id}` | 🔑 | → `204` |

### Error shape

Consistent everywhere, so a client can branch on `code` without knowing the
endpoint:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INVALID_ORDER",
  "message": "Cannot change an order from DELIVERED to PENDING",
  "path": "/orders/7/status/PENDING",
  "details": []
}
```

| Status | When |
|---:|---|
| `400` | Malformed body, or a business-rule violation |
| `401` | Missing, invalid or expired token |
| `403` | Valid token, wrong role |
| `404` | No such product / order |
| `409` | Duplicate username; illegal order-status transition |
| `413` | Image over the size limit |
| `415` | Unsupported image type |
| `502` | A downstream dependency failed |

Every response also carries `X-Correlation-Id`.

### Try it

```bash
GATEWAY=http://localhost:8080

TOKEN=$(curl -s -X POST $GATEWAY/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

curl -s -X POST $GATEWAY/products/images \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./photo.png" | jq

curl -s $GATEWAY/products | jq '.[0]'
```

---

## Configuration

Every value is environment-driven with a local default. Never commit a real
credential.

| Variable | Services | Purpose |
|---|---|---|
| `JWT_SECRET` | auth, gateway | HMAC key. **≥ 32 bytes** — the services refuse to start otherwise |
| `DB_PASSWORD` | product, order, auth | Postgres password |
| `SPRING_DATASOURCE_URL` | product, order, auth | Override the JDBC URL (local DB, or SQL Server) |
| `EUREKA_URL` | all except eureka | Registry address |
| `PRODUCT_SERVICE_URL` | order | Where Feign sends its call |
| `STORAGE_TYPE` | product | `local` (default) or `s3` |
| `S3_BUCKET` / `AWS_REGION` | product | Bucket and region |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | product | Leave blank to use the instance/task role |
| `UPLOAD_MAX_BYTES` | product | Upload limit, default 5 MB |
| `CORS_ALLOWED_ORIGINS` | all | Comma-separated. Patterns allowed, so `https://*.vercel.app` works |
| `ADMIN_PASSWORD` | auth | Bootstrap admin password |
| `LOG_LEVEL_APP` | all | Per-service log level |

**Profiles**

| Profile | Effect |
|---|---|
| `sqlserver` | Run the same jar against SQL Server |
| `discovery` | Gateway routes through Eureka (`lb://`) instead of fixed URLs |

See `.env.example` for a fully commented template.

---

## Testing

```bash
for s in eureka-server api-gateway auth-service product-service order-service; do
  (cd $s && ./mvnw -B clean verify) || break
done
```

or let CI do it — the same commands run on every push.

**What the tests actually assert.** They are unit tests: no Spring context, no
database, no network, no AWS account, and they finish in seconds.

| Suite | Asserts |
|---|---|
| `AuthServiceTest` | Hashing before persistence, role defaulting, duplicate rejection, identical error for unknown user vs wrong password |
| `JwtTokenServiceTest` | Foreign signature rejected, tampered payload rejected, expired rejected, wrong issuer rejected, payload is readable (Base64, not encryption), weak secret fails at construction |
| `JwtAuthGlobalFilterTest` | 401 vs 403, preflight never rejected, admin paths gated, client-supplied `X-User-Role` stripped |
| `ImageServiceTest` | Extension derived from content type not filename, type whitelist, size limit, unique keys |
| `ProductServiceTest` | Delete removes the image, storage outage does not roll back the delete, foreign `imageUrl` ignored |
| `ProductTest` | Invariants: name required, price non-negative and finite, rounded to cents, over-long text truncated, immutable |
| `OrderServiceTest` | Repeat orders merge, an outage is not mistaken for "not found", status-transition rules |
| `OrderStatusTest` | Every legal and illegal transition; terminal states |
| `BCryptPasswordHasherTest` | Salted, never plaintext, malformed hash is a failed login not a 500 |

Run the architecture rules locally too:

```bash
node tools/java-lint.mjs
```

---

## Repository layout

```
shopease/
├── api-gateway/            routing · CORS · JWT verification · correlation ids
├── auth-service/           registration · login · JWT   [onion]
├── product-service/        catalogue · image upload · S3 [onion]
├── order-service/          order lifecycle · Feign       [onion]
├── eureka-server/          service registry
├── frontend/               React + Vite (Vercel)
│   ├── vercel.json         SPA rewrite, cache headers, security headers
│   └── src/api/
│       ├── axiosInstance.js   request + response interceptors
│       ├── authApi.js         separate instance: no sign-out redirect
│       └── imageApi.js        multipart + presigned-URL upload
├── docs/
│   └── INTERVIEW_PREP.md   ← start here
├── .github/workflows/      ci.yml · deploy-frontend.yml · docker-publish.yml
├── infra/sql/              local database bootstrap
├── tools/java-lint.mjs     architecture rule enforcement
├── render.yaml             Render Blueprint (infrastructure as code)
├── docker-compose.yml      local Postgres (+ optional SQL Server, MinIO)
└── .env.example
```

---

## Further reading

- [`docs/INTERVIEW_PREP.md`](docs/INTERVIEW_PREP.md) — every topic mapped to its
  implementation, with Q&A, trap answers and a demo script.
- `*/src/main/java/.../package-info.java` — the layering rules, written next to
  the code they apply to.
- `*/src/main/resources/logback-spring.xml` — the log format and why each field
  is there.
- `*/Dockerfile` — the JVM flags tuned for a 0.1-CPU free-tier instance.
