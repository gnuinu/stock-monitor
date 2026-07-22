import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, formatKrw, Portfolio as PortfolioData } from '../api';

function pnlClass(v: number): string {
  if (v > 0) return 'up';
  if (v < 0) return 'down';
  return 'flat';
}

function signedKrw(v: number): string {
  return `${v > 0 ? '+' : v < 0 ? '−' : ''}${formatKrw(Math.abs(v))}`;
}

function signedPct(v: number): string {
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}%`;
}

export default function Portfolio() {
  const [pf, setPf] = useState<PortfolioData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const data = await api.portfolio();
        if (alive) {
          setPf(data);
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

  const reset = async () => {
    if (!confirm('모의투자 계좌를 초기화할까요? 모든 보유 종목과 손익이 사라집니다.')) {
      return;
    }
    try {
      setPf(await api.resetPortfolio());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (error && !pf) {
    return <div className="error-box">포트폴리오를 불러오지 못했습니다: {error}</div>;
  }
  if (!pf) {
    return <div className="loading">불러오는 중...</div>;
  }

  return (
    <>
      <div className="detail-header" style={{ justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>💼 모의투자 포트폴리오</h2>
        <button className="reset-btn" onClick={reset}>계좌 초기화</button>
      </div>

      <section className="grid-summary">
        <div className="card stat-tile">
          <div className="label">총 평가금액</div>
          <div className="value">{formatKrw(pf.totalValue)}</div>
          <div className={`sub ${pnlClass(pf.totalPnl)}`}>
            {signedKrw(pf.totalPnl)} ({signedPct(pf.totalPnlPercent)})
          </div>
        </div>
        <div className="card stat-tile">
          <div className="label">예수금 (현금)</div>
          <div className="value">{formatKrw(pf.cash)}</div>
          <div className="sub">주식 평가 {formatKrw(pf.holdingsValue)}</div>
        </div>
        <div className="card stat-tile">
          <div className="label">손익</div>
          <div className={`value ${pnlClass(pf.unrealizedPnl)}`}>{signedKrw(pf.unrealizedPnl)}</div>
          <div className="sub">평가손익 · 실현손익 {signedKrw(pf.realizedPnl)}</div>
        </div>
      </section>

      <section className="card">
        <h2>보유 종목</h2>
        {pf.holdings.length === 0 ? (
          <div className="sub" style={{ padding: '16px 2px' }}>
            보유 종목이 없습니다. 종목 상세 페이지에서 매수해 보세요.{' '}
            <Link to="/" className="back-link">관심 종목 보기 →</Link>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="watchlist">
              <thead>
                <tr>
                  <th>종목</th>
                  <th>수량</th>
                  <th>평균단가</th>
                  <th>현재가</th>
                  <th>평가금액</th>
                  <th>평가손익</th>
                  <th>수익률</th>
                </tr>
              </thead>
              <tbody>
                {pf.holdings.map((h) => (
                  <tr key={h.symbol}>
                    <td>
                      <Link to={`/stock/${h.symbol}`}>
                        <span className="stock-name">{h.name}</span>
                        <span className="stock-symbol">{h.symbol}</span>
                      </Link>
                    </td>
                    <td>{h.quantity.toLocaleString()}주</td>
                    <td>{formatKrw(h.avgPrice)}</td>
                    <td>{formatKrw(h.currentPrice)}</td>
                    <td>{formatKrw(h.value)}</td>
                    <td className={pnlClass(h.pnl)}>{signedKrw(h.pnl)}</td>
                    <td className={pnlClass(h.pnlPercent)}>{signedPct(h.pnlPercent)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="disclaimer">
          모의투자는 참고용이며 실제 체결·수수료·세금·환율 변동을 반영하지 않습니다.
          미국 종목은 1달러 = {pf.usdKrw.toLocaleString()}원 고정 환율로 환산합니다.
        </div>
      </section>
    </>
  );
}
