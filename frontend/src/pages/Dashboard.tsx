import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, formatPrice, formatVolume, MarketSummary, Quote } from '../api';
import Sparkline from '../components/Sparkline';

function changeClass(v: number): string {
  if (v > 0) return 'up';
  if (v < 0) return 'down';
  return 'flat';
}

function signed(v: number): string {
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}`;
}

export default function Dashboard() {
  const [quotes, setQuotes] = useState<Quote[] | null>(null);
  const [summary, setSummary] = useState<MarketSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const [q, s] = await Promise.all([api.stocks(), api.marketSummary()]);
        if (alive) {
          setQuotes(q);
          setSummary(s);
          setError(null);
        }
      } catch (e) {
        if (alive) setError((e as Error).message);
      }
    };
    load();
    const timer = setInterval(load, 5000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  if (error && !quotes) {
    return <div className="error-box">백엔드에 연결할 수 없습니다: {error}</div>;
  }
  if (!quotes || !summary) {
    return <div className="loading">시세를 불러오는 중...</div>;
  }

  return (
    <>
      <section className="grid-summary">
        <div className="card stat-tile">
          <div className="label">시장 분위기</div>
          <div className="value">
            <span className="up">▲ {summary.advancing}</span>{' '}
            <span className="down">▼ {summary.declining}</span>
          </div>
          <div className="sub">상승 {summary.advancing} · 하락 {summary.declining} · 보합 {summary.unchanged}</div>
        </div>
        <div className="card stat-tile">
          <div className="label">상승률 상위</div>
          <div className="movers" style={{ marginTop: 6 }}>
            {summary.topGainers.map((q) => (
              <div key={q.symbol} className="mover-row">
                <span className="nm">{q.name}</span>
                <span className={`pct ${changeClass(q.changePercent)}`}>{signed(q.changePercent)}%</span>
              </div>
            ))}
          </div>
        </div>
        <div className="card stat-tile">
          <div className="label">하락률 상위</div>
          <div className="movers" style={{ marginTop: 6 }}>
            {summary.topLosers.map((q) => (
              <div key={q.symbol} className="mover-row">
                <span className="nm">{q.name}</span>
                <span className={`pct ${changeClass(q.changePercent)}`}>{signed(q.changePercent)}%</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="card">
        <h2>관심 종목</h2>
        <div style={{ overflowX: 'auto' }}>
          <table className="watchlist">
            <thead>
              <tr>
                <th>종목</th>
                <th>현재가</th>
                <th>등락</th>
                <th>등락률</th>
                <th>거래량</th>
                <th>30일 추이</th>
              </tr>
            </thead>
            <tbody>
              {quotes.map((q) => (
                <tr key={q.symbol} onClick={() => navigate(`/stock/${q.symbol}`)}>
                  <td>
                    <span className="stock-name">{q.name}</span>
                    <span className="stock-symbol">{q.symbol}</span>
                    <span className="badge">{q.market}</span>
                  </td>
                  <td>{formatPrice(q)}</td>
                  <td className={changeClass(q.change)}>{signed(q.change)}</td>
                  <td className={changeClass(q.changePercent)}>{signed(q.changePercent)}%</td>
                  <td>{formatVolume(q.volume)}</td>
                  <td>
                    <Sparkline data={q.sparkline} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
