# ShopEase – microservices demo

| Service          | Local port | Notes                                                        |
|------------------|-----------:|--------------------------------------------------------------|
| `eureka-server`  | 8761       | Service registry / dashboard                                 |
| `api-gateway`    | 8080       | Spring Cloud Gateway; routes `/products`, `/orders`, `/auth` |
| `product-service`| 8081       | Postgres (`productdb`)                                       |
| `order-service`  | 8082       | Postgres (`orderdb`), calls product-service via Feign        |
| `auth-service`   | 8083       | Postgres (`authdb`), JWT                                     |
| `frontend`       | 5173       | Vite + React, talks to the gateway via `VITE_API_URL`        |

Every Java service is a standalone Maven project with its own `Dockerfile`. On Render each one is a
**Docker** web service whose *Root Directory* is the service folder.

## Running on Render's free tier

### Environment variables

| Variable       | Services                    | Purpose                                        |
|----------------|-----------------------------|------------------------------------------------|
| `DB_PASSWORD`  | product, order, auth        | Postgres password                              |
| `JWT_SECRET`   | auth                        | HMAC key for tokens                            |
| `EUREKA_URL`   | gateway, product, order, auth | e.g. `https://<eureka>.onrender.com/eureka`  |
| `VITE_API_URL` | frontend (build time)       | Public URL of the gateway                      |

Render also injects `PORT` (default `10000`); every service binds to it automatically
(`server.port: ${PORT:<local-port>}`), so you don't need to set it.

Optional overrides (Spring picks these up without code changes):

- `EUREKA_CLIENT_ENABLED=false` – turn the Eureka client off completely on a service. Nothing in
  the deployed system discovers peers through Eureka (the gateway routes and the Feign client use
  fixed URLs), so this only removes startup work.
- `SPRING_DATASOURCE_URL` – e.g. the database's **internal** hostname
  (`jdbc:postgresql://dpg-…-a:5432/productdb`) if the service runs in the same Render region as
  the database; that skips the public internet + TLS on every connection.
- `JAVA_OPTS` – replaces the JVM flags baked into the Dockerfile.

### Recommended service settings

- **Health Check Path** for `product-service`: `/health` (cheap, no DB access). Render then only
  routes traffic once the app is really up and shows the health-check progress under *Events*,
  which is the easiest way to see what a cold start is doing.

### Why the first request after a pause is slow

Free web services are spun down after 15 minutes without traffic and restarted on the next
request on a **0.1 CPU / 512 MB** instance. A Spring Boot JVM that boots in ~8 s on a laptop needs
1–3 minutes there, and prints nothing for the first part of that – so the log tab looks empty
while it is in fact booting. The Dockerfiles and `application.yml` files are tuned for this
(C1-only JIT, serial GC, small heap, small connection pool, no blocking Eureka registry fetch, binding
to `PORT`), which shortens the cold start but cannot remove it.

To avoid cold starts entirely you have two options:

1. **Keep the service warm** with an external pinger (cron-job.org, UptimeRobot, …) hitting
   `https://<service>.onrender.com/health` every ~10 minutes. Beware the quota: a workspace gets
   **750 free instance hours per month** and a service that never sleeps uses ~720 of them, so you
   can keep roughly *one* service awake around the clock (or several for a few hours a day) before
   Render suspends all free services until the next month.
2. **Upgrade** the services on the request path (gateway + product) to the Starter plan, which
   never spins down.
