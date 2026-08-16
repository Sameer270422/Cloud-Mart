import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';

export default function Navbar() {
  const { user, logout } = useAuth();
  const { items } = useCart();
  const itemCount = items.reduce((sum, i) => sum + i.quantity, 0);

  const initials = user?.fullName
    ? user.fullName.trim().split(/\s+/).slice(0, 2).map((n) => n[0]).join('').toUpperCase()
    : '';

  return (
    <nav className="navbar">
      <Link to="/" className="brand">
        <span className="brand-mark" aria-hidden="true">✨</span>
        CloudMart
      </Link>
      <div className="navbar-links">
        <Link to="/">Products</Link>
        <Link to="/cart">Cart{itemCount > 0 ? ` (${itemCount})` : ''}</Link>
        {user ? (
          <>
            <Link to="/orders">My Orders</Link>
            <span className="user-chip">
              <span className="avatar" aria-hidden="true">{initials}</span>
              {user.fullName}
            </span>
            <button className="secondary" onClick={logout}>Logout</button>
          </>
        ) : (
          <>
            <Link to="/login">Login</Link>
            <Link to="/register" className="btn">Sign up</Link>
          </>
        )}
      </div>
    </nav>
  );
}
