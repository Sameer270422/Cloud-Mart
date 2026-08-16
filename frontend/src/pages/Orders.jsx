import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';

const STATUS_BADGE = {
  CREATED: 'neutral',
  CONFIRMED: 'success',
  CANCELLED: 'danger',
};

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.listOrders()
      .then(setOrders)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <div className="page-header"><h1>My orders</h1></div>
      {error && <p className="error">{error}</p>}

      {loading ? (
        <div className="skeleton" style={{ height: 300 }} />
      ) : orders.length === 0 ? (
        <div className="empty-state">
          <div className="icon">📦</div>
          <h3>No orders yet</h3>
          <p>Once you check out, your orders will show up here.</p>
          <Link to="/" className="btn">Start shopping</Link>
        </div>
      ) : (
        orders.map((o) => (
          <div className="card order-card" key={o.id}>
            <div className="order-card-head">
              <div>
                <strong>Order #{o.id}</strong>
                <span className={`badge ${STATUS_BADGE[o.status] || 'neutral'}`} style={{ marginLeft: '0.6rem' }}>
                  {o.status}
                </span>
              </div>
              <span className="price">${o.totalAmount}</span>
            </div>
            <ul>
              {o.items.map((it) => (
                <li key={it.id}>
                  <span>{it.productName} &times; {it.quantity}</span>
                  <span>${(it.unitPrice * it.quantity).toFixed(2)}</span>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </div>
  );
}
