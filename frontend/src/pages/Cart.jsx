import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../api.js';

export default function Cart() {
  const { items, removeItem, setQuantity, total, clear } = useCart();
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
    return (
      <div>
        <div className="page-header"><h1>Your cart</h1></div>
        <div className="empty-state">
          <div className="icon">🛒</div>
          <h3>Your cart is empty</h3>
          <p>Add something you like from the catalog.</p>
          <Link to="/" className="btn">Browse products</Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header"><h1>Your cart</h1></div>
      {error && <p className="error">{error}</p>}
      {items.map((i) => (
        <div className="card cart-item" key={i.productId}>
          <div className="grow">
            <strong>{i.name}</strong>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>${i.price.toFixed(2)} each</span>
          </div>
          <div className="qty-stepper">
            <button className="secondary" onClick={() => setQuantity(i.productId, i.quantity - 1)} aria-label="Decrease quantity">−</button>
            <span>{i.quantity}</span>
            <button className="secondary" onClick={() => setQuantity(i.productId, i.quantity + 1)} aria-label="Increase quantity">+</button>
          </div>
          <span className="price">${(i.price * i.quantity).toFixed(2)}</span>
          <button className="danger" onClick={() => removeItem(i.productId)}>Remove</button>
        </div>
      ))}

      <div className="cart-summary">
        <div className="row">
          <span style={{ color: 'var(--text-muted)' }}>Total</span>
          <h3 style={{ margin: 0 }}>${total.toFixed(2)}</h3>
        </div>
        <button disabled={placing} onClick={checkout} style={{ width: '100%' }}>
          {placing ? 'Placing order...' : 'Checkout'}
        </button>
      </div>
    </div>
  );
}
