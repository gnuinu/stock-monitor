import { useEffect, useState } from 'react';
import { api, formatKrw, Holding, Quote } from '../api';

interface Props {
  quote: Quote;
  onTraded?: () => void;
}

/** Buy/sell a symbol against the in-memory paper-trading account. */
export default function TradeWidget({ quote, onTraded }: Props) {
  const [quantity, setQuantity] = useState(1);
  const [held, setHeld] = useState<Holding | null>(null);
  const [cash, setCash] = useState<number | null>(null);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    const pf = await api.portfolio();
    setCash(pf.cash);
    setHeld(pf.holdings.find((h) => h.symbol === quote.symbol) ?? null);
  };

  // load current holding/cash on mount and whenever the symbol changes
  useEffect(() => {
    refresh().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quote.symbol]);

  const trade = async (side: 'BUY' | 'SELL') => {
    if (quantity < 1) return;
    setBusy(true);
    setMessage(null);
    try {
      await api.order(quote.symbol, side, quantity);
      await refresh();
      setMessage({ text: `${side === 'BUY' ? '매수' : '매도'} ${quantity}주 체결되었습니다.`, ok: true });
      onTraded?.();
    } catch (e) {
      setMessage({ text: (e as Error).message, ok: false });
    } finally {
      setBusy(false);
    }
  };

  const priceKrw = quote.market === 'NASDAQ' ? quote.price * 1350 : quote.price;
  const estimate = priceKrw * quantity;

  return (
    <div className="card">
      <h2>💼 모의투자</h2>
      <div className="trade-meta">
        <span>예수금 {cash != null ? formatKrw(cash) : '—'}</span>
        <span>보유 {held ? `${held.quantity}주` : '없음'}</span>
      </div>
      <div className="trade-qty">
        <button onClick={() => setQuantity((q) => Math.max(1, q - 1))} disabled={busy}>−</button>
        <input
          type="number"
          min={1}
          value={quantity}
          onChange={(e) => setQuantity(Math.max(1, Math.floor(Number(e.target.value) || 1)))}
        />
        <button onClick={() => setQuantity((q) => q + 1)} disabled={busy}>+</button>
        <span className="trade-estimate">≈ {formatKrw(estimate)}</span>
      </div>
      <div className="trade-actions">
        <button className="buy-btn" onClick={() => trade('BUY')} disabled={busy}>매수</button>
        <button className="sell-btn" onClick={() => trade('SELL')} disabled={busy || !held}>매도</button>
      </div>
      {message && (
        <div className={`trade-msg ${message.ok ? 'ok' : 'err'}`}>{message.text}</div>
      )}
    </div>
  );
}
