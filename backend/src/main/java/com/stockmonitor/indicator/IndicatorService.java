package com.stockmonitor.indicator;

import com.stockmonitor.model.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classic technical indicators computed over a daily candle series.
 * All series are aligned to candle dates; leading values that cannot be
 * computed yet are simply omitted (charting libs handle ragged starts fine).
 */
@Service
public class IndicatorService {

    public record Point(String time, double value) {
    }

    public Map<String, Object> allIndicators(List<Candle> candles) {
        double[] close = candles.stream().mapToDouble(Candle::getClose).toArray();
        double[] high = candles.stream().mapToDouble(Candle::getHigh).toArray();
        double[] low = candles.stream().mapToDouble(Candle::getLow).toArray();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sma5", toPoints(candles, sma(close, 5)));
        out.put("sma20", toPoints(candles, sma(close, 20)));
        out.put("sma60", toPoints(candles, sma(close, 60)));
        out.put("sma120", toPoints(candles, sma(close, 120)));

        double[][] bb = bollinger(close, 20, 2.0);
        out.put("bollinger", Map.of(
                "upper", toPoints(candles, bb[0]),
                "middle", toPoints(candles, bb[1]),
                "lower", toPoints(candles, bb[2])
        ));

        out.put("rsi", toPoints(candles, rsi(close, 14)));

        double[][] macd = macd(close, 12, 26, 9);
        out.put("macd", Map.of(
                "macd", toPoints(candles, macd[0]),
                "signal", toPoints(candles, macd[1]),
                "histogram", toPoints(candles, macd[2])
        ));

        double[][] stoch = stochastic(close, high, low, 14, 3);
        out.put("stochastic", Map.of(
                "k", toPoints(candles, stoch[0]),
                "d", toPoints(candles, stoch[1])
        ));

        List<Map<String, Object>> volume = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            volume.add(Map.of(
                    "time", c.getTime(),
                    "value", c.getVolume(),
                    "up", c.getClose() >= c.getOpen()
            ));
        }
        out.put("volume", volume);
        return out;
    }

    public double[] sma(double[] values, int period) {
        double[] out = nanArray(values.length);
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) {
                sum -= values[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    public double[] ema(double[] values, int period) {
        double[] out = nanArray(values.length);
        if (values.length < period) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += values[i];
        }
        out[period - 1] = seed / period;
        for (int i = period; i < values.length; i++) {
            out[i] = values[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    public double[] rsi(double[] close, int period) {
        double[] out = nanArray(close.length);
        if (close.length <= period) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = close[i] - close[i - 1];
            if (diff >= 0) {
                gain += diff;
            } else {
                loss -= diff;
            }
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        out[period] = toRsi(avgGain, avgLoss);
        for (int i = period + 1; i < close.length; i++) {
            double diff = close[i] - close[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
            out[i] = toRsi(avgGain, avgLoss);
        }
        return out;
    }

    private double toRsi(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return 100;
        }
        return 100 - 100 / (1 + avgGain / avgLoss);
    }

    /** Returns [macdLine, signalLine, histogram]. */
    public double[][] macd(double[] close, int fast, int slow, int signalPeriod) {
        double[] emaFast = ema(close, fast);
        double[] emaSlow = ema(close, slow);
        double[] macdLine = nanArray(close.length);
        for (int i = 0; i < close.length; i++) {
            if (!Double.isNaN(emaFast[i]) && !Double.isNaN(emaSlow[i])) {
                macdLine[i] = emaFast[i] - emaSlow[i];
            }
        }
        double[] signal = emaOverNan(macdLine, signalPeriod);
        double[] histogram = nanArray(close.length);
        for (int i = 0; i < close.length; i++) {
            if (!Double.isNaN(macdLine[i]) && !Double.isNaN(signal[i])) {
                histogram[i] = macdLine[i] - signal[i];
            }
        }
        return new double[][]{macdLine, signal, histogram};
    }

    /** Returns [upper, middle, lower]. */
    public double[][] bollinger(double[] close, int period, double mult) {
        double[] middle = sma(close, period);
        double[] upper = nanArray(close.length);
        double[] lower = nanArray(close.length);
        for (int i = period - 1; i < close.length; i++) {
            double mean = middle[i];
            double sq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sq += (close[j] - mean) * (close[j] - mean);
            }
            double sd = Math.sqrt(sq / period);
            upper[i] = mean + mult * sd;
            lower[i] = mean - mult * sd;
        }
        return new double[][]{upper, middle, lower};
    }

    /** Returns [%K (smoothed), %D]. */
    public double[][] stochastic(double[] close, double[] high, double[] low, int period, int smooth) {
        double[] rawK = nanArray(close.length);
        for (int i = period - 1; i < close.length; i++) {
            double hh = Double.NEGATIVE_INFINITY;
            double ll = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                hh = Math.max(hh, high[j]);
                ll = Math.min(ll, low[j]);
            }
            rawK[i] = hh == ll ? 50 : (close[i] - ll) / (hh - ll) * 100;
        }
        double[] k = smaOverNan(rawK, smooth);
        double[] d = smaOverNan(k, smooth);
        return new double[][]{k, d};
    }

    private double[] emaOverNan(double[] values, int period) {
        int start = firstValid(values);
        double[] out = nanArray(values.length);
        if (start < 0 || values.length - start < period) {
            return out;
        }
        double[] valid = new double[values.length - start];
        System.arraycopy(values, start, valid, 0, valid.length);
        double[] emaValid = ema(valid, period);
        for (int i = 0; i < emaValid.length; i++) {
            out[start + i] = emaValid[i];
        }
        return out;
    }

    private double[] smaOverNan(double[] values, int period) {
        int start = firstValid(values);
        double[] out = nanArray(values.length);
        if (start < 0 || values.length - start < period) {
            return out;
        }
        double[] valid = new double[values.length - start];
        System.arraycopy(values, start, valid, 0, valid.length);
        double[] smaValid = sma(valid, period);
        for (int i = 0; i < smaValid.length; i++) {
            out[start + i] = smaValid[i];
        }
        return out;
    }

    private int firstValid(double[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                return i;
            }
        }
        return -1;
    }

    private double[] nanArray(int n) {
        double[] out = new double[n];
        java.util.Arrays.fill(out, Double.NaN);
        return out;
    }

    public List<Point> toPoints(List<Candle> candles, double[] values) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (!Double.isNaN(values[i])) {
                points.add(new Point(candles.get(i).getTime(), round4(values[i])));
            }
        }
        return points;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
