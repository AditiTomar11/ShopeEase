# ShopEase — Frontend

React + Vite frontend: clean editorial storefront (Playfair Display +
Inter, light palette). Navbar (Shop / Our Story / Login, + Admin for
ADMIN role), full-bleed hero, product grid with Quick View, Cart drawer
(real orders on checkout), Wishlist drawer, Buy Now, Login/Register
connected to `auth-service`, aur protected Admin Panel.

## Design system (src/index.css)

- Fonts: **Playfair Display** (brand, headings, nav, buttons) +
  **Inter** (body/UI) — Google Fonts link `index.html` mein hai.
- Colours: CSS variables `--bg`, `--surface-alt`, `--text`, `--muted`,
  `--line`, `--accent` (muted blue `#a9bce0`), `--ink`.
- Buttons: `.btn` + `.btn-primary` / `.btn-dark` / `.btn-outline`,
  sizes `.btn-sm` / `.btn-lg` / `.btn-block`. Flat, square corners.
- Layout: `.container` (max 1240px), `.section`, `.section-head`,
  `.product-grid` (4 → 3 → 2 columns responsive).
- Hero photo `public/hero-desk.jpg`, story photo `public/about-story.jpg`
  — replace karke apni photos laga sakti ho (same file name rakho).

## Chalane ka tarika

1. Backend services chalao (order zaroori hai):
   - `eureka-server` (8761)
   - `product-service` (8081)
   - `order-service` (8082)
   - `auth-service` (8083)
   - `api-gateway` (8080)

2. Is folder ke andar:
   ```
   npm install
   npm run dev
   ```

3. `http://localhost:5173` kholo.

## ⚠️ Backend dependency — abhi kya kaam nahi karega

Yeh frontend do fields use karta hai jo **backend mein abhi add nahi
hue** (step-by-step plan mein aage hain):

1. **`Product.imageUrl`** — Admin Panel se image URL ke saath product
   add karogi, lekin `product-service` ka `Product` model abhi sirf
   `name` aur `price` accept karta hai. Jab tak backend update nahi
   hota, image URL **save nahi hogi** (error aayega ya field ignore
   hogi, backend ki JSON parsing settings pe depend karta hai).

2. **`Order.username`** — "Buy Now" order banate waqt `username` bhi
   bhejta hai, lekin `order-service` ka `Order` model abhi sirf
   `productId` aur `quantity` leta hai. Jab tak backend update nahi
   hota, **order create fail ho sakta hai** ("Buy Now" pe error toast
   dikhega).

Dono cases mein **frontend code sahi hai** — bas backend ko catch up
karna hai. Jaise-jaise woh steps complete hote jaayenge, yeh features
apne aap kaam karna shuru kar denge, frontend mein kuch badalna nahi
padega.

## Folder structure

```
src/
├── api/
│   ├── axiosInstance.js    ← Gateway (8080) ke liye, JWT interceptor ke saath
│   └── authApi.js          ← auth-service (8083) ke liye seedha
├── components/
│   ├── Navbar.jsx           ← Fixed translucent header, mobile menu
│   ├── Footer.jsx
│   ├── ProductCard.jsx      ← Grid card: Quick View, wishlist, Add to Cart, Buy Now
│   ├── ProductModal.jsx     ← Quick View dialog (qty stepper)
│   ├── CartDrawer.jsx       ← Cart + checkout (POST /orders per item)
│   └── WishlistDrawer.jsx
├── hooks/
│   └── useLockBodyScroll.js
├── pages/
│   ├── Home.jsx             ← Hero, search/filter/sort, product grid, categories
│   ├── About.jsx            ← "Our Story" page (+ AboutPage.css)
│   ├── Login.jsx
│   ├── Register.jsx
│   └── AdminPanel.jsx      ← Protected: role !== 'ADMIN' toh home pe redirect
├── utils/
│   └── decodeToken.js      ← JWT payload decode karta hai (username/role nikalne ke liye)
├── App.jsx                  ← Routing
├── main.jsx
└── index.css
```

## Auth flow kaise kaam karta hai

1. Register/Login `authApi.js` se seedha `auth-service` (8083) ko
   jaate hain — gateway se nahi, kyunki gateway mein `/auth/**` route
   abhi add nahi hua.
2. Login se JWT token milta hai. `decodeToken.js` usse decode karke
   `username` aur `role` nikaalta hai (JWT payload mein already the,
   backend ne `auth-service` step mein isi liye claim mein daala tha).
3. Token, username, role — teeno `localStorage` mein save hote hain.
4. Baad ki saari API calls (`axiosInstance.js` ke through) automatically
   `Authorization: Bearer <token>` header attach karti hain.
5. Navbar aur Admin Panel `localStorage.getItem('role')` check karke
   decide karte hain kya dikhana hai.
