import { Link, Route, Routes } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import StockDetail from './pages/StockDetail';

export default function App() {
  return (
    <div className="layout">
      <header className="topbar">
        <Link to="/">
          <h1>📈 주식 모니터</h1>
        </Link>
        <span className="tagline">기술적 지표 + 병맛 차트 분석</span>
        <span className="live">
          <span className="live-dot" /> 자동 갱신
        </span>
      </header>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/stock/:symbol" element={<StockDetail />} />
      </Routes>
    </div>
  );
}
