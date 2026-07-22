package com.stockmonitor.market;

import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in market data simulator. Generates a deterministic (per-symbol seeded)
 * daily OHLCV history and nudges the latest candle on each refresh to emulate a
 * live feed. Always available — also serves as the fallback for real providers.
 */
@Component
public class SimulatedMarketDataProvider implements MarketDataProvider {

    public static final String NAME = "simulated";
    private static final int HISTORY_DAYS = 420;

    private final Map<String, Random> liveRandom = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Candle> history(StockMeta meta) {
        Random rnd = new Random(meta.symbol().hashCode());
        List<Candle> candles = new ArrayList<>(HISTORY_DAYS);

        LocalDate date = LocalDate.now();
        List<LocalDate> tradingDays = new ArrayList<>(HISTORY_DAYS);
        while (tradingDays.size() < HISTORY_DAYS) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                tradingDays.add(date);
            }
            date = date.minusDays(1);
        }
        java.util.Collections.reverse(tradingDays);

        double price = meta.basePrice() * (0.6 + rnd.nextDouble() * 0.5);
        int regimeLeft = 0;
        double drift = 0;
        double vol = 0.02;

        for (LocalDate day : tradingDays) {
            if (regimeLeft <= 0) {
                regimeLeft = 20 + rnd.nextInt(45);
                int regime = rnd.nextInt(4);
                switch (regime) {
                    case 0 -> { drift = 0.0015 + rnd.nextDouble() * 0.003; vol = 0.012 + rnd.nextDouble() * 0.01; }   // bull
                    case 1 -> { drift = -0.0015 - rnd.nextDouble() * 0.003; vol = 0.014 + rnd.nextDouble() * 0.012; } // bear
                    case 2 -> { drift = (rnd.nextDouble() - 0.5) * 0.001; vol = 0.006 + rnd.nextDouble() * 0.006; }   // sideways
                    default -> { drift = (rnd.nextDouble() - 0.5) * 0.004; vol = 0.025 + rnd.nextDouble() * 0.02; }   // volatile
                }
            }
            regimeLeft--;

            double ret = drift + vol * rnd.nextGaussian();
            double open = round(meta, price * Math.exp(vol * 0.3 * rnd.nextGaussian()));
            double close = round(meta, price * Math.exp(ret));
            double span = Math.abs(rnd.nextGaussian()) * vol * 0.7;
            double high = round(meta, Math.max(open, close) * (1 + span));
            double low = round(meta, Math.min(open, close) * (1 - span));
            long volume = (long) (meta.baseVolume() * Math.exp(rnd.nextGaussian() * 0.45) * (1 + 6 * Math.abs(ret)));

            candles.add(new Candle(day, open, high, low, close, volume));
            price = close;
        }
        return candles;
    }

    @Override
    public List<Candle> refresh(StockMeta meta, List<Candle> current) {
        if (current == null || current.isEmpty()) {
            return current;
        }
        Random rnd = liveRandom.computeIfAbsent(meta.symbol(), s -> new Random(s.hashCode() * 31L + 7));
        Candle last = current.get(current.size() - 1);
        double drift = rnd.nextGaussian() * 0.0012;
        double newClose = round(meta, last.getClose() * Math.exp(drift));
        long addedVolume = (long) (meta.baseVolume() * 0.001 * (0.5 + rnd.nextDouble()));
        last.tick(newClose, addedVolume);
        return current;
    }

    private double round(StockMeta meta, double value) {
        if (meta.basePrice() >= 1000) {
            return Math.max(1, Math.round(value / 10.0) * 10);
        }
        return Math.max(0.01, Math.round(value * 100.0) / 100.0);
    }
}
