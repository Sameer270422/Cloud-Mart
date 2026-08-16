import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../context/AuthContext.jsx';

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState('');
  const { user } = useAuth();

  useEffect(() => {
    api.listOrders(user.id).then(setOrders).catch((e) => setError(e.message));
  }, [user.id]);

  return (
    <div>
      <h1>My orders</h1>
      {error && <p className="error">{error}</p>}
      {orders.length === 0 && <p>No orders yet.</p>}
      {orders.map((o) => (
        <div className="card" key={o.id} style={{ marginBottom: '0.75rem' }}>
          <strong>Order #{o.id}</strong>
          <span className="badge" style={{ marginLeft: '0.5rem' }}>{o.status}</span>
          <p className="price">${o.totalAmount}</p>
          <ul>
            {o.items.map((it) => (
              <li key={it.id}>{it.productName} &times; {it.quantity}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
