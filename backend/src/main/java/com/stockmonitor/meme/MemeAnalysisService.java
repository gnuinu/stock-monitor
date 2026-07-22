package com.stockmonitor.meme;

import com.stockmonitor.model.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 병맛 차트 분석 엔진 — 인터넷 밈에서 유래한 차트 패턴을 "진지하게" 판독한다.
 * 최근 60거래일 종가를 0~1로 정규화한 뒤 각 밈 패턴과의 유사도를 0~100점으로 채점하고,
 * 상위 후보에 전문가(?) 코멘트를 붙여 돌려준다. 어디까지나 재미용이다.
 */
@Service
public class MemeAnalysisService {

    private static final int WINDOW = 60;

    public record MemeVerdict(String id, String name, String emoji, int score,
                              String tagline, String analysis, String advice) {
    }

    public Map<String, Object> analyze(String symbol, List<Candle> candles) {
        int n = Math.min(WINDOW, candles.size());
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double[] close = window.stream().mapToDouble(Candle::getClose).toArray();
        long[] volume = window.stream().mapToLong(Candle::getVolume).toArray();
        double[] z = normalize(close);
        double totalReturn = (close[n - 1] - close[0]) / close[0] * 100;

        List<MemeVerdict> verdicts = new ArrayList<>();
        verdicts.add(giyeong(z, totalReturn));
        verdicts.add(forwardRoll(z, totalReturn));
        verdicts.add(backwardRoll(z, totalReturn));
        verdicts.add(rocket(close, z, totalReturn));
        verdicts.add(waterfall(close, z, totalReturn));
        verdicts.add(flatline(close));
        verdicts.add(vRebound(z, totalReturn));
        verdicts.add(dishwashing(z, close, volume, totalReturn));
        verdicts.add(staircase(close, totalReturn));
        verdicts.add(basement(close, z));
        verdicts.add(rollercoaster(z));
        verdicts.add(heavenStairs(close, totalReturn));
        verdicts.add(crabWalk(close, totalReturn));
        verdicts.add(zen());

        verdicts.sort(Comparator.comparingInt(MemeVerdict::score).reversed());
        List<MemeVerdict> top = verdicts.subList(0, 3);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", symbol);
        out.put("windowDays", n);
        out.put("totalReturnPct", round1(totalReturn));
        out.put("best", top.get(0));
        out.put("candidates", top);
        out.put("disclaimer", "본 분석은 100% 재미용입니다. 이걸 보고 매매하시면 기영이가 됩니다.");
        return out;
    }

    // ── 패턴 판독기들 ─────────────────────────────────────────────────

    /** 기영이 차트: 평평한 뒤통수(고점 횡보) 후 이마 라인 수직 낙하. */
    private MemeVerdict giyeong(double[] z, double totalReturn) {
        int n = z.length;
        int split = (int) (n * 0.65);
        double flatRange = range(z, 0, split);
        double headLevel = mean(z, 0, split);
        double drop = headLevel - z[n - 1];

        double flatScore = clamp01((0.40 - flatRange) / 0.40);
        double headScore = clamp01((headLevel - 0.45) / 0.45);
        double dropScore = clamp01(drop / 0.55);
        int score = (int) (100 * flatScore * 0.35 + 100 * headScore * 0.15 + 100 * dropScore * 0.50);

        return new MemeVerdict("giyeong", "기영이 차트", "👦",
                score,
                "검은 머리 휘날리며... 앞머리 라인이 완성되고 있습니다.",
                String.format("전반부 %d일간 뒤통수처럼 평평하게 횡보하다가 후반부에 이마 라인을 그리며 흘러내렸습니다. "
                        + "기영이 옆모습 유사도가 상당히 높게 측정됩니다. 검정 고무신 전문가 평: \"이건 기영이다.\"", (int) (z.length * 0.65)),
                totalReturn < -10 ? "기철이 형한테 물어보세요. 손절은 빠를수록 머리숱에 좋습니다."
                        : "아직 앞머리는 지킬 수 있습니다. 이탈 시 미련 없이.");
    }

    /** 앞구르기 차트: 올라갔다가 데굴— 하고 둥글게 말리며 내려온다. */
    private MemeVerdict forwardRoll(double[] z, double totalReturn) {
        int n = z.length;
        int peak = argmax(z);
        double rise = z[peak] - z[0];
        double fall = z[peak] - z[n - 1];
        boolean peakCentered = peak > n * 0.25 && peak < n * 0.8;

        double smooth = smoothness(z);
        double score = 0;
        if (peakCentered) {
            score = 100 * clamp01(rise / 0.4) * 0.4 + 100 * clamp01(fall / 0.4) * 0.4 + smooth * 20;
        }
        return new MemeVerdict("forward-roll", "앞구르기 차트", "🤸",
                (int) score,
                "힘차게 올라가서... 데굴데굴 굴러 내려왔습니다.",
                "상승 → 정점 → 둥글게 말리는 하락. 교과서적인 앞구르기 자세입니다. 착지 지점은 아직 미정이며, "
                        + "체조 심판진은 회전의 완성도에 9.5점을 줬지만 계좌 점수는 별개입니다.",
                "구르기가 끝나면 일어나는 게 국룰이지만, 주식은 국룰을 잘 안 지킵니다.");
    }

    /** 뒤구르기 차트: 내려갔다가 둥글게 말아 올라오는 역구르기. */
    private MemeVerdict backwardRoll(double[] z, double totalReturn) {
        int n = z.length;
        int trough = argmin(z);
        double fall = z[0] - z[trough];
        double rise = z[n - 1] - z[trough];
        boolean centered = trough > n * 0.2 && trough < n * 0.75;

        double smooth = smoothness(z);
        double score = 0;
        if (centered) {
            score = 100 * clamp01(fall / 0.4) * 0.4 + 100 * clamp01(rise / 0.4) * 0.4 + smooth * 20;
        }
        return new MemeVerdict("backward-roll", "뒤구르기 차트", "🤸‍♂️",
                (int) score,
                "떨어질 땐 아팠지만, 뒤구르기로 예쁘게 일어나는 중입니다.",
                "하락 → 바닥 다지기 → 둥근 반등. 낙법을 완벽하게 소화한 뒤구르기 폼입니다. "
                        + "바닥에서 구른 흔적(거래량)이 남아 있으며, 관절(지지선)에 무리는 없어 보입니다.",
                "일어난 김에 어깨(전고점)까지 펴는지 지켜보세요. 다시 눕는 경우도 흔합니다.");
    }

    /** 우상향 존버 로켓: 꾸준하고 강한 상승. */
    private MemeVerdict rocket(double[] close, double[] z, double totalReturn) {
        double r2 = linearR2(z);
        double slopeUp = z[z.length - 1] - z[0];
        double score = 0;
        if (slopeUp > 0) {
            score = clamp01(totalReturn / 30) * 60 + r2 * 40;
        }
        return new MemeVerdict("rocket", "가즈아 로켓 차트", "🚀",
                (int) score,
                String.format("%d일 수익률 %+.1f%%. 연료 만땅, 궤도 진입 중.", z.length, totalReturn),
                "저점과 고점을 착실히 높이는 교과서적 우상향입니다. 탑승객들의 함성(\"가즈아\")이 들리는 듯하며, "
                        + "창밖으로 손절한 사람들이 점점 작아 보입니다.",
                "로켓의 유일한 단점: 연료가 떨어지는 시점은 아무도 모릅니다. 분할 익절이 산소마스크입니다.");
    }

    /** 폭포수 차트: 시원하게 계속 떨어진다. */
    private MemeVerdict waterfall(double[] close, double[] z, double totalReturn) {
        double r2 = linearR2(z);
        double slopeDown = z[0] - z[z.length - 1];
        double score = 0;
        if (slopeDown > 0) {
            score = clamp01(-totalReturn / 30) * 60 + r2 * 40;
        }
        return new MemeVerdict("waterfall", "폭포수 차트", "🌊",
                (int) score,
                "나이아가라 실시간 중계 중입니다. 시원~합니다.",
                String.format("%d일간 %.1f%% 하락하며 낙차 큰 폭포를 완성했습니다. 물줄기가 곧게 뻗어 "
                        + "R²까지 높은, 폭포로서는 매우 아름다운 형태입니다. 계좌로서는 아닙니다.", z.length, Math.abs(totalReturn)),
                "폭포 아래에서 물을 맞으며 버티는 것을 '존버'라 하나, 저체온증에 유의하세요.");
    }

    /** 심정지 차트: 변동성 실종. */
    private MemeVerdict flatline(double[] close) {
        double mean = mean(close, 0, close.length);
        double amplitude = (max(close) - min(close)) / mean * 100;
        double score = clamp01((5 - amplitude) / 5) * 100;
        return new MemeVerdict("flatline", "심정지 차트", "🫀",
                (int) score,
                "삐——————. 제세동기를 준비해 주세요.",
                String.format("최근 변동폭이 %.1f%%에 불과합니다. 심전도였다면 이미 코드블루 상황. "
                        + "거래원들도 하품 중이고, 호가창에는 거미줄이 쳐졌습니다.", amplitude),
                "심정지 차트의 유일한 장점: 잃지도 않는다는 것. 단, 기회비용은 조용히 죽어갑니다.");
    }

    /** V자 반등: 급락 후 급반등. */
    private MemeVerdict vRebound(double[] z, double totalReturn) {
        int n = z.length;
        int trough = argmin(z);
        double leftDrop = z[0] - z[trough];
        double rightRise = z[n - 1] - z[trough];
        boolean centered = trough > n * 0.3 && trough < n * 0.7;
        double score = 0;
        if (centered) {
            double sharpness = 1 - smoothness(z);
            score = clamp01(leftDrop / 0.35) * 40 + clamp01(rightRise / 0.35) * 40 + sharpness * 20;
        }
        return new MemeVerdict("v-rebound", "브이(V) 차트", "✌️",
                (int) score,
                "떨어질 때 판 사람 vs 바닥에서 산 사람. 승자가 정해졌습니다.",
                "수직 낙하 후 수직 반등이라는 V자 각본입니다. 바닥에서 '이제 끝났다'던 사람들의 목소리가 "
                        + "고점에서 '내가 살 줄 알았다'로 바뀌는 마법의 구간입니다.",
                "V자의 오른쪽 다리는 종종 W의 시작이기도 합니다. 두 번째 다리를 조심하세요.");
    }

    /** 설거지 차트: 거래량 동반 급등 후 수직 낙하 — 개미들 설거지 완료. */
    private MemeVerdict dishwashing(double[] z, double[] close, long[] volume, double totalReturn) {
        int n = z.length;
        int peak = argmax(z);
        double rise = z[peak] - z[0];
        double fall = z[peak] - z[n - 1];
        boolean peakLate = peak > n * 0.3;

        double avgVol = 0;
        for (long v : volume) avgVol += v;
        avgVol /= n;
        double peakVolRatio = volume[Math.min(peak, n - 1)] / Math.max(1, avgVol);

        double score = 0;
        if (peakLate && rise > 0.25 && fall > 0.25) {
            score = clamp01(rise / 0.5) * 35 + clamp01(fall / 0.5) * 40 + clamp01((peakVolRatio - 1) / 2) * 25;
        }
        return new MemeVerdict("dishwashing", "설거지 차트", "🍽️",
                (int) score,
                "설거지가 끝났습니다. 그릇이 반짝반짝하네요.",
                String.format("고점 부근 거래량이 평균의 %.1f배로 폭발한 뒤 가격이 흘러내렸습니다. "
                        + "누군가는 그릇을 팔았고, 누군가는 그릇을 닦고 있습니다. 세제 냄새가 여기까지 납니다.", peakVolRatio),
                "설거지 당한 그릇은 다시 밥을 담을 수 있지만, 시간이 꽤 걸립니다. 물기부터 말리세요.");
    }

    /** 계단식 하락: 툭... 횡보... 툭... 횡보... */
    private MemeVerdict staircase(double[] close, double totalReturn) {
        int bigDrops = 0;
        int calmDays = 0;
        for (int i = 1; i < close.length; i++) {
            double ret = (close[i] - close[i - 1]) / close[i - 1] * 100;
            if (ret < -3) bigDrops++;
            if (Math.abs(ret) < 1) calmDays++;
        }
        double score = 0;
        if (totalReturn < -5 && bigDrops >= 3) {
            score = clamp01(bigDrops / 6.0) * 50 + clamp01(calmDays / (close.length * 0.6)) * 30
                    + clamp01(-totalReturn / 25) * 20;
        }
        return new MemeVerdict("staircase", "지옥의 계단 차트", "🪜",
                (int) score,
                "한 층씩 내려가는 중입니다. 엘리베이터는 고장났습니다.",
                String.format("%d번의 급락 계단과 그 사이 평온한 층계참으로 이루어진 완벽한 하강 계단입니다. "
                        + "'이제 바닥이겠지' 할 때마다 아래층이 또 있었습니다.", bigDrops),
                "계단은 언젠가 끝나지만, 지하 몇 층까지 있는지는 시공사(세력)만 압니다.");
    }

    /** 지하실 차트: 신저가 행진 — 지하실 밑에 지하실. */
    private MemeVerdict basement(double[] close, double[] z) {
        int n = close.length;
        double lo = min(close);
        boolean atLow = close[n - 1] <= lo * 1.03;
        int newLows = 0;
        double runningMin = close[0];
        for (int i = 1; i < n; i++) {
            if (close[i] < runningMin) {
                runningMin = close[i];
                if (i > n / 2) newLows++;
            }
        }
        double score = 0;
        if (atLow) {
            score = 40 + clamp01(newLows / 10.0) * 40 + clamp01((z[0] - z[n - 1]) / 0.6) * 20;
        }
        return new MemeVerdict("basement", "지하실 차트", "🕳️",
                (int) score,
                "여기가 바닥인 줄 알았는데, 엘리베이터에 B2 버튼이 있었습니다.",
                String.format("최근 구간에서만 신저가를 %d번 갱신하며 지하 탐사를 이어가고 있습니다. "
                        + "'바닥 밑에 지하실, 지하실 밑에 맨틀'이라는 격언이 실시간으로 증명되는 중입니다.", newLows),
                "지하에서는 랜턴(분할 매수 계획) 없이 움직이지 마세요. 맨틀은 생각보다 뜨겁습니다.");
    }

    /** 롤러코스터 차트: 큰 폭으로 오르내리기를 반복. */
    private MemeVerdict rollercoaster(double[] z) {
        double amp = range(z, 0, z.length);
        double choppiness = 1 - smoothness(z);
        int bigSwings = 0;
        for (int i = 1; i < z.length; i++) {
            if (Math.abs(z[i] - z[i - 1]) > 0.08) {
                bigSwings++;
            }
        }
        double score = clamp01(amp / 0.7) * 40 + choppiness * 35 + clamp01(bigSwings / 8.0) * 25;
        return new MemeVerdict("rollercoaster", "롤러코스터 차트", "🎢",
                (int) score,
                "두 손 들고 타세요! 안전바 확인하셨죠?",
                String.format("큰 폭의 상승과 하락이 %d번이나 반복됐습니다. 방향성은 없지만 스릴 하나는 최고입니다. "
                        + "심장약한 분들은 계좌를 보지 않는 것이 정신건강에 이롭습니다.", bigSwings),
                "롤러코스터는 결국 출발점으로 돌아옵니다. 타는 값(수수료)만 계속 나갑니다.");
    }

    /** 천국의 계단 차트: 급등과 횡보를 반복하며 한 계단씩 상승. */
    private MemeVerdict heavenStairs(double[] close, double totalReturn) {
        int bigJumps = 0;
        int calmDays = 0;
        for (int i = 1; i < close.length; i++) {
            double ret = (close[i] - close[i - 1]) / close[i - 1] * 100;
            if (ret > 3) bigJumps++;
            if (Math.abs(ret) < 1) calmDays++;
        }
        double score = 0;
        if (totalReturn > 5 && bigJumps >= 3) {
            score = clamp01(bigJumps / 6.0) * 50 + clamp01(calmDays / (close.length * 0.6)) * 30
                    + clamp01(totalReturn / 25) * 20;
        }
        return new MemeVerdict("heaven-stairs", "천국의 계단 차트", "😇",
                (int) score,
                "한 층씩 착실히 올라가는 중입니다. 엘리베이터보다 안전합니다.",
                String.format("%d번의 급등 계단과 그 사이 횡보 층계참으로 이뤄진 아름다운 상승 계단입니다. "
                        + "'무릎에 사서 어깨에 판다'는 격언이 실현되는, 몇 안 되는 행복한 차트입니다.", bigJumps),
                "천국의 계단에도 끝은 있습니다. 옥상에서 뛰어내리지 않도록 익절 라인을 정해두세요.");
    }

    /** 게걸음 차트: 방향 없이 옆으로만 왔다갔다. */
    private MemeVerdict crabWalk(double[] close, double totalReturn) {
        int activeDays = 0;
        for (int i = 1; i < close.length; i++) {
            double ret = (close[i] - close[i - 1]) / close[i - 1] * 100;
            if (Math.abs(ret) > 1) activeDays++;
        }
        double score = 0;
        if (Math.abs(totalReturn) < 6) {
            score = clamp01((6 - Math.abs(totalReturn)) / 6) * 45
                    + clamp01(activeDays / (close.length * 0.5)) * 35 + 20;
        }
        return new MemeVerdict("crab-walk", "게걸음 차트", "🦀",
                (int) score,
                "옆으로, 옆으로. 게 한 마리가 지나갑니다.",
                String.format("오르락내리락은 하지만 %d일 누적 수익률은 %+.1f%%로 제자리걸음입니다. "
                        + "위로도 아래로도 안 가고 옆으로만 기어가는 전형적인 횡보장입니다.", close.length, totalReturn),
                "게걸음장에서는 매매 횟수만 늘고 수수료만 쌓입니다. 때론 손 놓고 기다리는 게 실력입니다.");
    }

    /** fallback: 아무 패턴도 아님. */
    private MemeVerdict zen() {
        return new MemeVerdict("zen", "무념무상 차트", "🧘",
                33,
                "차트가 아무 생각이 없습니다. 왜냐면 아무 생각이 없기 때문입니다.",
                "특별한 밈 패턴이 검출되지 않았습니다. 오르지도 내리지도, 구르지도 눕지도 않는 "
                        + "해탈의 경지입니다. 이런 차트가 사실 제일 무섭습니다.",
                "차트가 명상 중일 때는 같이 명상하는 것도 훌륭한 전략입니다.");
    }

    // ── 수학 유틸 ─────────────────────────────────────────────────────

    private double[] normalize(double[] v) {
        double lo = min(v);
        double hi = max(v);
        double span = hi - lo;
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = span == 0 ? 0.5 : (v[i] - lo) / span;
        }
        return out;
    }

    /** 0(들쭉날쭉)~1(매끈) — 일별 변화 방향의 일관성. */
    private double smoothness(double[] z) {
        int changes = 0;
        int count = 0;
        for (int i = 2; i < z.length; i++) {
            double d1 = z[i - 1] - z[i - 2];
            double d2 = z[i] - z[i - 1];
            if (d1 != 0 && d2 != 0) {
                count++;
                if (Math.signum(d1) != Math.signum(d2)) changes++;
            }
        }
        return count == 0 ? 0 : 1 - (double) changes / count;
    }

    private double linearR2(double[] y) {
        int n = y.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            sx += i;
            sy += y[i];
            sxx += (double) i * i;
            sxy += i * y[i];
            syy += y[i] * y[i];
        }
        double cov = n * sxy - sx * sy;
        double varX = n * sxx - sx * sx;
        double varY = n * syy - sy * sy;
        if (varX == 0 || varY == 0) return 0;
        double r = cov / Math.sqrt(varX * varY);
        return r * r;
    }

    private double range(double[] v, int from, int to) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) {
            lo = Math.min(lo, v[i]);
            hi = Math.max(hi, v[i]);
        }
        return hi - lo;
    }

    private double mean(double[] v, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += v[i];
        return sum / (to - from);
    }

    private int argmax(double[] v) {
        int idx = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[idx]) idx = i;
        return idx;
    }

    private int argmin(double[] v) {
        int idx = 0;
        for (int i = 1; i < v.length; i++) if (v[i] < v[idx]) idx = i;
        return idx;
    }

    private double max(double[] v) {
        return v[argmax(v)];
    }

    private double min(double[] v) {
        return v[argmin(v)];
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
