import { useEffect, useState, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import ProductCard from '../components/ProductCard';
import {
  Search,
  Heart,
  ChevronDown,
  ArrowRight,
  BadgeCheck,
  Tag,
  ShieldCheck,
  Truck,
  Cpu,
  Laptop,
  Smartphone,
  Tablet,
  Watch,
  Headphones,
} from 'lucide-react';

const WHY_CHOOSE_US = [
  {
    Icon: BadgeCheck,
    title: 'Verified Specifications',
    description: 'Every laptop, phone, tablet and watch is listed with authentic manufacturer specs — no guesswork.',
  },
  {
    Icon: Tag,
    title: 'Honest Pricing',
    description: 'Exact prices with taxes included. No sponsored rankings and no hidden fees at checkout.',
  },
  {
    Icon: ShieldCheck,
    title: 'Secure Checkout',
    description: 'Stateless JWT authentication protects your account across every service behind the gateway.',
  },
  {
    Icon: Truck,
    title: 'Fast Dispatch',
    description: 'Orders flow straight to fulfilment, and repeat orders are consolidated automatically.',
  },
];

const HOW_IT_WORKS = [
  { step: '01', title: 'Browse', description: 'Filter the collection by category and price in real time.' },
  { step: '02', title: 'Compare', description: 'Open a quick view to check specifications side by side.' },
  { step: '03', title: 'Order', description: 'Add to cart or buy now — sign in once and you are set.' },
  { step: '04', title: 'Dispatch', description: 'Your order is confirmed and prepared for delivery.' },
];

// After this long on the initial load, assume the free-tier backend is cold-starting.
const WAKE_HINT_DELAY_MS = 6000;
// A cold start can fail with a 502/503 from the hosting edge before the JVM is up;
// retry a few times before showing an error.
const MAX_FETCH_ATTEMPTS = 4;
const RETRY_DELAY_MS = 8000;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function categoryIcon(name) {
  const lower = name.toLowerCase();
  if (lower.includes('laptop') || lower.includes('computer')) return Laptop;
  if (lower.includes('mobile') || lower.includes('phone')) return Smartphone;
  if (lower.includes('tablet') || lower.includes('pad')) return Tablet;
  if (lower.includes('watch') || lower.includes('wearable')) return Watch;
  if (lower.includes('audio') || lower.includes('headphone')) return Headphones;
  return Cpu;
}

function Home({
  wishlistIds,
  onAddToCart,
  onBuyNow,
  onToggleWishlist,
  onQuickView,
  onProductsLoaded,
}) {
  const location = useLocation();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('All');
  const [maxPrice, setMaxPrice] = useState(500000);
  const [sortBy, setSortBy] = useState('default');
  const [showWishlistOnly, setShowWishlistOnly] = useState(false);

  // Free-tier Render services spin down when idle and take a minute or more to
  // boot on the next request. Tell the user that's what's happening, and retry a
  // few times instead of giving up on the first failed attempt.
  const [wakingUp, setWakingUp] = useState(false);

  const fetchProducts = async () => {
    setLoading(true);
    setError('');
    const wakeHintTimer = setTimeout(() => setWakingUp(true), WAKE_HINT_DELAY_MS);

    try {
      for (let attempt = 1; ; attempt++) {
        try {
          const res = await axiosInstance.get('/products');
          const data = Array.isArray(res.data) ? res.data : [];
          setProducts(data);
          if (onProductsLoaded) onProductsLoaded(data);

          if (data.length > 0) {
            const highest = Math.max(...data.map((p) => p.price || 0));
            setMaxPrice(highest > 0 ? highest : 500000);
          }
          return;
        } catch (err) {
          if (attempt >= MAX_FETCH_ATTEMPTS) {
            setError(
              'The backend is taking longer than usual to wake up. Give it a moment and hit Retry.'
            );
            return;
          }
          await sleep(RETRY_DELAY_MS);
        }
      }
    } finally {
      clearTimeout(wakeHintTimer);
      setWakingUp(false);
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, []);

  // Honour "/#products" / "/#categories" links coming from other pages once the
  // catalog has rendered.
  useEffect(() => {
    if (loading || !location.hash) return;
    const target = document.getElementById(location.hash.slice(1));
    if (target) target.scrollIntoView({ behavior: 'smooth' });
  }, [loading, location.hash]);

  const scrollToProducts = (e) => {
    e.preventDefault();
    document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' });
  };

  const highestPrice = useMemo(
    () => (products.length > 0 ? Math.max(...products.map((p) => p.price || 0)) : 500000),
    [products]
  );

  const categories = useMemo(() => {
    const set = new Set(products.map((p) => p.category).filter(Boolean));
    return ['All', ...Array.from(set)];
  }, [products]);

  const filteredProducts = useMemo(() => {
    return products
      .filter((p) => {
        const matchesSearch =
          !searchQuery ||
          p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          (p.description && p.description.toLowerCase().includes(searchQuery.toLowerCase()));

        const matchesCategory =
          activeCategory === 'All' || p.category === activeCategory;

        const matchesPrice = (p.price || 0) <= maxPrice;

        const matchesWishlist = !showWishlistOnly || wishlistIds.includes(p.id);

        return matchesSearch && matchesCategory && matchesPrice && matchesWishlist;
      })
      .sort((a, b) => {
        if (sortBy === 'price-low') return (a.price || 0) - (b.price || 0);
        if (sortBy === 'price-high') return (b.price || 0) - (a.price || 0);
        if (sortBy === 'name-az') return a.name.localeCompare(b.name);
        return 0;
      });
  }, [products, searchQuery, activeCategory, maxPrice, sortBy, showWishlistOnly, wishlistIds]);

  const categoryShowcase = useMemo(() => {
    return categories
      .filter((c) => c !== 'All')
      .map((cat) => ({
        name: cat,
        count: products.filter((p) => p.category === cat).length,
        Icon: categoryIcon(cat),
      }));
  }, [categories, products]);

  const resetFilters = () => {
    setSearchQuery('');
    setActiveCategory('All');
    setShowWishlistOnly(false);
    setSortBy('default');
    setMaxPrice(highestPrice);
  };

  return (
    <div className="home">
      {/* Hero */}
      <section className="hero">
        <div className="hero-inner">
          <h1 className="hero-title">
            Next-Gen Hardware.
            <br />
            Verified Specs &amp; Value.
          </h1>
          <a href="#products" className="btn btn-primary btn-lg" onClick={scrollToProducts}>
            Shop Now
          </a>
        </div>
      </section>

      {/* Catalog */}
      <section id="products" className="section">
        <div className="container">
          <header className="section-head">
            <span className="eyebrow">The Collection</span>
            <h2 className="section-title">Gadgets &amp; Tech</h2>
            <p className="section-sub">
              Laptops, smartphones, tablets and wearables — curated and verified.
            </p>
          </header>

          <div className="toolbar">
            <div className="toolbar-row">
              <label className="search-field">
                <Search size={18} strokeWidth={1.5} />
                <input
                  type="search"
                  placeholder="Search laptops, phones, tablets, watches…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </label>

              <div className="toolbar-controls">
                <label className="range-field">
                  <span>Up to ₹{maxPrice.toLocaleString('en-IN')}</span>
                  <input
                    type="range"
                    min="0"
                    max={highestPrice}
                    step="500"
                    value={Math.min(maxPrice, highestPrice)}
                    onChange={(e) => setMaxPrice(Number(e.target.value))}
                  />
                </label>

                <label className="select-field">
                  <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                    <option value="default">Recommended</option>
                    <option value="price-low">Price: Low to High</option>
                    <option value="price-high">Price: High to Low</option>
                    <option value="name-az">Name: A to Z</option>
                  </select>
                  <ChevronDown size={16} strokeWidth={1.5} />
                </label>

                <button
                  type="button"
                  className={`toggle-chip${showWishlistOnly ? ' is-active' : ''}`}
                  onClick={() => setShowWishlistOnly(!showWishlistOnly)}
                >
                  <Heart size={14} strokeWidth={1.5} fill={showWishlistOnly ? 'currentColor' : 'none'} />
                  <span>Wishlist ({wishlistIds.length})</span>
                </button>
              </div>
            </div>

            {categories.length > 1 && (
              <div className="category-tabs" role="tablist" aria-label="Categories">
                {categories.map((cat) => (
                  <button
                    key={cat}
                    type="button"
                    role="tab"
                    aria-selected={activeCategory === cat}
                    className={`category-tab${activeCategory === cat ? ' is-active' : ''}`}
                    onClick={() => setActiveCategory(cat)}
                  >
                    {cat}
                    <span>
                      {cat === 'All' ? products.length : products.filter((p) => p.category === cat).length}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {error && (
            <div className="notice notice-error">
              <p>{error}</p>
              <button type="button" className="btn btn-outline btn-sm" onClick={fetchProducts}>
                Retry
              </button>
            </div>
          )}

          {loading && (
            <div className="state-block">
              <div className="spinner" />
              <p>{wakingUp ? 'Waking up the backend services…' : 'Loading the collection…'}</p>
              {wakingUp && (
                <p className="state-hint">
                  The servers sleep when idle on free hosting, so the first load can take a minute or two.
                </p>
              )}
            </div>
          )}

          {!loading && filteredProducts.length > 0 && (
            <>
              <p className="results-count">
                Showing {filteredProducts.length} of {products.length} products
              </p>
              <div className="product-grid">
                {filteredProducts.map((product) => (
                  <ProductCard
                    key={product.id}
                    product={product}
                    onAddToCart={onAddToCart}
                    onBuyNow={onBuyNow}
                    onToggleWishlist={onToggleWishlist}
                    isWishlisted={wishlistIds.includes(product.id)}
                    onQuickView={onQuickView}
                  />
                ))}
              </div>
            </>
          )}

          {!loading && filteredProducts.length === 0 && !error && (
            <div className="state-block">
              <h3>No products match your filters</h3>
              <p>Try a different category, clear the search or raise the price limit.</p>
              <button type="button" className="btn btn-outline btn-sm" onClick={resetFilters}>
                Reset filters
              </button>
            </div>
          )}
        </div>
      </section>

      {/* Categories */}
      {categoryShowcase.length > 0 && (
        <section id="categories" className="section section-alt">
          <div className="container">
            <header className="section-head">
              <span className="eyebrow">Browse</span>
              <h2 className="section-title">Shop by Category</h2>
            </header>

            <div className="category-grid">
              {categoryShowcase.map(({ name, count, Icon }) => (
                <button
                  key={name}
                  type="button"
                  className="category-tile"
                  onClick={() => {
                    setActiveCategory(name);
                    document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' });
                  }}
                >
                  <Icon size={30} strokeWidth={1.1} />
                  <h3>{name}</h3>
                  <p>
                    {count} {count === 1 ? 'product' : 'products'}
                  </p>
                  <span className="tile-link">
                    Browse <ArrowRight size={14} strokeWidth={1.5} />
                  </span>
                </button>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* Why us */}
      <section className="section">
        <div className="container">
          <header className="section-head">
            <span className="eyebrow">Our Promise</span>
            <h2 className="section-title">Why ShopEase</h2>
          </header>

          <div className="feature-grid">
            {WHY_CHOOSE_US.map(({ Icon, title, description }) => (
              <div key={title} className="feature">
                <Icon size={28} strokeWidth={1.1} />
                <h3>{title}</h3>
                <p>{description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="section section-alt">
        <div className="container">
          <header className="section-head">
            <span className="eyebrow">Simple by Design</span>
            <h2 className="section-title">How It Works</h2>
          </header>

          <ol className="steps">
            {HOW_IT_WORKS.map((item) => (
              <li key={item.step} className="step">
                <span className="step-number">{item.step}</span>
                <h3>{item.title}</h3>
                <p>{item.description}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* CTA */}
      <section className="cta-band">
        <div className="container cta-inner">
          <div>
            <h2>Ready to upgrade your setup?</h2>
            <p>Authentic laptops, smartphones, tablets and smartwatches — dispatched fast.</p>
          </div>
          <a href="#products" className="btn btn-dark btn-lg" onClick={scrollToProducts}>
            Explore the Collection
          </a>
        </div>
      </section>
    </div>
  );
}

export default Home;
