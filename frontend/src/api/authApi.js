/**
 * A SEPARATE axios instance, on purpose.
 *
 * ---------------------------------------------------------------------------
 * Why not just use the shared `axiosInstance`?
 * ---------------------------------------------------------------------------
 * Because the auth calls must not trigger the global sign-out behaviour.
 *
 * A failed login is a 401. That is correct, expected, and the whole point of the
 * call. If it went through the shared instance, the 401 interceptor would clear
 * localStorage and redirect to /login — wiping the page (and the error message
 * the user needs to read) in the exact moment they submitted the form.
 *
 * ---------------------------------------------------------------------------
 * The general rule
 * ---------------------------------------------------------------------------
 * Interceptors are global side effects. Use them for behaviour that is genuinely
 * true of *every* request on that instance:
 *
 *   - attaching credentials, request ids, tracing headers  -> yes, global
 *   - redirecting to /login on 401                          -> yes, global
 *
 * and keep anything that is specific to a call — or that would be *wrong* for
 * some calls — out of the shared instance. Two small, well-named instances beat
 * one instance full of `if (url !== '/auth/login')` special cases.
 *
 * Note there is deliberately no response interceptor here: `Login.jsx` reads
 * `res.data.token`, because this instance returns the full Axios response.
 * Only `axiosInstance` unwraps the body, and mixing the two conventions is the
 * fastest way to ship a bug, so the difference is documented at both ends.
 */
import axios from 'axios';

const authApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  withCredentials: true,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request interceptor: send a request id so a failed login can be traced in the
 * service logs. Deliberately NOT sending the token — /auth/login and
 * /auth/register are public endpoints and an Authorization header there is just
 * noise.
 */
authApi.interceptors.request.use(
  (config) => {
    config.headers['X-Request-Id'] =
      `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    return config;
  },
  (error) => Promise.reject(error)
);

export default authApi;
