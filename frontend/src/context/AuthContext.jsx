import React, { createContext, useContext, useEffect, useState } from 'react';
import { api } from '../api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const raw = window.localStorage.getItem('cloudmart_user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  });

  const persist = (authResponse) => {
    const nextUser = {
      id: authResponse.userId,
      fullName: authResponse.fullName,
      email: authResponse.email,
      role: authResponse.role,
    };
    window.localStorage.setItem('cloudmart_token', authResponse.token);
    window.localStorage.setItem('cloudmart_user', JSON.stringify(nextUser));
    setUser(nextUser);
  };

  const login = async (email, password) => persist(await api.login({ email, password }));
  const register = async (fullName, email, password) => persist(await api.register({ fullName, email, password }));
  const logout = () => {
    window.localStorage.removeItem('cloudmart_token');
    window.localStorage.removeItem('cloudmart_user');
    setUser(null);
  };

  useEffect(() => {
    window.addEventListener('cloudmart:unauthorized', logout);
    return () => window.removeEventListener('cloudmart:unauthorized', logout);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
