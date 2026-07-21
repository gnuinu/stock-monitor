import { SignalReport } from '../api';

const ACTION_LABEL: Record<string, string> = {
  BUY: '매수',
  SELL: '매도',
  HOLD: '중립',
};

export default function SignalPanel({ report }: { report: SignalReport }) {
  // score: -100(매도) .. 100(매수) → needle position 0..100%
  const needle = (report.score + 100) / 2;
  return (
    <div className="card">
      <h2>매매 시그널</h2>
      <div className="score-headline">
        <span className="num">{report.score > 0 ? `+${report.score}` : report.score}</span>
        <span>{report.scoreLabel}</span>
      </div>
      <div className="score-gauge">
        <div className="gauge-track">
          <div className="gauge-needle" style={{ left: `${needle}%` }} />
        </div>
        <div className="gauge-labels">
          <span>매도 -100</span>
          <span>중립 0</span>
          <span>매수 +100</span>
        </div>
      </div>
      <div className="signal-list">
        {report.signals.map((s) => (
          <div key={s.name} className="signal-item">
            <div className="head">
              <span className={`action-chip action-${s.action}`}>{ACTION_LABEL[s.action]}</span>
              <span>{s.name}</span>
              <span className="badge">{s.category}</span>
              <span className="strength">{'●'.repeat(s.strength)}{'○'.repeat(3 - s.strength)}</span>
            </div>
            <div className="desc">{s.description}</div>
          </div>
        ))}
        {report.signals.length === 0 && <div className="desc">현재 감지된 시그널이 없습니다.</div>}
      </div>
      <div className="disclaimer">{report.disclaimer}</div>
    </div>
  );
}
