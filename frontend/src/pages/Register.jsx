import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import authApi from '../api/authApi';

export default function Register() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await authApi.post('/auth/register', {
        username,
        password,
        role: 'CUSTOMER',
      });

      setSuccess('Your account is ready. Taking you to sign in…');
      setTimeout(() => {
        navigate('/login');
      }, 1500);
    } catch (err) {
      setError('Registration failed. That username may already be taken.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page auth-page">
      <aside className="auth-visual" aria-hidden="true">
        <blockquote>
          <p>“Every listing with the details that actually matter before you buy.”</p>
          <cite>— The ShopEase promise</cite>
        </blockquote>
      </aside>

      <div className="auth-panel">
        <div className="auth-card">
          <span className="eyebrow">Account</span>
          <h1 className="auth-title">Create an account</h1>
          <p className="auth-sub">Save your wishlist and check out in seconds.</p>

          {error && <div className="notice notice-error">{error}</div>}
          {success && <div className="notice notice-success">{success}</div>}

          <form onSubmit={handleSubmit} className="form">
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                autoComplete="username"
                placeholder="Choose a username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </label>

            <label className="field">
              <span>Password</span>
              <input
                type="password"
                autoComplete="new-password"
                placeholder="Create a strong password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            <button type="submit" className="btn btn-dark btn-block btn-lg" disabled={loading}>
              {loading ? 'Creating your account…' : 'Create Account'}
            </button>
          </form>

          <p className="auth-alt">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
