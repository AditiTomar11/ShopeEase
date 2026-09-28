import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Package, Plus, ClipboardList, Eye, Pencil, Trash2, RefreshCw, Search, X,
} from 'lucide-react';
import axiosInstance from '../api/axiosInstance';
import "./AdminSidebar.css";

const STATUS_OPTIONS = ['PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const EMPTY_FORM = { name: '', price: '', category: '', imageUrl: '', description: '' };
const inr = (n) => `₹${Number(n || 0).toLocaleString('en-IN')}`;

export default function AdminPanel() {
  const navigate = useNavigate();
  const isAdmin = localStorage.getItem('role') === 'ADMIN';

  const [section, setSection] = useState('products'); // 'products' | 'add' | 'orders'
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState(null);
  const [viewProduct, setViewProduct] = useState(null);
  const [productSearch, setProductSearch] = useState('');
  const [orderSearch, setOrderSearch] = useState('');

  // Sirf ADMIN role ko access
  useEffect(() => {
    if (!isAdmin) navigate('/');
  }, [isAdmin, navigate]);

  // Alert 3.5s baad apne aap hat jaaye
  useEffect(() => {
    if (!notice) return undefined;
    const t = setTimeout(() => setNotice(null), 3500);
    return () => clearTimeout(t);
  }, [notice]);

  const showNotice = (type, text) => setNotice({ type, text });

  // ---------- API (unchanged) ----------
  const fetchProducts = async () => {
    try {
      const res = await axiosInstance.get('/products');
      setProducts(Array.isArray(res.data) ? res.data : []);
    } catch {
      showNotice('error', 'Could not load products. Please refresh.');
    }
  };

  const fetchOrders = async () => {
    try {
      const res = await axiosInstance.get('/orders');
      setOrders(Array.isArray(res.data) ? res.data : []);
    } catch {
      showNotice('error', 'Could not load orders. Please refresh.');
    }
  };

  const loadAll = async () => {
    setLoading(true);
    await Promise.all([fetchProducts(), fetchOrders()]);
    setLoading(false);
  };

  useEffect(() => {
    if (isAdmin) loadAll();
  }, [isAdmin]); // eslint-disable-line react-hooks/exhaustive-deps

  // ---------- helpers ----------
  const productName = (order) =>
    products.find((p) => p.id === order.productId)?.name
    || order.productName
    || `Product #${order.productId}`;

  const filteredProducts = products.filter((p) => {
    const q = productSearch.trim().toLowerCase();
    return !q || `${p.name} ${p.category || ''}`.toLowerCase().includes(q);
  });

  const filteredOrders = [...orders]
    .sort((a, b) => b.id - a.id)
    .filter((o) => {
      const q = orderSearch.trim().toLowerCase();
      return !q || `${productName(o)} ${o.username || ''} ${o.status || ''}`.toLowerCase().includes(q);
    });

  const pendingCount = orders.filter((o) => (o.status || 'PENDING') === 'PENDING').length;
  const categories = [...new Set(products.map((p) => p.category).filter(Boolean))];

  // ---------- product form ----------
  const openAdd = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setSection('add');
  };

  const openEdit = (product) => {
    setEditingId(product.id);
    setForm({
      name: product.name || '',
      price: product.price ?? '',
      category: product.category || '',
      imageUrl: product.imageUrl || '',
      description: product.description || '',
    });
    setViewProduct(null);
    setSection('add');
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      name: form.name.trim(),
      price: Number(form.price),
      category: form.category.trim(),
      imageUrl: form.imageUrl.trim(),
      description: form.description.trim(),
    };
    if (!payload.name || form.price === '') {
      showNotice('error', 'Product name and price are required.');
      return;
    }
    setSaving(true);
    try {
      if (editingId) {
        await axiosInstance.put(`/products/${editingId}`, payload);
        showNotice('success', 'Product updated successfully!');
      } else {
        await axiosInstance.post('/products', payload);
        showNotice('success', 'New product created successfully!');
      }
      setForm(EMPTY_FORM);
      setEditingId(null);
      await fetchProducts();
      setSection('products');
    } catch {
      showNotice('error', 'Could not save the product. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const deleteProduct = async (id) => {
    if (!window.confirm('Delete this product?')) return;
    try {
      await axiosInstance.delete(`/products/${id}`);
      setProducts((prev) => prev.filter((p) => p.id !== id));
      showNotice('success', 'Product deleted.');
    } catch {
      showNotice('error', 'Could not delete the product.');
    }
  };

  // ---------- orders ----------
  const updateOrderStatus = async (order, status) => {
    try {
      await axiosInstance.put(`/orders/${order.id}`, { ...order, status });
      setOrders((prev) => prev.map((o) => (o.id === order.id ? { ...o, status } : o)));
      showNotice('success', `Order #${order.id} marked as ${status}.`);
    } catch {
      showNotice('error', 'Could not update the order status.');
    }
  };

  const deleteOrder = async (id) => {
    if (!window.confirm('Delete this order?')) return;
    try {
      await axiosInstance.delete(`/orders/${id}`);
      setOrders((prev) => prev.filter((o) => o.id !== id));
      showNotice('success', 'Order deleted.');
    } catch {
      showNotice('error', 'Could not delete the order.');
    }
  };

  if (!isAdmin) return null;

  return (
    <div className="adm-page">
      <div className="adm-layout">
        {/* ---------- LEFT SIDEBAR ---------- */}
        <aside className="adm-sidebar">
          <h2 className="adm-brand">Dashboard</h2>
          <nav className="adm-nav">
            <button
              type="button"
              className={`adm-nav-item${section === 'products' ? ' active' : ''}`}
              onClick={() => setSection('products')}
            >
              <Package size={18} /> Products
              <span className="adm-count">{products.length}</span>
            </button>
            <button
              type="button"
              className={`adm-nav-item${section === 'add' ? ' active' : ''}`}
              onClick={openAdd}
            >
              <Plus size={18} /> Add product
            </button>
            <button
              type="button"
              className={`adm-nav-item${section === 'orders' ? ' active' : ''}`}
              onClick={() => setSection('orders')}
            >
              <ClipboardList size={18} /> Orders
              <span className="adm-count">{orders.length}</span>
            </button>
          </nav>
        </aside>

        {/* ---------- RIGHT CONTENT ---------- */}
        <main className="adm-main">
          {notice && <div className={`adm-alert adm-alert-${notice.type}`}>{notice.text}</div>}

          {/* PRODUCTS LIST */}
          {section === 'products' && (
            <>
              <div className="adm-header">
                <h1>Products List</h1>
                <button type="button" className="adm-btn adm-btn-success" onClick={openAdd}>
                  <Plus size={18} /> Add Product
                </button>
              </div>

              <label className="adm-search">
                <Search size={16} />
                <input
                  value={productSearch}
                  onChange={(e) => setProductSearch(e.target.value)}
                  placeholder="Search by name or category…"
                />
              </label>

              <div className="adm-table-wrap">
                <table className="adm-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Image</th>
                      <th>Name</th>
                      <th>Category</th>
                      <th>Price</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loading ? (
                      <tr><td colSpan={6} className="adm-empty">Loading products…</td></tr>
                    ) : filteredProducts.length === 0 ? (
                      <tr><td colSpan={6} className="adm-empty">No products found.</td></tr>
                    ) : (
                      filteredProducts.map((p) => (
                        <tr key={p.id}>
                          <td>{p.id}</td>
                          <td>
                            {p.imageUrl
                              ? <img className="adm-thumb" src={p.imageUrl} alt="" />
                              : <span className="adm-thumb adm-thumb-empty" />}
                          </td>
                          <td className="adm-strong">{p.name}</td>
                          <td>{p.category || '—'}</td>
                          <td>{inr(p.price)}</td>
                          <td>
                            <div className="adm-actions">
                              <button type="button" className="adm-icon" title="View" onClick={() => setViewProduct(p)}>
                                <Eye size={18} />
                              </button>
                              <button type="button" className="adm-icon" title="Edit" onClick={() => openEdit(p)}>
                                <Pencil size={18} />
                              </button>
                              <button type="button" className="adm-icon adm-icon-danger" title="Delete" onClick={() => deleteProduct(p.id)}>
                                <Trash2 size={18} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {/* ADD / EDIT PRODUCT */}
          {section === 'add' && (
            <>
              <div className="adm-header">
                <h1>{editingId ? `Edit Product #${editingId}` : 'Add Product'}</h1>
                <button type="button" className="adm-btn adm-btn-outline" onClick={() => setSection('products')}>
                  Back to list
                </button>
              </div>

              <form className="adm-card" onSubmit={handleSubmit}>
                <div className="adm-form-grid">
                  <div className="adm-field">
                    <label htmlFor="adm-name">Product name</label>
                    <input id="adm-name" className="adm-input" name="name" value={form.name}
                      onChange={handleChange} placeholder="e.g. Dell Pro 14 Laptop" required />
                  </div>
                  <div className="adm-field">
                    <label htmlFor="adm-price">Price (₹)</label>
                    <input id="adm-price" className="adm-input" name="price" type="number" min="0" step="any"
                      value={form.price} onChange={handleChange} placeholder="e.g. 89999" required />
                  </div>
                  <div className="adm-field">
                    <label htmlFor="adm-category">Category</label>
                    <input id="adm-category" className="adm-input" name="category" value={form.category}
                      onChange={handleChange} placeholder="Laptop, Mobile, Tablet, Watch…" list="adm-categories" />
                    <datalist id="adm-categories">
                      {categories.map((c) => <option key={c} value={c} />)}
                    </datalist>
                  </div>
                  <div className="adm-field">
                    <label htmlFor="adm-image">Image URL</label>
                    <input id="adm-image" className="adm-input" name="imageUrl" value={form.imageUrl}
                      onChange={handleChange} placeholder="https://…" />
                  </div>
                  <div className="adm-field full">
                    <label htmlFor="adm-desc">Description</label>
                    <textarea id="adm-desc" className="adm-input" name="description" value={form.description}
                      onChange={handleChange} placeholder="Short product description" />
                  </div>
                </div>

                {form.imageUrl && <img className="adm-preview" src={form.imageUrl} alt="Preview" />}

                <div className="adm-form-actions">
                  <button type="submit" className="adm-btn adm-btn-success" disabled={saving}>
                    {saving ? 'Saving…' : editingId ? 'Update Product' : 'Save Product'}
                  </button>
                  {editingId && (
                    <button type="button" className="adm-btn adm-btn-secondary" onClick={openAdd}>
                      Cancel edit
                    </button>
                  )}
                </div>
              </form>
            </>
          )}

          {/* ORDERS LIST */}
          {section === 'orders' && (
            <>
              <div className="adm-header">
                <h1>Orders List</h1>
                <button type="button" className="adm-btn adm-btn-outline" onClick={loadAll} disabled={loading}>
                  <RefreshCw size={16} /> Refresh
                </button>
              </div>

              <div className="adm-summary">
                <span>Total orders <b>{orders.length}</b></span>
                <span>Pending <b>{pendingCount}</b></span>
              </div>

              <label className="adm-search">
                <Search size={16} />
                <input
                  value={orderSearch}
                  onChange={(e) => setOrderSearch(e.target.value)}
                  placeholder="Filter by product, customer or status…"
                />
              </label>

              <div className="adm-table-wrap">
                <table className="adm-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Product</th>
                      <th>Qty</th>
                      <th>Customer</th>
                      <th>Status</th>
                      <th>Update Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loading ? (
                      <tr><td colSpan={7} className="adm-empty">Loading orders…</td></tr>
                    ) : filteredOrders.length === 0 ? (
                      <tr><td colSpan={7} className="adm-empty">No orders found.</td></tr>
                    ) : (
                      filteredOrders.map((o) => {
                        const status = String(o.status || 'PENDING').toUpperCase();
                        const options = STATUS_OPTIONS.includes(status) ? STATUS_OPTIONS : [status, ...STATUS_OPTIONS];
                        return (
                          <tr key={o.id}>
                            <td>#{o.id}</td>
                            <td className="adm-strong">{productName(o)}</td>
                            <td>{o.quantity}</td>
                            <td>@{o.username || 'guest'}</td>
                            <td>
                              <span className={`adm-badge adm-badge-${status.toLowerCase()}`}>{status}</span>
                            </td>
                            <td>
                              <select className="adm-select" value={status} onChange={(e) => updateOrderStatus(o, e.target.value)}>
                                {options.map((s) => <option key={s} value={s}>{s}</option>)}
                              </select>
                            </td>
                            <td>
                              <div className="adm-actions">
                                <button type="button" className="adm-icon adm-icon-danger" title="Delete order" onClick={() => deleteOrder(o.id)}>
                                  <Trash2 size={18} />
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </main>
      </div>

      {/* VIEW PRODUCT MODAL (eye icon) */}
      {viewProduct && (
        <div className="adm-modal-backdrop" onClick={() => setViewProduct(null)}>
          <div className="adm-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="adm-modal-head">
              <h3>Product #{viewProduct.id}</h3>
              <button type="button" className="adm-icon" aria-label="Close" onClick={() => setViewProduct(null)}>
                <X size={18} />
              </button>
            </div>
            <div className="adm-modal-body">
              {viewProduct.imageUrl
                ? <img src={viewProduct.imageUrl} alt={viewProduct.name} />
                : <div className="adm-modal-noimg">No image</div>}
              <dl>
                <dt>Name</dt><dd>{viewProduct.name}</dd>
                <dt>Category</dt><dd>{viewProduct.category || '—'}</dd>
                <dt>Price</dt><dd>{inr(viewProduct.price)}</dd>
                <dt>Description</dt><dd>{viewProduct.description || '—'}</dd>
              </dl>
            </div>
            <div className="adm-modal-foot">
              <button type="button" className="adm-btn adm-btn-outline" onClick={() => setViewProduct(null)}>Close</button>
              <button type="button" className="adm-btn adm-btn-success" onClick={() => openEdit(viewProduct)}>
                <Pencil size={16} /> Edit
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}