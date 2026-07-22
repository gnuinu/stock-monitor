import { Link, NavLink, Route, Routes } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StockDetail from './pages/StockDetail';
import Portfolio from './pages/Portfolio';

export default function App() {
  return (
    <div className="layout">
      <header className="topbar">
        <Link to="/">
          <h1>📈 주식 모니터</h1>
        </Link>
        <nav className="main-nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
            대시보드
          </NavLink>
          <NavLink to="/portfolio" className={({ isActive }) => (isActive ? 'active' : '')}>
            모의투자
          </NavLink>
        </nav>
        <span className="live">
          <span className="live-dot" /> 자동 갱신
        </span>
      </header>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/stock/:symbol" element={<StockDetail />} />
        <Route path="/portfolio" element={<Portfolio />} />
      </Routes>
    </div>
  );
}
