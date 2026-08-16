import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useCart } from '../context/CartContext.jsx';

export default function Products() {
  const [products, setProducts] = useState([]);
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const { addItem } = useCart();

  useEffect(() => {
    api.listProducts()
      .then(setProducts)
      .catch((e) => setError(e.message));
  }, []);

  const handleSearch = async (e) => {
    e.preventDefault();
    try {
      setProducts(await api.listProducts(search ? { search } : {}));
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div>
      <h1>Products</h1>
      {error && <p className="error">{error}</p>}
      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '0.5rem', maxWidth: 480 }}>
        <input
          placeholder="Search products..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit">Search</button>
      </form>
      <div className="grid" style={{ marginTop: '1.5rem' }}>
        {products.map((p) => (
          <div className="card" key={p.id}>
            <span className="badge">{p.category}</span>
            <h3>{p.name}</h3>
            <p>{p.description}</p>
            <p className="price">${p.price.toFixed ? p.price.toFixed(2) : p.price}</p>
            <p style={{ fontSize: '0.85rem', color: '#666' }}>{p.stockQuantity} in stock</p>
            <button onClick={() => addItem(p)}>Add to cart</button>
          </div>
        ))}
      </div>
    </div>
  );
}
