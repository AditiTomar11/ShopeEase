import { X } from 'lucide-react';

export default function CompareBar({ compareProducts, onRemove, onClear, onOpenCompare }) {
  if (compareProducts.length === 0) return null;

  return (
    <div className="compare-bar">
      <div className="compare-bar-inner">
        <div className="compare-thumbs">
          {compareProducts.map((p) => (
            <div className="compare-thumb" key={p.id}>
              <img src={p.imageUrl} alt={p.name} />
              <button type="button" onClick={() => onRemove(p.id)} aria-label={`Remove ${p.name}`}>
                <X size={12} strokeWidth={2} />
              </button>
            </div>
          ))}
        </div>

        <span className="compare-count">{compareProducts.length} of 3 selected</span>

        <div className="compare-bar-actions">
          <button type="button" className="btn-text" onClick={onClear}>
            Clear
          </button>
          <button
            type="button"
            className="btn btn-dark btn-sm"
            disabled={compareProducts.length < 2}
            onClick={onOpenCompare}
          >
            Compare
          </button>
        </div>
      </div>
    </div>
  );
}