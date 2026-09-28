import { Link } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { ArrowRight } from 'lucide-react';
import './AboutPage.css';

// Simple scroll-reveal hook
function useReveal() {
  const ref = useRef(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.unobserve(el);
        }
      },
      { threshold: 0.15 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, visible];
}

// Animated count-up for stats
function Counter({ end, suffix = '', duration = 1400 }) {
  const [ref, visible] = useReveal();
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!visible) return;
    let start = null;
    const step = (ts) => {
      if (!start) start = ts;
      const progress = Math.min((ts - start) / duration, 1);
      setCount(Math.floor(progress * end));
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [visible, end, duration]);

  return (
    <span ref={ref} className="stat-number">
      {count}
      {suffix}
    </span>
  );
}

function Reveal({ children, className = '', delay = 0 }) {
  const [ref, visible] = useReveal();
  return (
    <div
      ref={ref}
      className={`reveal ${visible ? 'is-visible' : ''} ${className}`}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </div>
  );
}

const SERVICES = [
  {
    name: 'API Gateway',
    port: '8080',
    text: 'Spring Cloud Gateway — the single entry point routing /products, /orders and /auth.',
  },
  {
    name: 'Product Service',
    port: '8081',
    text: 'Owns the catalog: categories, pricing and the full specification text for every listing.',
  },
  {
    name: 'Order Service',
    port: '8082',
    text: 'Places orders, looks products up over OpenFeign and merges repeat pending orders.',
  },
  {
    name: 'Auth Service',
    port: '8083',
    text: 'Issues stateless JWT bearer tokens; passwords are stored as BCrypt hashes.',
  },
  {
    name: 'Eureka Server',
    port: '8761',
    text: 'Service registry where every microservice announces itself on start-up.',
  },
];

const VALUES = [
  {
    title: 'Clarity over clutter',
    text: 'Every listing has a verified category, detailed specifications and the exact price — no hidden fees.',
  },
  {
    title: 'No pay-to-rank listings',
    text: 'Results strictly match your filters, never a sponsored position.',
  },
  {
    title: 'Built for comparing',
    text: 'Filter by category and price in real time, and open quick views without leaving the page.',
  },
];

export default function AboutPage() {
  return (
    <div className="page about-page">
      {/* Intro */}
      <section className="about-intro container">
        <span className="eyebrow">Our Story</span>
        <h1 className="about-title">
          Technology shopping,
          <br />
          <em>without the noise.</em>
        </h1>
        <p className="about-lead">
          ShopEase started with a simple frustration: buying a laptop or a phone online meant
          wading through sponsored listings, vague specifications and prices that changed at
          checkout. We built a store that gets out of your way — honest specs, honest prices and a
          checkout that just works.
        </p>

        <div className="about-stats">
          <div className="stat">
            <Counter end={500} suffix="+" />
            <span className="stat-label">Hardware listings</span>
          </div>
          <div className="stat">
            <Counter end={5} />
            <span className="stat-label">Microservices</span>
          </div>
          <div className="stat">
            <Counter end={100} suffix="%" />
            <span className="stat-label">Verified specs</span>
          </div>
        </div>
      </section>

      {/* Split story */}
      <Reveal className="about-split container">
        <figure className="about-figure">
          <img src="/about-story.jpg" alt="A laptop, headphones and a phone on a desk by a window" />
        </figure>
        <div className="about-copy">
          <span className="eyebrow">What we're building</span>
          <h2>The details that matter, before you buy.</h2>
          <p>
            Every product on ShopEase carries the information that actually matters — clear
            hardware specifications, transparent pricing and the right category, without the
            fluff. Filter by what you need, compare options side by side and check out instantly.
          </p>
          <p>
            We eliminate sponsored listings and paid rankings. What you see matches your exact
            search, delivered by a fast, stateless platform.
          </p>
          <Link to="/#products" className="text-link">
            Browse the collection <ArrowRight size={16} strokeWidth={1.5} />
          </Link>
        </div>
      </Reveal>

      {/* Values */}
      <section className="section section-alt">
        <div className="container">
          <header className="section-head">
            <span className="eyebrow">Principles</span>
            <h2 className="section-title">What we stand for</h2>
          </header>

          <div className="values-grid">
            {VALUES.map((v, i) => (
              <Reveal key={v.title} delay={i * 120}>
                <div className="value">
                  <span className="value-num">0{i + 1}</span>
                  <h3>{v.title}</h3>
                  <p>{v.text}</p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Architecture */}
      <Reveal className="section">
        <div className="container">
          <header className="section-head">
            <span className="eyebrow">Under the hood</span>
            <h2 className="section-title">Built on microservices</h2>
            <p className="section-sub">
              Five independent Spring Boot services behind one gateway, discovered through Eureka.
            </p>
          </header>

          <div className="services-grid">
            {SERVICES.map((s) => (
              <div key={s.name} className="service">
                <span className="service-port">Port {s.port}</span>
                <h3>{s.name}</h3>
                <p>{s.text}</p>
              </div>
            ))}
          </div>
        </div>
      </Reveal>

      {/* CTA */}
      <section className="cta-band">
        <div className="container cta-inner">
          <div>
            <h2>Looking for your next device?</h2>
            <p>Browse the collection and see the platform in action.</p>
          </div>
          <Link to="/#products" className="btn btn-dark btn-lg">
            Shop Now
          </Link>
        </div>
      </section>
    </div>
  );
}
