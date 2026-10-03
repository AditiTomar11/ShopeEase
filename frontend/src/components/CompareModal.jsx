import { X } from 'lucide-react';

const PLACEHOLDER_IMAGE =
  'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=600&auto=format&fit=crop&q=80';

export default function CompareModal({ products, onClose, onRemove, onAddToCart, onBuyNow }) {
  if (!products || products.length === 0) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal compare-modal"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
          <X size={20} strokeWidth={1.5} />
        </button>

        <div className="compare-table-wrap">
          <table className="compare-table">
            <thead>
              <tr>
                <th></th>
                {products.map((p) => (
                  <th key={p.id}>
                    <img src={p.imageUrl || PLACEHOLDER_IMAGE} alt={p.name} />
                    <span>{p.name}</span>
                    <button type="button" className="btn-text" onClick={() => onRemove(p.id)}>
                      Remove
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <th>Price</th>
                {products.map((p) => (
                  <td key={p.id} className="cell-price">₹{Number(p.price).toLocaleString('en-IN')}</td>
                ))}
              </tr>
              <tr>
                <th>Category</th>
                {products.map((p) => (
                  <td key={p.id}>{p.category || '—'}</td>
                ))}
              </tr>
              <tr>
                <th>Description</th>
                {products.map((p) => (
                  <td key={p.id} className="cell-muted">{p.description || '—'}</td>
                ))}
              </tr>
              <tr>
                <th></th>
                {products.map((p) => (
                  <td key={p.id}>
                    <div className="compare-actions">
                      <button type="button" className="btn btn-outline btn-xs" onClick={() => onAddToCart(p)}>
                        Add to Cart
                      </button>
                      <button type="button" className="btn btn-dark btn-xs" onClick={() => onBuyNow(p)}>
                        Buy Now
                      </button>
                    </div>
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}