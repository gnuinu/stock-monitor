import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  api,
  CandleDto,
  formatPrice,
  formatVolume,
  Indicators,
  MemeReport,
  Quote,
  SignalReport,
} from '../api';
import StockCharts from '../components/StockCharts';
import SignalPanel from '../components/SignalPanel';
import MemePanel from '../components/MemePanel';
import TradeWidget from '../components/TradeWidget';

const RANGES = [
  { label: '3개월', days: 63 },
  { label: '6개월', days: 126 },
  { label: '1년', days: 252 },
  { label: '전체', days: 0 },
] as const;

export default function StockDetail() {
  const { symbol = '' } = useParams();
  const [quote, setQuote] = useState<Quote | null>(null);
  const [candles, setCandles] = useState<CandleDto[] | null>(null);
  const [indicators, setIndicators] = useState<Indicators | null>(null);
  const [signals, setSignals] = useState<SignalReport | null>(null);
  const [meme, setMeme] = useState<MemeReport | null>(null);
  const [days, setDays] = useState<number>(126);
  const [error, setError] = useState<string | null>(null);

  // quote + analysis: light polling
  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const [q, s, m] = await Promise.all([api.stock(symbol), api.signals(symbol), api.meme(symbol)]);
        if (alive) {
          setQuote(q);
          setSignals(s);
          setMeme(m);
          setError(null);
        }
      } catch (e) {
        if (alive) setError((e as Error).message);
      }
    };
    load();
    const timer = setInterval(load, 7000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [symbol]);

  // candles + indicators: on range change and slower refresh
  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const [c, ind] = await Promise.all([api.candles(symbol, days), api.indicators(symbol, days)]);
        if (alive) {
          setCandles(c);
          setIndicators(ind);
        }
      } catch (e) {
        if (alive) setError((e as Error).message);
      }
    };
    load();
    const timer = setInterval(load, 15000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [symbol, days]);

  if (error && !quote) {
    return <div className="error-box">데이터를 불러오지 못했습니다: {error}</div>;
  }
  if (!quote) {
    return <div className="loading">불러오는 중...</div>;
  }

  const dirClass = quote.change > 0 ? 'up' : quote.change < 0 ? 'down' : 'flat';
  const sign = quote.change > 0 ? '+' : '';

  return (
    <>
      <Link to="/" className="back-link">← 관심 종목으로</Link>
      <div className="detail-header" style={{ marginTop: 10 }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>
          {quote.name} <span className="stock-symbol">{quote.symbol}</span>
          <span className="badge">{quote.market}</span>
          <span className="badge">{quote.sector}</span>
        </h2>
        <span className={`price ${dirClass}`}>{formatPrice(quote)}</span>
        <span className={`change ${dirClass}`}>
          {sign}{quote.change.toLocaleString()} ({sign}{quote.changePercent.toFixed(2)}%)
        </span>
        <span className="meta">
          시가 {quote.open.toLocaleString()} · 고가 {quote.dayHigh.toLocaleString()} · 저가{' '}
          {quote.dayLow.toLocaleString()} · 거래량 {formatVolume(quote.volume)}
        </span>
      </div>

      <div className="range-row">
        {RANGES.map((r) => (
          <button
            key={r.label}
            className={days === r.days ? 'active' : ''}
            onClick={() => setDays(r.days)}
          >
            {r.label}
          </button>
        ))}
      </div>

      <div className="detail-grid">
        {candles && indicators ? (
          <StockCharts candles={candles} indicators={indicators} />
        ) : (
          <div className="loading">차트를 불러오는 중...</div>
        )}
        <div className="side-stack">
          <TradeWidget quote={quote} />
          {signals && <SignalPanel report={signals} />}
          {meme && <MemePanel report={meme} />}
        </div>
      </div>
    </>
  );
}
