const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const token = localStorageSafeGet('cloudmart_token');
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    // A 401 while we thought we were logged in means the token is gone/
    // expired/invalid as far as the gateway is concerned - clear local
    // auth state so the UI doesn't keep sending a token that'll never
    // work, and let AuthContext react (it owns the actual state).
    if (res.status === 401 && token) {
      window.dispatchEvent(new Event('cloudmart:unauthorized'));
    }
    throw new Error(body.error || `Request failed with status ${res.status}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

// NOTE: browser storage APIs are intentionally avoided in shared/artifact
// contexts; the frontend app itself runs standalone (not as an artifact)
// so localStorage is safe here, but we guard it defensively.
function localStorageSafeGet(key) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export const api = {
  register: (data) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  listProducts: (params = {}) => {
    const qs = new URLSearchParams(params).toString();
    return request(`/api/products${qs ? `?${qs}` : ''}`);
  },
  getProduct: (id) => request(`/api/products/${id}`),
  getCategories: () => request('/api/products/categories'),
  createProduct: (data) => request('/api/products', { method: 'POST', body: JSON.stringify(data) }),
  updateProduct: (id, data) => request(`/api/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteProduct: (id) => request(`/api/products/${id}`, { method: 'DELETE' }),
  placeOrder: (data) => request('/api/orders', { method: 'POST', body: JSON.stringify(data) }),
  listOrders: () => request('/api/orders'),
  listNotifications: () => request('/api/notifications'),
  semanticSearch: (q, limit = 12) =>
    request(`/api/assistant/search?q=${encodeURIComponent(q)}&limit=${limit}`),
  assistantChat: (data) => request('/api/assistant/chat', { method: 'POST', body: JSON.stringify(data) }),
};
