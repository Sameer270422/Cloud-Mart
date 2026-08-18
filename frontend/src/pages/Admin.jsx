import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

const EMPTY_FORM = { name: '', description: '', category: '', subcategory: '', price: '', stockQuantity: '' };

export default function Admin() {
  const [products, setProducts] = useState([]);
  const [categoryTree, setCategoryTree] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  const load = () => {
    setLoading(true);
    setError('');
    return Promise.all([api.listProducts(), api.getCategories()])
      .then(([p, c]) => { setProducts(p); setCategoryTree(c); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openAddForm = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError('');
    setFormOpen(true);
  };

  const openEditForm = (p) => {
    setEditingId(p.id);
    setForm({
      name: p.name,
      description: p.description || '',
      category: p.category || '',
      subcategory: p.subcategory || '',
      price: String(p.price),
      stockQuantity: String(p.stockQuantity),
    });
    setFormError('');
    setFormOpen(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');

    const price = Number(form.price);
    const stockQuantity = Number(form.stockQuantity);
    if (!form.name.trim()) { setFormError('Name is required.'); return; }
    if (!Number.isFinite(price) || price <= 0) { setFormError('Price must be a positive number.'); return; }
    if (!Number.isInteger(stockQuantity) || stockQuantity < 0) { setFormError('Stock must be a whole number, 0 or more.'); return; }

    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      category: form.category.trim(),
      subcategory: form.subcategory.trim() || null,
      price,
      stockQuantity,
    };

    setSaving(true);
    try {
      if (editingId) {
        await api.updateProduct(editingId, payload);
      } else {
        await api.createProduct(payload);
      }
      closeForm();
      await load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (p) => {
    if (!window.confirm(`Delete "${p.name}"? This can't be undone.`)) return;
    setDeletingId(p.id);
    setError('');
    try {
      await api.deleteProduct(p.id);
      setProducts((prev) => prev.filter((x) => x.id !== p.id));
    } catch (e) {
      setError(e.message);
    } finally {
      setDeletingId(null);
    }
  };

  const categoryOptions = categoryTree.map((n) => n.category);
  const subcategoryOptions = [...new Set(categoryTree.flatMap((n) => n.subcategories))];

  return (
    <div>
      <div className="page-header admin-header">
        <div>
          <h1>Admin</h1>
          <p>Add, edit, and remove products from the catalog.</p>
        </div>
        {!formOpen && <button onClick={openAddForm}>+ Add product</button>}
      </div>

      {error && <p className="error">{error}</p>}

      {formOpen && (
        <div className="card admin-form-card">
          <h3>{editingId ? 'Edit product' : 'Add product'}</h3>
          {formError && <p className="error">{formError}</p>}
          <form onSubmit={handleSubmit} className="admin-form">
            <input
              placeholder="Product name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            <textarea
              placeholder="Description"
              rows={2}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
            <div className="admin-form-row">
              <input
                placeholder="Category"
                list="admin-category-options"
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
              />
              <input
                placeholder="Subcategory (optional)"
                list="admin-subcategory-options"
                value={form.subcategory}
                onChange={(e) => setForm({ ...form, subcategory: e.target.value })}
              />
            </div>
            <datalist id="admin-category-options">
              {categoryOptions.map((c) => <option key={c} value={c} />)}
            </datalist>
            <datalist id="admin-subcategory-options">
              {subcategoryOptions.map((s) => <option key={s} value={s} />)}
            </datalist>
            <div className="admin-form-row">
              <input
                type="number"
                step="0.01"
                min="0.01"
                placeholder="Price"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
                required
              />
              <input
                type="number"
                step="1"
                min="0"
                placeholder="Stock quantity"
                value={form.stockQuantity}
                onChange={(e) => setForm({ ...form, stockQuantity: e.target.value })}
                required
              />
            </div>
            <div className="admin-form-actions">
              <button type="submit" disabled={saving}>
                {saving ? 'Saving…' : editingId ? 'Save changes' : 'Add product'}
              </button>
              <button type="button" className="secondary" onClick={closeForm} disabled={saving}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      {loading ? (
        <div className="skeleton" style={{ height: 300 }} />
      ) : products.length === 0 ? (
        <div className="empty-state">
          <div className="icon">📦</div>
          <h3>No products yet</h3>
          <p>Add your first product to get the catalog started.</p>
        </div>
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Category</th>
                <th>Price</th>
                <th>Stock</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>
                    <strong>{p.name}</strong>
                    <span className="admin-table-desc">{p.description}</span>
                  </td>
                  <td>
                    <span className="badge">{p.category}</span>
                    {p.subcategory && <span className="admin-subcat">{p.subcategory}</span>}
                  </td>
                  <td className="price">${Number(p.price).toFixed(2)}</td>
                  <td>
                    <span className={`stock-dot ${p.stockQuantity <= 0 ? 'out' : p.stockQuantity < 10 ? 'low' : ''}`}>
                      {p.stockQuantity}
                    </span>
                  </td>
                  <td className="admin-row-actions">
                    <button className="secondary" onClick={() => openEditForm(p)}>Edit</button>
                    <button
                      className="danger"
                      onClick={() => handleDelete(p)}
                      disabled={deletingId === p.id}
                    >
                      {deletingId === p.id ? 'Deleting…' : 'Delete'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
