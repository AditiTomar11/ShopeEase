import { useState } from 'react';
import { X, Trash2, ShoppingCart } from 'lucide-react';
import axiosInstance from '../api/axiosInstance';
import useLockBodyScroll from '../hooks/useLockBodyScroll';

const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=300';

export default function CartDrawer({
  isOpen,
  onClose,
  cartItems,
  onUpdateQuantity,
  onRemoveItem,
  onClearCart,
  onOrderPlaced,
}) {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  useLockBodyScroll(isOpen);

  const isLoggedIn = !!localStorage.getItem('token');
  const username = localStorage.getItem('username');

  const totalPrice = cartItems.reduce(
    (sum, item) => sum + (item.product.price || 0) * item.quantity,
    0
  );

  const handleCheckout = async () => {
    if (!isLoggedIn) {
      setMessage({ type: 'error', text: 'Please sign in to place your order.' });
      return;
    }

    if (cartItems.length === 0) return;

    setLoading(true);
    setMessage({ type: '', text: '' });

    try {
      // Place order for each product in cart
      for (const item of cartItems) {
        await axiosInstance.post('/orders', {
          productId: item.product.id,
          quantity: item.quantity,
          username: username,
        });
      }

      setMessage({ type: 'success', text: 'Thank you — your order has been placed.' });
      onClearCart();
      if (onOrderPlaced) onOrderPlaced();

      setTimeout(() => {
        setMessage({ type: '', text: '' });
        onClose();
      }, 2000);
    } catch (err) {
      setMessage({
        type: 'error',
        text: 'We could not place the order. The order service may be unavailable.',
      });
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" aria-label="Shopping cart" onClick={(e) => e.stopPropagation()}>
        <header className="drawer-head">
          <h2>
            Your Cart <span>({cartItems.length})</span>
          </h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close cart">
            <X size={20} strokeWidth={1.5} />
          </button>
        </header>

        {message.text && <div className={`notice notice-${message.type}`}>{message.text}</div>}

        <div className="drawer-body">
          {cartItems.length === 0 ? (
            <div className="drawer-empty">
              <ShoppingCart size={32} strokeWidth={1.25} />
              <h3>Your cart is empty</h3>
              <p>Browse the collection and add the pieces you like.</p>
              <button type="button" className="btn btn-outline btn-sm" onClick={onClose}>
                Continue shopping
              </button>
            </div>
          ) : (
            <ul className="line-items">
              {cartItems.map((item) => (
                <li key={item.product.id} className="line-item">
                  <img
                    src={item.product.imageUrl || PLACEHOLDER_IMAGE}
                    alt={item.product.name}
                    className="line-thumb"
                  />
                  <div className="line-info">
                    <h4>{item.product.name}</h4>
                    <p className="line-price">₹{Number(item.product.price).toLocaleString('en-IN')}</p>
                    <div className="qty-stepper qty-sm" aria-label="Quantity">
                      <button
                        type="button"
                        aria-label="Decrease"
                        onClick={() => onUpdateQuantity(item.product.id, item.quantity - 1)}
                      >
                        −
                      </button>
                      <span>{item.quantity}</span>
                      <button
                        type="button"
                        aria-label="Increase"
                        onClick={() => onUpdateQuantity(item.product.id, item.quantity + 1)}
                      >
                        +
                      </button>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="icon-btn line-remove"
                    title="Remove"
                    aria-label={`Remove ${item.product.name}`}
                    onClick={() => onRemoveItem(item.product.id)}
                  >
                    <Trash2 size={16} strokeWidth={1.5} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {cartItems.length > 0 && (
          <footer className="drawer-foot">
            <div className="summary-row">
              <span>Subtotal</span>
              <span>₹{totalPrice.toLocaleString('en-IN')}</span>
            </div>
            <div className="summary-row summary-muted">
              <span>Shipping</span>
              <span>Free</span>
            </div>
            <div className="summary-row summary-total">
              <span>Total</span>
              <span>₹{totalPrice.toLocaleString('en-IN')}</span>
            </div>

            <button
              type="button"
              className="btn btn-dark btn-block btn-lg"
              onClick={handleCheckout}
              disabled={loading}
            >
              {loading ? 'Placing your order…' : 'Checkout'}
            </button>
            <p className="drawer-note">Taxes included. Orders are confirmed by our order service.</p>
          </footer>
        )}
      </aside>
    </div>
  );
}
