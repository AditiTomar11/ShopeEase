# ShopEase — Frontend

React 18 + Vite storefront. Static SPA deployed to **Vercel**; every API call
goes to the **API gateway** on Render.

Editorial design system: Playfair Display for headings, Inter for body, a light
palette, flat square corners. Navbar with cart/wishlist drawers and quick view,
an admin panel with product and order management, and image upload to S3.

---

## Quick start

```bash
cp .env.local.example .env.local     # VITE_API_URL=/api
npm install
npm run dev                           # http://localhost:5173
```

`/api` is proxied to `http://localhost:8080` (see `vite.config.js`), so in
development the browser only makes same-origin requests and **CORS never comes
into the picture**.

The admin panel lives at `/admin`. Sign in with **admin / admin123** (created by
the auth service's `AdminSeeder`).

```bash
npm run build      # -> dist/
npm run preview    # serve the production build locally
```

---

## Environment

| Variable | When | Notes |
|---|---|---|
| `VITE_API_URL` | build time | Full gateway URL, or `/api` for a same-origin proxy |

> **Vite inlines `import.meta.env.VITE_*` at BUILD time, not at runtime.**
> Changing the value requires a **rebuild**, not just a restart. This is the
> single most common source of "I changed the env var and nothing happened".

Never commit a real `.env` / `.env.local`. Both are gitignored; the `.example`
files are not.

---

## Source layout

```
src/
├── api/
│   ├── axiosInstance.js   the shared instance: request + response interceptors
│   ├── authApi.js         a SEPARATE instance, so a failed login never redirects
│   └── imageApi.js        multipart upload + presigned direct-to-S3 upload
├── components/            Navbar, ProductCard, ProductModal, CartDrawer, …
├── pages/
│   ├── Home.jsx           catalogue, filters, cold-start retry
│   ├── Login.jsx  Register.jsx
│   ├── AdminPanel.jsx     products, image upload, orders
│   └── About.jsx
├── utils/decodeToken.js   reads `sub`/`role` for the UI — NOT a security check
├── hooks/                 useLockBodyScroll
└── index.css              design system
```

---

## The interceptors

`src/api/axiosInstance.js` is the single axios instance the whole app uses.

**Request interceptor**

1. attaches `Authorization: Bearer <token>`;
2. attaches an `X-Request-Id` for tracing;
3. **deletes `Content-Type` when the body is `FormData`** — the browser must set
   it so it can include the multipart boundary;
4. counts in-flight requests, so a global spinner is possible
   (`subscribeToPendingRequests`).

**Response interceptor**

1. **unwraps `response.data`** — so callers write `products`, not `res.data`.
   This is a project-wide convention: every call site was updated to match.
2. **`401`** → clear the session and redirect to `/login`. Skipped for
   `/auth/login` and `/auth/register`, and skipped when already on `/login` —
   otherwise a failed login would redirect to itself in a loop.
3. **`403`** → show a message, **do not sign out**. The user is still signed in.
4. **`409 / 413 / 415`** → surface the backend's human-readable message
   (duplicate username, image too large, wrong file type).
5. **`404`** → log method + URL + correlation id; usually a version mismatch.
6. **`5xx` / network error** → a friendly message, the details to the console.
7. decrements the in-flight counter on **both** paths — the error path is the one
   people forget, and the spinner then sticks forever.

### Why there are two instances

`authApi` is separate on purpose. A failed login is a `401` — correct and
expected — and routing it through the shared instance would clear storage and
redirect, wiping the page at the exact moment the user submitted the form.

The rule: put behaviour that is true of **every** request into the shared
instance; keep anything that would be *wrong* for some calls in its own
instance. Two small named instances beat one full of
`if (url !== '/auth/login')` branches.

---

## Image upload

`src/api/imageApi.js` supports both strategies and picks automatically:

1. **Presigned, direct to S3** (preferred when `app.storage.type=s3`) —
   `POST /products/images/presign`, then `PUT` the bytes straight to S3. The
   API never carries image bytes.
2. **Through the API** — `POST /products/images` as `multipart/form-data`.

The presigned `PUT` uses a **raw `axios` call**, not `axiosInstance`, on
purpose: the shared request interceptor would attach the Bearer token (a
credential leak to a third party) and a JSON `Content-Type` that S3 would reject
with `SignatureDoesNotMatch`.

Client-side validation in `validateImageFile` is UX only — it saves a doomed
5 MB round trip. The real check is the server-side whitelist in `ImageService`.

The admin form previews the selection locally and uploads only on submit, so a
user who changes their mind never leaves an orphaned object in the bucket.

---

## Deploying to Vercel

| Setting | Value |
|---|---|
| Framework preset | Vite |
| Build command | `npm run build` |
| Output directory | `dist` |
| Install command | `npm ci` |

Or connect the repo and let GitHub Actions do it — see
`.github/workflows/deploy-frontend.yml`.

### `vercel.json`

**The SPA rewrite** is the critical line:

```json
"rewrites": [{ "source": "/((?!api/).*)", "destination": "/index.html" }]
```

Without it, refreshing `/admin` or sharing a link to `/login` gives a 404,
because the browser asks Vercel for a file at that path and no such file exists.
It is a **rewrite, not a redirect** — the URL does not change, which keeps the
back button and relative links working.

**Cache headers.** Vite fingerprints filenames (`index-a1b2c3.js`), so
`/assets/*` gets `max-age=31536000, immutable`. `index.html` gets `no-cache`, or
a deploy would never reach anyone who had already visited — the classic
stale-frontend bug.

**Security headers.** `X-Content-Type-Options`, `X-Frame-Options: DENY`,
`Referrer-Policy`, `Permissions-Policy`.

### CORS: two valid setups

| Approach | How | Trade-off |
|---|---|---|
| **Direct** (shipped) | `VITE_API_URL=https://gateway.onrender.com` | Needs the gateway's allowed origins to include this app. Supported with patterns, so `https://*.vercel.app` covers every preview deploy. |
| **Same-origin proxy** | `VITE_API_URL=/api` + a Vercel rewrite forwarding `/api/*` to the gateway | **CORS disappears entirely** — no preflight, no risk of the two lists drifting apart. Costs one extra network hop. |

### Environment variables in CI

`VERCEL_TOKEN` (account access), `VERCEL_ORG_ID` and `VERCEL_PROJECT_ID` (which
project), plus `VITE_API_URL`. The workflow uses the Vercel **CLI** rather than a
marketplace action, so no third-party action runs with your token.

---

## Design system (`src/index.css`)

- **Fonts** — Playfair Display (headings, nav, buttons) + Inter (body/UI).
- **Colours** — CSS variables: `--bg`, `--surface-alt`, `--text`, `--muted`,
  `--line`, `--accent` (muted blue `#a9bce0`), `--ink`.
- **Buttons** — `.btn` with `.btn-primary` / `.btn-dark` / `.btn-outline`, plus
  `.btn-sm` / `.btn-lg` / `.btn-block`. Flat, square corners.
- **Layout** — `.container` (max 1240px), `.section`, `.section-head`,
  `.product-grid` (4 → 3 → 2 columns responsive).
- **Photos** — `public/hero-desk.jpg`, `public/about-story.jpg`. Replace them
  keeping the same filenames.
- **Admin panel** — `src/pages/AdminSidebar.css`, including the upload preview
  and progress bar styles.
