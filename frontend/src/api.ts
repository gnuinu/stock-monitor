const BASE = '/api';

export interface Quote {
  symbol: string;
  name: string;
  market: string;
  sector: string;
  price: number;
  change: number;
  changePercent: number;
  open: number;
  dayHigh: number;
  dayLow: number;
  prevClose: number;
  volume: number;
  sparkline: number[];
  updatedAt: string;
}

export interface CandleDto {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface Point {
  time: string;
  value: number;
}

export interface VolumePoint {
  time: string;
  value: number;
  up: boolean;
}

export interface Indicators {
  sma5: Point[];
  sma20: Point[];
  sma60: Point[];
  sma120: Point[];
  bollinger: { upper: Point[]; middle: Point[]; lower: Point[] };
  rsi: Point[];
  macd: { macd: Point[]; signal: Point[]; histogram: Point[] };
  stochastic: { k: Point[]; d: Point[] };
  volume: VolumePoint[];
}

export interface Signal {
  name: string;
  category: string;
  action: 'BUY' | 'SELL' | 'HOLD';
  strength: number;
  description: string;
}

export interface SignalReport {
  signals: Signal[];
  score: number;
  scoreLabel: string;
  disclaimer: string;
}

export interface MemeVerdict {
  id: string;
  name: string;
  emoji: string;
  score: number;
  tagline: string;
  analysis: string;
  advice: string;
}

export interface MemeReport {
  symbol: string;
  windowDays: number;
  totalReturnPct: number;
  best: MemeVerdict;
  candidates: MemeVerdict[];
  disclaimer: string;
}

export interface MarketSummary {
  advancing: number;
  declining: number;
  unchanged: number;
  topGainers: Quote[];
  topLosers: Quote[];
  dataSource: string;
  fallbackSymbols: string[];
  updatedAt: string;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) {
    throw new Error(`API ${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  stocks: () => get<Quote[]>('/stocks'),
  stock: (symbol: string) => get<Quote>(`/stocks/${symbol}`),
  candles: (symbol: string, days = 0) => get<CandleDto[]>(`/stocks/${symbol}/candles?days=${days}`),
  indicators: (symbol: string, days = 0) => get<Indicators>(`/stocks/${symbol}/indicators?days=${days}`),
  signals: (symbol: string) => get<SignalReport>(`/stocks/${symbol}/signals`),
  meme: (symbol: string) => get<MemeReport>(`/stocks/${symbol}/meme`),
  marketSummary: () => get<MarketSummary>('/market/summary'),
};

export function formatPrice(q: { price: number; market: string }): string {
  if (q.market === 'NASDAQ') {
    return `$${q.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
  return `${q.price.toLocaleString('ko-KR')}원`;
}

export function formatVolume(v: number): string {
  if (v >= 1_0000_0000) return `${(v / 1_0000_0000).toFixed(1)}억`;
  if (v >= 1_0000) return `${(v / 1_0000).toFixed(1)}만`;
  return v.toLocaleString();
}
