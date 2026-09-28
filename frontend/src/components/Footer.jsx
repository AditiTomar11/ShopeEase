import { Link } from 'react-router-dom';
import { ArrowUp } from 'lucide-react';

export default function Footer() {
  const role = localStorage.getItem('role');
  const token = localStorage.getItem('token');

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <Link to="/" className="brand">
            Shop<span>Ease</span>
          </Link>
          <p>
            Laptops, smartphones, tablets and wearables — every listing with verified
            specifications and transparent, tax-inclusive pricing.
          </p>
        </div>

        <div className="footer-col">
          <h4>Shop</h4>
          <ul>
            <li><Link to={{ pathname: '/', hash: '#products' }}>All products</Link></li>
            <li><Link to={{ pathname: '/', hash: '#categories' }}>Categories</Link></li>
          </ul>
        </div>

        <div className="footer-col">
          <h4>Company</h4>
          <ul>
            <li><Link to="/about">Our Story</Link></li>
            {role === 'ADMIN' && <li><Link to="/admin">Admin</Link></li>}
          </ul>
        </div>

        <div className="footer-col">
          <h4>Account</h4>
          <ul>
            {token ? (
              <li><span className="footer-muted">Signed in as {localStorage.getItem('username')}</span></li>
            ) : (
              <>
                <li><Link to="/login">Login</Link></li>
                <li><Link to="/register">Create an account</Link></li>
              </>
            )}
          </ul>
        </div>
      </div>

      <div className="container footer-bottom">
        <p>© {new Date().getFullYear()} ShopEase. All rights reserved.</p>
        <p className="footer-muted">Built on Spring Boot microservices &amp; React.</p>
        <button type="button" className="footer-top-btn" onClick={scrollToTop}>
          <span>Back to top</span>
          <ArrowUp size={14} strokeWidth={1.5} />
        </button>
      </div>
    </footer>
  );
}
