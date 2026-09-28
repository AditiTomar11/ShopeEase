import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import About from './pages/About';
import Login from './pages/Login';
import Register from './pages/Register';
import AdminPanel from './pages/AdminPanel';
import CartDrawer from './components/CartDrawer';
import WishlistDrawer from './components/WishlistDrawer';
import ProductModal from './components/ProductModal';
import axiosInstance from './api/axiosInstance';

// Start every new route at the top of the page (hash links keep their target).
function ScrollToTop() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (!hash) window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [pathname, hash]);
  return null;
}

function App() {
  const [cartItems, setCartItems] = useState(() => {
    try {
      const saved = localStorage.getItem('shopease_cart');
      return saved ? JSON.parse(saved) : [];
    } catch (e) {
      return [];
    }
  });

  const [wishlistIds, setWishlistIds] = useState(() => {
    try {
      const saved = localStorage.getItem('shopease_wishlist');
      return saved ? JSON.parse(saved) : [];
    } catch (e) {
      return [];
    }
  });

  const [allProducts, setAllProducts] = useState([]);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [isWishlistOpen, setIsWishlistOpen] = useState(false);
  const [quickViewProduct, setQuickViewProduct] = useState(null);
  const [toastMessage, setToastMessage] = useState('');

  // Persist cart to localStorage
  useEffect(() => {
    localStorage.setItem('shopease_cart', JSON.stringify(cartItems));
  }, [cartItems]);

  // Persist wishlist to localStorage
  useEffect(() => {
    localStorage.setItem('shopease_wishlist', JSON.stringify(wishlistIds));
  }, [wishlistIds]);

  // Fetch all products for global reference (used in wishlist drawer)
  const fetchAllProducts = async () => {
    try {
      const res = await axiosInstance.get('/products');
      setAllProducts(res.data || []);
    } catch (err) {
      // ignore silent fetch
    }
  };

  useEffect(() => {
    fetchAllProducts();
  }, []);

  const showToast = (text) => {
    setToastMessage(text);
    setTimeout(() => {
      setToastMessage('');
    }, 2800);
  };

  // Cart operations
  const handleAddToCart = (product, qty = 1) => {
    setCartItems((prev) => {
      const existing = prev.find((item) => item.product.id === product.id);
      if (existing) {
        return prev.map((item) =>
          item.product.id === product.id
            ? { ...item, quantity: item.quantity + qty }
            : item
        );
      } else {
        return [...prev, { product, quantity: qty }];
      }
    });
    showToast(`Added to cart — ${product.name}`);
  };

  const handleUpdateCartQuantity = (productId, newQty) => {
    if (newQty <= 0) {
      handleRemoveFromCart(productId);
      return;
    }
    setCartItems((prev) =>
      prev.map((item) =>
        item.product.id === productId ? { ...item, quantity: newQty } : item
      )
    );
  };

  const handleRemoveFromCart = (productId) => {
    setCartItems((prev) => prev.filter((item) => item.product.id !== productId));
  };

  const handleClearCart = () => {
    setCartItems([]);
  };

  // Wishlist operations
  const handleToggleWishlist = (productId) => {
    setWishlistIds((prev) => {
      if (prev.includes(productId)) {
        showToast('Removed from your wishlist');
        return prev.filter((id) => id !== productId);
      } else {
        showToast('Saved to your wishlist');
        return [...prev, productId];
      }
    });
  };

  const wishlistedProducts = allProducts.filter((p) => wishlistIds.includes(p.id));

  // Direct Buy Now handler
  const handleBuyNow = async (product, quantity = 1) => {
    const isLoggedIn = !!localStorage.getItem('token');
    const username = localStorage.getItem('username');

    if (!isLoggedIn) {
      showToast('Please sign in to place an order');
      return;
    }

    try {
      await axiosInstance.post('/orders', {
        productId: product.id,
        quantity: quantity,
        username: username,
      });
      showToast(`Order placed — ${product.name}`);
    } catch (err) {
      showToast('Order failed — the order service is unavailable');
    }
  };

  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ScrollToTop />
      <div className="app-shell">
        {toastMessage && (
          <div className="toast" role="status" aria-live="polite">
            {toastMessage}
          </div>
        )}

        <Navbar
          cartCount={cartItems.reduce((sum, item) => sum + item.quantity, 0)}
          wishlistCount={wishlistIds.length}
          onOpenCart={() => setIsCartOpen(true)}
          onOpenWishlist={() => setIsWishlistOpen(true)}
        />

        <main className="app-main">
          <Routes>
            <Route
              path="/"
              element={
                <Home
                  cartItems={cartItems}
                  wishlistIds={wishlistIds}
                  onAddToCart={handleAddToCart}
                  onBuyNow={handleBuyNow}
                  onToggleWishlist={handleToggleWishlist}
                  onQuickView={(product) => setQuickViewProduct(product)}
                  onProductsLoaded={(products) => setAllProducts(products)}
                />
              }
            />
            <Route path="/about" element={<About />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/admin" element={<AdminPanel />} />
          </Routes>
        </main>

        <Footer />

        {/* Global Drawers & Modals */}
        <CartDrawer
          isOpen={isCartOpen}
          onClose={() => setIsCartOpen(false)}
          cartItems={cartItems}
          onUpdateQuantity={handleUpdateCartQuantity}
          onRemoveItem={handleRemoveFromCart}
          onClearCart={handleClearCart}
          onOrderPlaced={fetchAllProducts}
        />

        <WishlistDrawer
          isOpen={isWishlistOpen}
          onClose={() => setIsWishlistOpen(false)}
          wishlistProducts={wishlistedProducts}
          onRemoveFromWishlist={handleToggleWishlist}
          onAddToCart={handleAddToCart}
          onBuyNow={handleBuyNow}
        />

        <ProductModal
          product={quickViewProduct}
          onClose={() => setQuickViewProduct(null)}
          onAddToCart={handleAddToCart}
          onBuyNow={handleBuyNow}
          isWishlisted={quickViewProduct ? wishlistIds.includes(quickViewProduct.id) : false}
          onToggleWishlist={handleToggleWishlist}
        />
      </div>
    </BrowserRouter>
  );
}

export default App;
