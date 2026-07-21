import { MemeReport } from '../api';

export default function MemePanel({ report }: { report: MemeReport }) {
  const best = report.best;
  return (
    <div className="card meme-card">
      <h2>🤪 병맛 차트 분석소</h2>
      <div className="meme-best">
        <div className="emoji" aria-hidden="true">{best.emoji}</div>
        <div className="name">{best.name}</div>
        <div className="match">패턴 유사도 {best.score}%</div>
        <div className="tagline">"{best.tagline}"</div>
      </div>
      <div className="meme-analysis">
        <div>{best.analysis}</div>
        <div className="advice">💡 {best.advice}</div>
      </div>
      <div className="meme-candidates">
        {report.candidates.map((c) => (
          <div key={c.id} className="meme-candidate">
            <span style={{ width: 20, textAlign: 'center' }}>{c.emoji}</span>
            <span style={{ width: 110 }}>{c.name}</span>
            <span className="bar-track">
              <span className="bar-fill" style={{ width: `${c.score}%`, display: 'block' }} />
            </span>
            <span className="pct">{c.score}%</span>
          </div>
        ))}
      </div>
      <div className="disclaimer">{report.disclaimer}</div>
    </div>
  );
}
