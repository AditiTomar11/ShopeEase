import { Heart, X, Trash2 } from 'lucide-react';
import useLockBodyScroll from '../hooks/useLockBodyScroll';

const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=300';

export default function WishlistDrawer({
  isOpen,
  onClose,
  wishlistProducts,
  onRemoveFromWishlist,
  onAddToCart,
  onBuyNow,
}) {
  useLockBodyScroll(isOpen);

  if (!isOpen) return null;

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" aria-label="Wishlist" onClick={(e) => e.stopPropagation()}>
        <header className="drawer-head">
          <h2>
            Wishlist <span>({wishlistProducts.length})</span>
          </h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close wishlist">
            <X size={20} strokeWidth={1.5} />
          </button>
        </header>

        <div className="drawer-body">
          {wishlistProducts.length === 0 ? (
            <div className="drawer-empty">
              <Heart size={32} strokeWidth={1.25} />
              <h3>Nothing saved yet</h3>
              <p>Tap the heart on any product to keep it here for later.</p>
              <button type="button" className="btn btn-outline btn-sm" onClick={onClose}>
                Browse products
              </button>
            </div>
          ) : (
            <ul className="line-items">
              {wishlistProducts.map((product) => (
                <li key={product.id} className="line-item">
                  <img
                    src={product.imageUrl || PLACEHOLDER_IMAGE}
                    alt={product.name}
                    className="line-thumb"
                  />
                  <div className="line-info">
                    <h4>{product.name}</h4>
                    <p className="line-price">₹{Number(product.price).toLocaleString('en-IN')}</p>
                    <div className="line-actions">
                      <button
                        type="button"
                        className="btn btn-outline btn-xs"
                        onClick={() => {
                          onAddToCart(product);
                          onRemoveFromWishlist(product.id);
                        }}
                      >
                        Move to Cart
                      </button>
                      <button type="button" className="btn btn-dark btn-xs" onClick={() => onBuyNow(product)}>
                        Buy Now
                      </button>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="icon-btn line-remove"
                    title="Remove"
                    aria-label={`Remove ${product.name}`}
                    onClick={() => onRemoveFromWishlist(product.id)}
                  >
                    <Trash2 size={16} strokeWidth={1.5} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>
    </div>
  );
}
