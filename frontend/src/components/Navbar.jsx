import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';

export default function Navbar() {
  const { user, logout } = useAuth();
  const { items } = useCart();

  return (
    <nav className="navbar">
      <div>
        <Link to="/" className="brand">CloudMart</Link>
      </div>
      <div>
        <Link to="/">Products</Link>
        <Link to="/cart">Cart ({items.length})</Link>
        {user ? (
          <>
            <Link to="/orders">My Orders</Link>
            <a href="#" onClick={(e) => { e.preventDefault(); logout(); }}>
              Logout ({user.fullName})
            </a>
          </>
        ) : (
          <>
            <Link to="/login">Login</Link>
            <Link to="/register">Register</Link>
          </>
        )}
      </div>
    </nav>
  );
}
