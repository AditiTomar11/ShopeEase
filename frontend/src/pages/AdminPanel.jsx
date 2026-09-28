import { useEffect, useState, useMemo } from 'react';
import { Navigate } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance';
import { Edit3, Trash2, Search, RefreshCw } from 'lucide-react';

const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?w=100';
const ORDER_STATUSES = ['PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminPanel() {
  const role = localStorage.getItem('role');

  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(false);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form State
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [editingId, setEditingId] = useState(null);

  // Search Filter state
  const [productSearch, setProductSearch] = useState('');
  const [orderSearch, setOrderSearch] = useState('');
  const [activeTab, setActiveTab] = useState('products'); // 'products' | 'orders'

  const fetchProducts = async () => {
    setLoadingProducts(true);
    try {
      const res = await axiosInstance.get('/products');
      setProducts(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setError('Failed to fetch products from backend service.');
    } finally {
      setLoadingProducts(false);
    }
  };

  const fetchOrders = async () => {
    setLoadingOrders(true);
    try {
      const res = await axiosInstance.get('/orders');
      setOrders(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setError('Failed to fetch orders from backend service.');
    } finally {
      setLoadingOrders(false);
    }
  };

  useEffect(() => {
    if (role === 'ADMIN') {
      fetchProducts();
      fetchOrders();
    }
  }, [role]);

  const resetForm = () => {
    setName('');
    setPrice('');
    setImageUrl('');
    setCategory('');
    setDescription('');
    setEditingId(null);
  };

  const showNotification = (msg, isSuccess = true) => {
    if (isSuccess) {
      setSuccess(msg);
      setTimeout(() => setSuccess(''), 3000);
    } else {
      setError(msg);
      setTimeout(() => setError(''), 3500);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      name,
      price: parseFloat(price),
      imageUrl,
      category,
      description,
    };

    try {
      if (editingId) {
        await axiosInstance.put(`/products/${editingId}`, payload);
        showNotification('Product updated successfully!');
      } else {
        await axiosInstance.post('/products', payload);
        showNotification('New product created successfully!');
      }
      resetForm();
      fetchProducts();
    } catch (err) {
      showNotification('Error saving product. Check backend service status.', false);
    }
  };

  const handleEdit = (product) => {
    setEditingId(product.id);
    setName(product.name);
    setPrice(product.price);
    setImageUrl(product.imageUrl || '');
    setCategory(product.category || '');
    setDescription(product.description || '');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleDeleteProduct = async (id) => {
    if (!window.confirm('Are you sure you want to delete this product?')) return;
    try {
      await axiosInstance.delete(`/products/${id}`);
      showNotification('Product deleted.');
      fetchProducts();
    } catch (err) {
      showNotification('Could not delete product.', false);
    }
  };

  const handleDeleteOrder = async (id) => {
    if (!window.confirm('Are you sure you want to delete this order?')) return;
    try {
      await axiosInstance.delete(`/orders/${id}`);
      showNotification('Order deleted.');
      fetchOrders();
    } catch (err) {
      showNotification('Could not delete order.', false);
    }
  };

  // Status Change via PUT /orders/{id}
  const handleUpdateOrderStatus = async (order, newStatus) => {
    try {
      await axiosInstance.put(`/orders/${order.id}`, {
        ...order,
        status: newStatus,
      });
      showNotification(`Order #${order.id} status changed to ${newStatus}`);
      fetchOrders();
    } catch (err) {
      showNotification('Could not update order status.', false);
    }
  };

  // Metrics
  const totalRevenue = useMemo(() => {
    return orders.reduce((sum, o) => {
      const match = products.find((p) => p.id === o.productId);
      const unitPrice = match ? match.price : 0;
      return sum + unitPrice * (o.quantity || 1);
    }, 0);
  }, [orders, products]);

  const pendingOrdersCount = useMemo(() => {
    return orders.filter((o) => (o.status || 'PENDING').toUpperCase() === 'PENDING').length;
  }, [orders]);

  const filteredProducts = useMemo(() => {
    if (!productSearch) return products;
    return products.filter(
      (p) =>
        p.name.toLowerCase().includes(productSearch.toLowerCase()) ||
        (p.category && p.category.toLowerCase().includes(productSearch.toLowerCase()))
    );
  }, [products, productSearch]);

  const filteredOrders = useMemo(() => {
    if (!orderSearch) return orders;
    return orders.filter(
      (o) =>
        (o.productName && o.productName.toLowerCase().includes(orderSearch.toLowerCase())) ||
        (o.username && o.username.toLowerCase().includes(orderSearch.toLowerCase())) ||
        String(o.id).includes(orderSearch)
    );
  }, [orders, orderSearch]);

  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="page admin-page">
      <div className="container">
        <header className="admin-head">
          <div>
            <span className="eyebrow">Admin</span>
            <h1 className="page-title">Store Management</h1>
            <p className="page-sub">Manage the catalog and keep orders moving.</p>
          </div>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => {
              fetchProducts();
              fetchOrders();
            }}
          >
            <RefreshCw size={14} strokeWidth={1.5} />
            <span>Refresh</span>
          </button>
        </header>

        {success && <div className="notice notice-success">{success}</div>}
        {error && <div className="notice notice-error">{error}</div>}

        {/* Metrics */}
        <div className="metrics-grid">
          <div className="metric">
            <span className="metric-label">Products</span>
            <strong className="metric-value">{products.length}</strong>
          </div>
          <div className="metric">
            <span className="metric-label">Orders</span>
            <strong className="metric-value">{orders.length}</strong>
          </div>
          <div className="metric">
            <span className="metric-label">Order value</span>
            <strong className="metric-value">₹{totalRevenue.toLocaleString('en-IN')}</strong>
          </div>
          <div className="metric">
            <span className="metric-label">Pending</span>
            <strong className="metric-value">{pendingOrdersCount}</strong>
          </div>
        </div>

        {/* Product form */}
        <section className="panel">
          <div className="panel-head">
            <h2>{editingId ? `Editing product #${editingId}` : 'Add a product'}</h2>
            {editingId && (
              <button type="button" className="btn-text" onClick={resetForm}>
                Cancel
              </button>
            )}
          </div>

          <form onSubmit={handleSubmit} className="form">
            <div className="form-grid">
              <label className="field">
                <span>Product name *</span>
                <input
                  type="text"
                  placeholder="e.g. UltraBook Pro M3"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </label>

              <label className="field">
                <span>Price (₹) *</span>
                <input
                  type="number"
                  step="0.01"
                  placeholder="e.g. 89999"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  required
                />
              </label>

              <label className="field">
                <span>Category *</span>
                <input
                  type="text"
                  placeholder="e.g. Laptop, Mobile, Audio"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  required
                />
              </label>

              <label className="field">
                <span>Image URL</span>
                <input
                  type="text"
                  placeholder="https://…"
                  value={imageUrl}
                  onChange={(e) => setImageUrl(e.target.value)}
                />
              </label>

              <label className="field field-full">
                <span>Description / specifications *</span>
                <textarea
                  placeholder="Detailed hardware specifications and description…"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  required
                />
              </label>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-dark">
                {editingId ? 'Update Product' : 'Add Product'}
              </button>
            </div>
          </form>
        </section>

        {/* Tabs */}
        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'products'}
            className={`tab${activeTab === 'products' ? ' is-active' : ''}`}
            onClick={() => setActiveTab('products')}
          >
            Products <span>{products.length}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'orders'}
            className={`tab${activeTab === 'orders' ? ' is-active' : ''}`}
            onClick={() => setActiveTab('orders')}
          >
            Orders <span>{orders.length}</span>
          </button>
        </div>

        {/* Products table */}
        {activeTab === 'products' && (
          <section className="panel panel-flush">
            <div className="table-toolbar">
              <label className="search-field">
                <Search size={16} strokeWidth={1.5} />
                <input
                  type="search"
                  placeholder="Filter products…"
                  value={productSearch}
                  onChange={(e) => setProductSearch(e.target.value)}
                />
              </label>
            </div>

            {loadingProducts ? (
              <div className="state-block">
                <div className="spinner" />
                <p>Loading products…</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Product</th>
                      <th>Category</th>
                      <th>Price</th>
                      <th className="col-actions">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredProducts.map((p) => (
                      <tr key={p.id}>
                        <td className="cell-muted">#{p.id}</td>
                        <td>
                          <div className="cell-product">
                            <img src={p.imageUrl || PLACEHOLDER_IMAGE} alt={p.name} />
                            <div>
                              <strong>{p.name}</strong>
                              <p>{p.description}</p>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="pill">{p.category}</span>
                        </td>
                        <td className="cell-price">₹{Number(p.price).toLocaleString('en-IN')}</td>
                        <td className="col-actions">
                          <div className="row-actions">
                            <button type="button" className="btn-text" onClick={() => handleEdit(p)}>
                              <Edit3 size={14} strokeWidth={1.5} />
                              <span>Edit</span>
                            </button>
                            <button
                              type="button"
                              className="btn-text btn-text-danger"
                              onClick={() => handleDeleteProduct(p.id)}
                            >
                              <Trash2 size={14} strokeWidth={1.5} />
                              <span>Delete</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}

                    {filteredProducts.length === 0 && (
                      <tr>
                        <td colSpan={5} className="cell-empty">
                          No products match your search.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}

        {/* Orders table */}
        {activeTab === 'orders' && (
          <section className="panel panel-flush">
            <div className="table-toolbar">
              <label className="search-field">
                <Search size={16} strokeWidth={1.5} />
                <input
                  type="search"
                  placeholder="Filter orders by product or username…"
                  value={orderSearch}
                  onChange={(e) => setOrderSearch(e.target.value)}
                />
              </label>
            </div>

            {loadingOrders ? (
              <div className="state-block">
                <div className="spinner" />
                <p>Loading orders…</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Order</th>
                      <th>Product</th>
                      <th>Qty</th>
                      <th>Customer</th>
                      <th>Status</th>
                      <th>Update status</th>
                      <th className="col-actions">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredOrders.map((o) => (
                      <tr key={o.id}>
                        <td className="cell-muted">#{o.id}</td>
                        <td>
                          <strong>{o.productName || `Product #${o.productId}`}</strong>
                        </td>
                        <td>{o.quantity}</td>
                        <td>@{o.username || 'guest'}</td>
                        <td>
                          <span className={`status status-${(o.status || 'PENDING').toLowerCase()}`}>
                            {o.status || 'PENDING'}
                          </span>
                        </td>
                        <td>
                          <label className="select-field select-sm">
                            <select
                              value={o.status || 'PENDING'}
                              onChange={(e) => handleUpdateOrderStatus(o, e.target.value)}
                            >
                              {ORDER_STATUSES.map((s) => (
                                <option key={s} value={s}>
                                  {s}
                                </option>
                              ))}
                            </select>
                          </label>
                        </td>
                        <td className="col-actions">
                          <div className="row-actions">
                            <button
                              type="button"
                              className="btn-text btn-text-danger"
                              onClick={() => handleDeleteOrder(o.id)}
                            >
                              <Trash2 size={14} strokeWidth={1.5} />
                              <span>Delete</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}

                    {filteredOrders.length === 0 && (
                      <tr>
                        <td colSpan={7} className="cell-empty">
                          No orders recorded yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
