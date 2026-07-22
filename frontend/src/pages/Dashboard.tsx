import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, formatPrice, formatVolume, MarketSummary, Quote } from '../api';
import Sparkline from '../components/Sparkline';

const FAV_KEY = 'stock-monitor:favorites';

function loadFavorites(): string[] {
  try {
    return JSON.parse(localStorage.getItem(FAV_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function changeClass(v: number): string {
  if (v > 0) return 'up';
  if (v < 0) return 'down';
  return 'flat';
}

function signed(v: number): string {
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}`;
}

const SOURCE_LABELS: Record<string, string> = {
  yahoo: 'Yahoo Finance 실시간',
};

function DataSourceBadge({ summary }: { summary: MarketSummary }) {
  // A real provider with no symbols on fallback = fully live; any fallback or simulated = not live.
  const isReal = summary.dataSource !== 'simulated';
  const label = SOURCE_LABELS[summary.dataSource] ?? `${summary.dataSource} 실시간`;
  if (isReal && summary.fallbackSymbols.length === 0) {
    return <span className="source-badge live">{label}</span>;
  }
  if (isReal && summary.fallbackSymbols.length > 0) {
    const n = summary.fallbackSymbols.length;
    return (
      <span className="source-badge partial" title={`시뮬레이션 대체: ${summary.fallbackSymbols.join(', ')}`}>
        일부 실시간 · {n}종목 대체
      </span>
    );
  }
  return <span className="source-badge sim">시뮬레이션 데이터</span>;
}

export default function Dashboard() {
  const [quotes, setQuotes] = useState<Quote[] | null>(null);
  const [summary, setSummary] = useState<MarketSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [market, setMarket] = useState('ALL');
  const [favOnly, setFavOnly] = useState(false);
  const [favorites, setFavorites] = useState<string[]>(loadFavorites);
  const navigate = useNavigate();

  const toggleFavorite = (symbol: string) => {
    setFavorites((prev) => {
      const next = prev.includes(symbol) ? prev.filter((s) => s !== symbol) : [...prev, symbol];
      localStorage.setItem(FAV_KEY, JSON.stringify(next));
      return next;
    });
  };

  const markets = useMemo(
    () => ['ALL', ...Array.from(new Set((quotes ?? []).map((q) => q.market)))],
    [quotes],
  );

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (quotes ?? []).filter((q) => {
      if (market !== 'ALL' && q.market !== market) return false;
      if (favOnly && !favorites.includes(q.symbol)) return false;
      if (term && !q.name.toLowerCase().includes(term) && !q.symbol.toLowerCase().includes(term)) {
        return false;
      }
      return true;
    });
  }, [quotes, search, market, favOnly, favorites]);

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
          <div className="label">
            시장 분위기 <DataSourceBadge summary={summary} />
          </div>
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
        <div className="watchlist-header">
          <h2 style={{ margin: 0 }}>관심 종목</h2>
          <div className="watchlist-controls">
            <input
              className="search-input"
              type="search"
              placeholder="종목명·코드 검색"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select className="filter-select" value={market} onChange={(e) => setMarket(e.target.value)}>
              {markets.map((m) => (
                <option key={m} value={m}>{m === 'ALL' ? '전체 시장' : m}</option>
              ))}
            </select>
            <button
              className={`fav-toggle ${favOnly ? 'active' : ''}`}
              onClick={() => setFavOnly((v) => !v)}
            >
              ⭐ 즐겨찾기{favorites.length > 0 ? ` (${favorites.length})` : ''}
            </button>
          </div>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table className="watchlist">
            <thead>
              <tr>
                <th aria-label="즐겨찾기" style={{ width: 28 }}></th>
                <th>종목</th>
                <th>현재가</th>
                <th>등락</th>
                <th>등락률</th>
                <th>거래량</th>
                <th>30일 추이</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((q) => (
                <tr key={q.symbol} onClick={() => navigate(`/stock/${q.symbol}`)}>
                  <td onClick={(e) => { e.stopPropagation(); toggleFavorite(q.symbol); }} style={{ textAlign: 'center', cursor: 'pointer' }}>
                    <span className={favorites.includes(q.symbol) ? 'star on' : 'star'}>★</span>
                  </td>
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
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', color: 'var(--muted)', padding: 24 }}>
                    조건에 맞는 종목이 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
