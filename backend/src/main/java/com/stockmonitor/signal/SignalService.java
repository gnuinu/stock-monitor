package com.stockmonitor.signal;

import com.stockmonitor.indicator.IndicatorService;
import com.stockmonitor.model.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based trading signals derived from the indicator set.
 * Each signal carries an action (BUY / SELL / HOLD), a strength (1-3) and a
 * human-readable Korean description. Signals are aggregated into a single
 * -100..100 score for the gauge on the frontend.
 */
@Service
public class SignalService {

    public record Signal(String name, String category, String action, int strength, String description) {
    }

    private final IndicatorService indicators;

    public SignalService(IndicatorService indicators) {
        this.indicators = indicators;
    }

    public Map<String, Object> analyze(List<Candle> candles) {
        double[] close = candles.stream().mapToDouble(Candle::getClose).toArray();
        double[] high = candles.stream().mapToDouble(Candle::getHigh).toArray();
        double[] low = candles.stream().mapToDouble(Candle::getLow).toArray();
        long[] volume = candles.stream().mapToLong(Candle::getVolume).toArray();
        int last = close.length - 1;

        List<Signal> signals = new ArrayList<>();

        double[] sma5 = indicators.sma(close, 5);
        double[] sma20 = indicators.sma(close, 20);
        double[] sma60 = indicators.sma(close, 60);

        // 단기 골든/데드 크로스 (5일선 vs 20일선, 최근 5거래일 이내)
        int cross520 = recentCross(sma5, sma20, 5);
        if (cross520 > 0) {
            signals.add(new Signal("골든 크로스", "이동평균", "BUY", 3,
                    "5일 이동평균선이 20일선을 최근 상향 돌파했습니다. 단기 상승 전환 신호로 해석됩니다."));
        } else if (cross520 < 0) {
            signals.add(new Signal("데드 크로스", "이동평균", "SELL", 3,
                    "5일 이동평균선이 20일선을 최근 하향 이탈했습니다. 단기 하락 전환 신호로 해석됩니다."));
        }

        // 중기 추세: 20일선 vs 60일선 배열
        if (valid(sma20[last]) && valid(sma60[last])) {
            if (sma20[last] > sma60[last] && close[last] > sma20[last]) {
                signals.add(new Signal("정배열 추세", "이동평균", "BUY", 2,
                        "주가 > 20일선 > 60일선의 정배열 구간입니다. 중기 상승 추세가 유지되고 있습니다."));
            } else if (sma20[last] < sma60[last] && close[last] < sma20[last]) {
                signals.add(new Signal("역배열 추세", "이동평균", "SELL", 2,
                        "주가 < 20일선 < 60일선의 역배열 구간입니다. 중기 하락 추세가 이어지고 있습니다."));
            }
        }

        // RSI
        double[] rsi = indicators.rsi(close, 14);
        if (valid(rsi[last])) {
            double r = rsi[last];
            if (r >= 70) {
                signals.add(new Signal("RSI 과매수", "모멘텀", "SELL", r >= 80 ? 3 : 2,
                        String.format("RSI(14)가 %.1f로 과매수권입니다. 단기 조정 가능성에 유의하세요.", r)));
            } else if (r <= 30) {
                signals.add(new Signal("RSI 과매도", "모멘텀", "BUY", r <= 20 ? 3 : 2,
                        String.format("RSI(14)가 %.1f로 과매도권입니다. 기술적 반등 가능성이 있습니다.", r)));
            } else {
                signals.add(new Signal("RSI 중립", "모멘텀", "HOLD", 1,
                        String.format("RSI(14) %.1f — 과열도 침체도 아닌 중립 구간입니다.", r)));
            }
        }

        // MACD 시그널 교차
        double[][] macd = indicators.macd(close, 12, 26, 9);
        int macdCross = recentCross(macd[0], macd[1], 5);
        if (macdCross > 0) {
            signals.add(new Signal("MACD 골든 크로스", "모멘텀", "BUY", 2,
                    "MACD선이 시그널선을 최근 상향 돌파했습니다. 상승 모멘텀이 살아나고 있습니다."));
        } else if (macdCross < 0) {
            signals.add(new Signal("MACD 데드 크로스", "모멘텀", "SELL", 2,
                    "MACD선이 시그널선을 최근 하향 이탈했습니다. 모멘텀이 약해지고 있습니다."));
        }

        // 볼린저 밴드
        double[][] bb = indicators.bollinger(close, 20, 2.0);
        if (valid(bb[0][last]) && valid(bb[2][last])) {
            if (close[last] >= bb[0][last]) {
                signals.add(new Signal("볼린저 상단 돌파", "변동성", "SELL", 2,
                        "종가가 볼린저 밴드 상단(+2σ) 위에 있습니다. 통계적으로 과열 구간입니다."));
            } else if (close[last] <= bb[2][last]) {
                signals.add(new Signal("볼린저 하단 이탈", "변동성", "BUY", 2,
                        "종가가 볼린저 밴드 하단(-2σ) 아래에 있습니다. 통계적으로 과매도 구간입니다."));
            }
            double bandWidth = (bb[0][last] - bb[2][last]) / bb[1][last];
            if (bandWidth < 0.06) {
                signals.add(new Signal("밴드 스퀴즈", "변동성", "HOLD", 1,
                        "볼린저 밴드 폭이 크게 축소됐습니다. 조만간 큰 방향성 움직임이 나올 수 있습니다."));
            }
        }

        // 스토캐스틱
        double[][] stoch = indicators.stochastic(close, high, low, 14, 3);
        if (valid(stoch[0][last]) && valid(stoch[1][last])) {
            double k = stoch[0][last];
            if (k >= 80) {
                signals.add(new Signal("스토캐스틱 과매수", "모멘텀", "SELL", 1,
                        String.format("Stochastic %%K가 %.1f로 과매수권입니다.", k)));
            } else if (k <= 20) {
                signals.add(new Signal("스토캐스틱 과매도", "모멘텀", "BUY", 1,
                        String.format("Stochastic %%K가 %.1f로 과매도권입니다.", k)));
            }
        }

        // 거래량 급증
        if (close.length > 21) {
            double avgVol = 0;
            for (int i = last - 20; i < last; i++) {
                avgVol += volume[i];
            }
            avgVol /= 20;
            double ratio = volume[last] / Math.max(1, avgVol);
            if (ratio >= 2.0) {
                boolean up = close[last] >= close[last - 1];
                signals.add(new Signal("거래량 급증", "수급", up ? "BUY" : "SELL", 2,
                        String.format("거래량이 20일 평균 대비 %.1f배로 급증했습니다. %s 흐름에 힘이 실리고 있습니다.",
                                ratio, up ? "상승" : "하락")));
            }
        }

        // 이격도 (주가 vs 20일선)
        if (valid(sma20[last])) {
            double disparity = close[last] / sma20[last] * 100;
            if (disparity >= 110) {
                signals.add(new Signal("이격도 과열", "이동평균", "SELL", 1,
                        String.format("20일선 이격도 %.1f%% — 단기 과열로 평균 회귀 압력이 있습니다.", disparity)));
            } else if (disparity <= 90) {
                signals.add(new Signal("이격도 침체", "이동평균", "BUY", 1,
                        String.format("20일선 이격도 %.1f%% — 단기 낙폭 과대로 반등 여지가 있습니다.", disparity)));
            }
        }

        int score = score(signals);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("signals", signals);
        out.put("score", score);
        out.put("scoreLabel", label(score));
        out.put("disclaimer", "본 시그널은 기술적 지표 기반 참고 자료일 뿐 투자 권유가 아닙니다. 투자의 책임은 본인에게 있습니다.");
        return out;
    }

    private int score(List<Signal> signals) {
        double sum = 0;
        double weight = 0;
        for (Signal s : signals) {
            int dir = switch (s.action()) {
                case "BUY" -> 1;
                case "SELL" -> -1;
                default -> 0;
            };
            sum += dir * s.strength();
            weight += s.strength();
        }
        if (weight == 0) {
            return 0;
        }
        return (int) Math.round(sum / weight * 100);
    }

    private String label(int score) {
        if (score >= 60) return "적극 매수 우위";
        if (score >= 25) return "매수 우위";
        if (score > -25) return "중립";
        if (score > -60) return "매도 우위";
        return "적극 매도 우위";
    }

    /** +1 if a crossed above b within lookback days, -1 if crossed below, 0 otherwise. */
    private int recentCross(double[] a, double[] b, int lookback) {
        int last = a.length - 1;
        for (int i = last; i > Math.max(0, last - lookback); i--) {
            if (!valid(a[i]) || !valid(b[i]) || !valid(a[i - 1]) || !valid(b[i - 1])) {
                continue;
            }
            if (a[i - 1] <= b[i - 1] && a[i] > b[i]) {
                return 1;
            }
            if (a[i - 1] >= b[i - 1] && a[i] < b[i]) {
                return -1;
            }
        }
        return 0;
    }

    private boolean valid(double v) {
        return !Double.isNaN(v);
    }
}
