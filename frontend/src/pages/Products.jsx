import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useCart } from '../context/CartContext.jsx';

const CATEGORY_STYLE = {
  electronics: { emoji: '💻', gradient: 'linear-gradient(135deg, #7c3aed, #2563eb)' },
  furniture: { emoji: '🪑', gradient: 'linear-gradient(135deg, #d97706, #b45309)' },
  outdoors: { emoji: '🏕️', gradient: 'linear-gradient(135deg, #16a34a, #0d9488)' },
};
const DEFAULT_STYLE = { emoji: '🛍️', gradient: 'linear-gradient(135deg, #6d28d9, #db2777)' };

function categoryStyle(category) {
  return CATEGORY_STYLE[(category || '').toLowerCase()] || DEFAULT_STYLE;
}

function stockClass(qty) {
  if (qty <= 0) return 'out';
  if (qty < 10) return 'low';
  return '';
}

function ProductCardSkeleton() {
  return <div className="skeleton" />;
}

export default function Products() {
  const [products, setProducts] = useState([]);
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const { addItem } = useCart();

  useEffect(() => {
    api.listProducts()
      .then(setProducts)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const handleSearch = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      setProducts(await api.listProducts(search ? { search } : {}));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="hero">
        <h1>Find what you need, faster.</h1>
        <p>Browse the CloudMart catalog or search in your own words.</p>
        <form onSubmit={handleSearch} className="search-bar">
          <input
            placeholder="Search products..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button type="submit">Search</button>
        </form>
      </div>

      {error && <p className="error">{error}</p>}

      {loading ? (
        <div className="grid">
          {Array.from({ length: 8 }).map((_, i) => <ProductCardSkeleton key={i} />)}
        </div>
      ) : products.length === 0 ? (
        <div className="empty-state">
          <div className="icon">🔍</div>
          <h3>No products found</h3>
          <p>Try a different search term.</p>
        </div>
      ) : (
        <div className="grid">
          {products.map((p) => {
            const style = categoryStyle(p.category);
            const price = p.price.toFixed ? p.price.toFixed(2) : p.price;
            return (
              <div className="card product-card" key={p.id}>
                <div className="product-thumb" style={{ background: style.gradient }}>
                  <span aria-hidden="true">{style.emoji}</span>
                </div>
                <div className="product-body">
                  <span className="badge">{p.category}</span>
                  <h3>{p.name}</h3>
                  <p className="product-desc">{p.description}</p>
                  <div className="product-footer">
                    <span className="price">${price}</span>
                    <span className={`stock-dot ${stockClass(p.stockQuantity)}`}>
                      {p.stockQuantity > 0 ? `${p.stockQuantity} in stock` : 'Out of stock'}
                    </span>
                  </div>
                  <button disabled={p.stockQuantity <= 0} onClick={() => addItem(p)}>
                    {p.stockQuantity <= 0 ? 'Unavailable' : 'Add to cart'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
