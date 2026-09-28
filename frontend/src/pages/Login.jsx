import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import authApi from '../api/authApi';
import { decodeToken } from '../utils/decodeToken';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await authApi.post('/auth/login', { username, password });
      const token = res.data.token;
      const decoded = decodeToken(token);

      const userRole = decoded?.role || 'CUSTOMER';
      const userSub = decoded?.sub || username;

      localStorage.setItem('token', token);
      localStorage.setItem('username', userSub);
      localStorage.setItem('role', userRole);

      if (userRole === 'ADMIN') {
        navigate('/admin');
      } else {
        navigate('/');
      }
    } catch (err) {
      setError('We could not sign you in. Check your username and password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page auth-page">
      <aside className="auth-visual" aria-hidden="true">
        <blockquote>
          <p>“Verified specs, honest prices, and a checkout that just works.”</p>
          <cite>— The ShopEase promise</cite>
        </blockquote>
      </aside>

      <div className="auth-panel">
        <div className="auth-card">
          <span className="eyebrow">Account</span>
          <h1 className="auth-title">Welcome back</h1>
          <p className="auth-sub">Sign in to place orders and manage your account.</p>

          {error && <div className="notice notice-error">{error}</div>}

          <form onSubmit={handleSubmit} className="form">
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                autoComplete="username"
                placeholder="Your username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </label>

            <label className="field">
              <span>Password</span>
              <input
                type="password"
                autoComplete="current-password"
                placeholder="Your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            <button type="submit" className="btn btn-dark btn-block btn-lg" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign In'}
            </button>
          </form>

          <p className="auth-alt">
            New to ShopEase? <Link to="/register">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
