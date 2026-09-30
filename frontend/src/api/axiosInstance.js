/**
 * The single axios instance every part of the app uses.
 *
 * ---------------------------------------------------------------------------
 * Why interceptors at all?
 * ---------------------------------------------------------------------------
 * An interceptor is a function that runs on every request or every response.
 * Without one, each of the 8 components that calls the API would have to
 * remember to attach the token, and each would need its own 401 handler. That is
 * duplicated logic, and duplicated logic drifts: someone adds a call in a new
 * component and forgets the token, and the bug only shows up for logged-in
 * users in that one screen.
 *
 * With interceptors, "attach the token" and "handle an expired session" are
 * written once and cannot be forgotten.
 *
 * ---------------------------------------------------------------------------
 * The two hooks
 * ---------------------------------------------------------------------------
 *   axios.interceptors.request.use(onFulfilled, onRejected)   -> before sending
 *   axios.interceptors.response.use(onFulfilled, onRejected)  -> after
 *
 * `onFulfilled` runs on success, `onRejected` on failure (a thrown error).
 * Interceptors run in registration order for requests and in REVERSE
 * registration order for responses — so a response interceptor registered first
 * is the last to see the response. Keep the number small and the order obvious.
 *
 * ---------------------------------------------------------------------------
 * What these interceptors do
 * ---------------------------------------------------------------------------
 * REQUEST
 *   1. attach `Authorization: Bearer <token>` when signed in
 *   2. attach a per-request id, so a slow call can be correlated with the log
 *   3. let a caller opt out of the JSON Content-Type (needed for FormData)
 *   4. count in-flight requests so a global spinner can be shown
 *
 * RESPONSE
 *   1. unwrap the body to `response.data` (so callers write `res` not `res.data`)
 *   2. 401 -> the token is missing, invalid or expired: sign out and redirect
 *   3. 403 -> signed in but not allowed: show a message, DO NOT sign out
 *   4. 413/415 -> translate the backend's upload errors into something a user
 *      can act on
 *   5. 5xx / network error -> a friendly message plus the correlation id
 */

import axios from 'axios';

// VITE_API_URL is inlined at BUILD time by Vite, so this is a constant baked
// into the bundle. Falling back to '/api' lets the Vercel rewrite in
// vercel.json proxy to the gateway, which removes CORS from the picture
// entirely (same-origin request).
const baseURL = import.meta.env.VITE_API_URL || '/api';

const TOKEN_KEY = 'token';
const USER_KEY = 'username';
const ROLE_KEY = 'role';

const axiosInstance = axios.create({
  baseURL,
  // Send cookies too. Harmless with a Bearer token, and required the moment
  // anything switches to an httpOnly cookie for the token.
  withCredentials: true,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ---------------------------------------------------------------------------
// in-flight request counter
// ---------------------------------------------------------------------------
// Kept in a module variable rather than React state on purpose: the interceptors
// live outside the component tree, so they have no access to a context, and a
// module variable cannot trigger a re-render. Callers subscribe with
// subscribeToPendingRequests() if they want a spinner.
let pendingCount = 0;
const pendingListeners = new Set();

function setPending(delta) {
  pendingCount = Math.max(0, pendingCount + delta);
  pendingListeners.forEach((listener) => listener(pendingCount));
}

/** @returns an unsubscribe function. */
export function subscribeToPendingRequests(listener) {
  pendingListeners.add(listener);
  listener(pendingCount);
  return () => pendingListeners.delete(listener);
}

export function getPendingRequestCount() {
  return pendingCount;
}

// ---------------------------------------------------------------------------
// sign-out, in one place
// ---------------------------------------------------------------------------
/**
 * Clears every trace of the session and returns the user to the login page.
 *
 * <p>localStorage is the right call for a demo and the wrong one for anything
 * handling real money: anything in localStorage is readable by any script on
 * the page, so a single XSS bug hands the attacker a valid token that survives
 * until it expires. The production answer is an httpOnly, Secure, SameSite
 * cookie, which JavaScript cannot read at all. The trade-off is that cookies
 * need CSRF protection; that is why the gateway disables CSRF only because it
 * uses a stateless Bearer token.
 *
 * @param {string} [reason] shown to the user so a silent redirect is not confusing
 */
function signOut(reason = '') {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(ROLE_KEY);

  const { pathname, search } = window.location;
  const isOnLogin = pathname === '/login' || pathname === '/register';

  // Without the isOnLogin guard, a 401 while already on /login would push the
  // same URL repeatedly: navigate -> fetch -> 401 -> navigate -> ...
  if (!isOnLogin) {
    const next = encodeURIComponent(pathname + search);
    const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    window.location.assign(`/login${query}${next ? `&next=${next}` : ''}`);
  }
}

/** Reads the token, returning null rather than a string when signed out. */
export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    // Private browsing / disabled storage: treat as signed out rather than
    // letting the exception escape into a render.
    return null;
  }
}

/** True when the stored JWT's `exp` claim is in the past. */
export function isTokenExpired() {
  const token = getToken();
  if (!token) return true;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (!payload.exp) return false;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true; // unreadable token is as good as invalid
  }
}

// ---------------------------------------------------------------------------
// REQUEST interceptor
// ---------------------------------------------------------------------------
axiosInstance.interceptors.request.use(
  (config) => {
    // 1. Token. `config.headers` is always defined on an AxiosRequestConfig
    //    inside a request interceptor, so no null guard is needed here.
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 2. Request id. Echoed in the server log next to the correlation id, which
    //    is how you find out WHERE a slow request went wrong.
    config.headers['X-Request-Id'] =
      `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

    // 3. FormData must NOT carry an explicit Content-Type. If you set it to
    //    application/json the browser sends JSON-encoded text as the file body
    //    and the server sees a corrupt upload. Letting the browser set the
    //    boundary is the whole point of multipart/form-data.
    const isFormData =
      typeof FormData !== 'undefined' && config.data instanceof FormData;
    if (isFormData) {
      delete config.headers['Content-Type'];
    }

    setPending(1);
    return config;
  },
  (error) => {
    // A request interceptor's onRejected fires when building the request itself
    // failed (a bad config, a synchronous throw). Rejecting is what tells axios
    // to abandon the call; returning `error` here would be swallowed.
    return Promise.reject(error);
  }
);

// ---------------------------------------------------------------------------
// RESPONSE interceptor
// ---------------------------------------------------------------------------
axiosInstance.interceptors.response.use(
  (response) => {
    setPending(-1);
    // Unwrapping here means every caller writes `const res = await api.get(...)`
    // and then uses `res` directly. It is a small convenience, and it is the
    // kind that becomes painful to undo later, so decide it once, here.
    return response.data;
  },
  (error) => {
    setPending(-1);

    const { response, config } = error;
    const status = response?.status;
    const code = response?.data?.code;
    const correlationId = response?.headers?.['x-correlation-id'];
    const method = (config?.method || 'get').toUpperCase();
    const url = config?.url || '';

    // The request never reached the server: DNS failure, connection refused,
    // CORS rejection, or our own 30 s timeout. There is no status to branch on,
    // and retrying is pointless if the backend is asleep.
    if (!response) {
      const message =
        error.code === 'ECONNABORTED'
          ? 'The request timed out. The service may be waking up — please try again.'
          : 'Cannot reach the server. Check your connection and try again.';
      return Promise.reject(
        Object.assign(new Error(message), { originalError: error, isNetworkError: true })
      );
    }

    switch (status) {
      // ---------------------------------------------------------------
      // 401 Unauthorized — we do not know who you are.
      // Either no token, a malformed one, or one that has expired.
      // This is the ONLY case that signs the user out.
      // ---------------------------------------------------------------
      case 401: {
        // Do not try to sign out for the login/register calls themselves:
        // failing to log in is a 401 by design, and redirecting would wipe the
        // page (and any retry) for a user who was never signed in.
        const isAuthAttempt = url.includes('/auth/login') || url.includes('/auth/register');
        if (!isAuthAttempt) {
          signOut('Your session has expired. Please sign in again.');
        }
        return Promise.reject(
          Object.assign(
            new Error(response?.data?.message || 'Your session has expired.'),
            { status, code, correlationId }
          )
        );
      }

      // ---------------------------------------------------------------
      // 403 Forbidden — we know who you are, you just may not do this.
      // Signing out here would be wrong: the user is still logged in, and
      // bouncing them to /login for a permission error is both wrong and
      // infuriating. The admin panel relies on this distinction.
      // ---------------------------------------------------------------
      case 403:
        return Promise.reject(
          Object.assign(
            new Error(response?.data?.message || 'You do not have permission to do that.'),
            { status, code, correlationId, isForbidden: true }
          )
        );

      // ---------------------------------------------------------------
      // 409 Conflict — valid request, clashes with current state.
      // 413 / 415 — upload too large / wrong file type. Both come back with a
      // message written for humans, so pass it straight through.
      // ---------------------------------------------------------------
      case 409:
      case 413:
      case 415:
        return Promise.reject(
          Object.assign(
            new Error(response?.data?.message || 'That request could not be completed.'),
            { status, code, correlationId }
          )
        );

      // ---------------------------------------------------------------
      // 404 — the endpoint or resource is gone. Usually a deployment
      // mismatch (the frontend is newer than the backend) or a typo in a
      // path, so naming the method and URL in the log saves real time.
      // ---------------------------------------------------------------
      case 404:
        console.error(`[api] 404 — ${method} ${url} (correlation: ${correlationId || 'n/a'})`);
        return Promise.reject(
          Object.assign(new Error('That resource was not found.'), {
            status,
            code,
            correlationId,
          })
        );

      default:
        break;
    }

    // ---------------------------------------------------------------
    // Everything else: 5xx, or an unexpected 4xx.
    // Log enough to debug with, tell the user nothing technical.
    // ---------------------------------------------------------------
    console.error(
      `[api] ${status} — ${method} ${url}`,
      { code, correlationId, body: response?.data }
    );

    const message =
      status >= 500
        ? 'Something went wrong on our side. Please try again in a moment.'
        : response?.data?.message || 'The request could not be completed.';

    return Promise.reject(
      Object.assign(new Error(message), {
        status,
        code,
        correlationId,
        isServerError: status >= 500,
        originalError: error,
      })
    );
  }
);

export default axiosInstance;
