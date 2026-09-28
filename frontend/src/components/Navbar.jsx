import { useEffect, useState } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { ShoppingCart, Heart, Menu, X } from 'lucide-react';

export default function Navbar({
  cartCount = 0,
  wishlistCount = 0,
  onOpenCart,
  onOpenWishlist,
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');
  const username = localStorage.getItem('username');

  // The header sits on top of the hero photo on the home page and turns solid once
  // the visitor scrolls (or on any other page).
  const overlay = location.pathname === '/';

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    setMobileMenuOpen(false);
    navigate('/login');
  };

  // "Shop" scrolls to the catalog when we're already on the home page, otherwise
  // it navigates home and lets Home scroll once the products have loaded.
  const goToShop = (e) => {
    e.preventDefault();
    setMobileMenuOpen(false);
    if (location.pathname === '/') {
      document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' });
    } else {
      navigate({ pathname: '/', hash: '#products' });
    }
  };

  const linkClass = ({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`;

  return (
    <header
      className={`site-header${overlay ? ' is-overlay' : ''}${scrolled ? ' is-scrolled' : ''}${
        mobileMenuOpen ? ' menu-open' : ''
      }`}
    >
      <nav className="site-nav container-wide" aria-label="Primary">
        <Link to="/" className="brand">
          Shop<span>Ease</span>
        </Link>

        <div className="nav-links">
          <a href="/#products" className="nav-link" onClick={goToShop}>
            Shop
          </a>
          <NavLink to="/about" className={linkClass}>
            Our Story
          </NavLink>
          {role === 'ADMIN' && (
            <NavLink to="/admin" className={linkClass}>
              Admin
            </NavLink>
          )}
        </div>

        <div className="nav-actions">
          {token ? (
            <div className="nav-user">
              <span className="nav-user-name">{username}</span>
              <button type="button" className="nav-link" onClick={handleLogout}>
                Logout
              </button>
            </div>
          ) : (
            <NavLink to="/login" className={linkClass}>
              Login
            </NavLink>
          )}

          <button
            type="button"
            className="nav-icon-btn"
            onClick={onOpenWishlist}
            aria-label={`Wishlist, ${wishlistCount} items`}
          >
            <Heart size={20} strokeWidth={1.5} />
            {wishlistCount > 0 && <span className="nav-badge">{wishlistCount}</span>}
          </button>

          <button
            type="button"
            className="nav-icon-btn nav-cart"
            onClick={onOpenCart}
            aria-label={`Cart, ${cartCount} items`}
          >
            <ShoppingCart size={20} strokeWidth={1.5} />
            <span className="nav-cart-count">{cartCount}</span>
          </button>

          <button
            type="button"
            className="nav-menu-btn"
            onClick={() => setMobileMenuOpen((open) => !open)}
            aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? <X size={22} strokeWidth={1.5} /> : <Menu size={22} strokeWidth={1.5} />}
          </button>
        </div>
      </nav>

      {mobileMenuOpen && (
        <div className="mobile-menu">
          <a href="/#products" onClick={goToShop}>
            Shop
          </a>
          <Link to="/about">Our Story</Link>
          {role === 'ADMIN' && <Link to="/admin">Admin</Link>}
          {token ? (
            <button type="button" onClick={handleLogout}>
              Logout ({username})
            </button>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register">Create account</Link>
            </>
          )}
        </div>
      )}
    </header>
  );
}
