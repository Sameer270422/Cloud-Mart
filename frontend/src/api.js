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
  placeOrder: (data) => request('/api/orders', { method: 'POST', body: JSON.stringify(data) }),
  listOrders: (userId) => request(`/api/orders?userId=${userId}`),
  listNotifications: () => request('/api/notifications'),
  semanticSearch: (q, limit = 12) =>
    request(`/api/assistant/search?q=${encodeURIComponent(q)}&limit=${limit}`),
  assistantChat: (data) => request('/api/assistant/chat', { method: 'POST', body: JSON.stringify(data) }),
};
