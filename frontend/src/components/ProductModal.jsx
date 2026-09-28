import { useEffect, useState } from 'react';
import { X, Heart, ShieldCheck, Truck, RefreshCw } from 'lucide-react';
import useLockBodyScroll from '../hooks/useLockBodyScroll';

const PLACEHOLDER_IMAGE =
  'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=600&auto=format&fit=crop&q=80';

export default function ProductModal({
  product,
  onClose,
  onAddToCart,
  onBuyNow,
  isWishlisted,
  onToggleWishlist,
}) {
  const [quantity, setQuantity] = useState(1);
  const open = !!product;

  useLockBodyScroll(open);

  // Reset quantity for each product and close on Escape.
  useEffect(() => {
    if (!open) return undefined;
    setQuantity(1);
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, product, onClose]);

  if (!product) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-view-title"
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
          <X size={20} strokeWidth={1.5} />
        </button>

        <div className="modal-media">
          <img
            src={product.imageUrl || PLACEHOLDER_IMAGE}
            alt={product.name}
            onError={(e) => {
              e.target.onerror = null;
              e.target.src = PLACEHOLDER_IMAGE;
            }}
          />
        </div>

        <div className="modal-body">
          {product.category && <span className="eyebrow">{product.category}</span>}
          <h2 id="quick-view-title" className="modal-title">
            {product.name}
          </h2>

          <p className="modal-price">
            ₹{Number(product.price).toLocaleString('en-IN')}
            <span>Inclusive of all taxes</span>
          </p>

          <p className="modal-desc">
            {product.description ||
              'Verified specifications and genuine performance, backed by the manufacturer warranty.'}
          </p>

          <ul className="perks">
            <li>
              <Truck size={18} strokeWidth={1.5} />
              <span>Dispatched within 24 hours</span>
            </li>
            <li>
              <ShieldCheck size={18} strokeWidth={1.5} />
              <span>1-year official brand warranty</span>
            </li>
            <li>
              <RefreshCw size={18} strokeWidth={1.5} />
              <span>7-day hassle-free returns</span>
            </li>
          </ul>

          <div className="modal-controls">
            <div className="qty-stepper" aria-label="Quantity">
              <button type="button" onClick={() => setQuantity(Math.max(1, quantity - 1))} aria-label="Decrease">
                −
              </button>
              <span>{quantity}</span>
              <button type="button" onClick={() => setQuantity(quantity + 1)} aria-label="Increase">
                +
              </button>
            </div>

            <button
              type="button"
              className={`wishlist-inline${isWishlisted ? ' is-active' : ''}`}
              onClick={() => onToggleWishlist(product.id)}
            >
              <Heart size={16} strokeWidth={1.5} fill={isWishlisted ? 'currentColor' : 'none'} />
              <span>{isWishlisted ? 'Saved' : 'Save for later'}</span>
            </button>
          </div>

          <div className="modal-actions">
            <button
              type="button"
              className="btn btn-outline btn-block"
              onClick={() => {
                onAddToCart(product, quantity);
                onClose();
              }}
            >
              Add to Cart
            </button>
            <button
              type="button"
              className="btn btn-dark btn-block"
              onClick={() => {
                onBuyNow(product, quantity);
                onClose();
              }}
            >
              Buy Now
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
