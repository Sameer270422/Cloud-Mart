import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../api.js';

export default function Cart() {
  const { items, removeItem, total, clear } = useCart();
  const { user } = useAuth();
  const [error, setError] = useState('');
  const [placing, setPlacing] = useState(false);
  const navigate = useNavigate();

  const checkout = async () => {
    if (!user) {
      navigate('/login');
      return;
    }
    setPlacing(true);
    setError('');
    try {
      await api.placeOrder({
        userId: user.id,
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      });
      clear();
      navigate('/orders');
    } catch (e) {
      setError(e.message);
    } finally {
      setPlacing(false);
    }
  };

  if (items.length === 0) {
    return <div><h1>Your cart</h1><p>Your cart is empty.</p></div>;
  }

  return (
    <div>
      <h1>Your cart</h1>
      {error && <p className="error">{error}</p>}
      {items.map((i) => (
        <div className="card" key={i.productId} style={{ marginBottom: '0.75rem' }}>
          <strong>{i.name}</strong> &times; {i.quantity}
          <span className="price" style={{ float: 'right' }}>${(i.price * i.quantity).toFixed(2)}</span>
          <div>
            <button className="secondary" onClick={() => removeItem(i.productId)}>Remove</button>
          </div>
        </div>
      ))}
      <h3>Total: ${total.toFixed(2)}</h3>
      <button disabled={placing} onClick={checkout}>{placing ? 'Placing order...' : 'Checkout'}</button>
    </div>
  );
}
