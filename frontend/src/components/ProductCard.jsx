import { Heart } from 'lucide-react';

const PLACEHOLDER_IMAGE =
  'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=600&auto=format&fit=crop&q=80';

export default function ProductCard({
  product,
  onAddToCart,
  onBuyNow,
  onToggleWishlist,
  isWishlisted,
  onQuickView,
}) {
  return (
    <article className="product-card">
      <div className="product-media">
        <img
          src={product.imageUrl || PLACEHOLDER_IMAGE}
          alt={product.name}
          loading="lazy"
          onClick={() => onQuickView(product)}
          onError={(e) => {
            e.target.onerror = null;
            e.target.src = PLACEHOLDER_IMAGE;
          }}
        />

        <button
          type="button"
          className={`wishlist-toggle${isWishlisted ? ' is-active' : ''}`}
          title={isWishlisted ? 'Remove from wishlist' : 'Save to wishlist'}
          aria-label={isWishlisted ? 'Remove from wishlist' : 'Save to wishlist'}
          onClick={() => onToggleWishlist(product.id)}
        >
          <Heart size={18} strokeWidth={1.5} fill={isWishlisted ? 'currentColor' : 'none'} />
        </button>

        <button type="button" className="quick-view" onClick={() => onQuickView(product)}>
          Quick View
        </button>
      </div>

      <div className="product-body">
        {product.category && <span className="product-category">{product.category}</span>}
        <h3 className="product-name">
          <button type="button" onClick={() => onQuickView(product)} title={product.name}>
            {product.name}
          </button>
        </h3>
        <p className="product-price">₹{Number(product.price).toLocaleString('en-IN')}</p>

        <div className="product-actions">
          <button type="button" className="btn btn-outline btn-sm" onClick={() => onAddToCart(product)}>
            Add to Cart
          </button>
          <button type="button" className="btn btn-dark btn-sm" onClick={() => onBuyNow(product)}>
            Buy Now
          </button>
        </div>
      </div>
    </article>
  );
}
