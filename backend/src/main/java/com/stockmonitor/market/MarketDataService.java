package com.stockmonitor.market;

import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in market data simulator. Generates a deterministic (per-symbol seeded)
 * daily OHLCV history and mutates the latest candle every few seconds to
 * emulate a live feed. Swap this service for a real data provider
 * (KIS, Yahoo Finance, Alpha Vantage, ...) without touching the API layer.
 */
@Service
public class MarketDataService {

    private static final int HISTORY_DAYS = 420;

    private static final List<StockMeta> UNIVERSE = List.of(
            new StockMeta("005930", "삼성전자", "KOSPI", "반도체", 71000, 12_000_000),
            new StockMeta("000660", "SK하이닉스", "KOSPI", "반도체", 178000, 3_500_000),
            new StockMeta("035420", "NAVER", "KOSPI", "인터넷", 192000, 700_000),
            new StockMeta("035720", "카카오", "KOSPI", "인터넷", 47000, 2_400_000),
            new StockMeta("373220", "LG에너지솔루션", "KOSPI", "2차전지", 390000, 300_000),
            new StockMeta("005380", "현대차", "KOSPI", "자동차", 245000, 900_000),
            new StockMeta("068270", "셀트리온", "KOSPI", "바이오", 185000, 500_000),
            new StockMeta("034020", "두산에너빌리티", "KOSPI", "에너지", 21000, 5_000_000),
            new StockMeta("086520", "에코프로", "KOSDAQ", "2차전지", 95000, 1_500_000),
            new StockMeta("012450", "한화에어로스페이스", "KOSPI", "방산", 280000, 400_000),
            new StockMeta("AAPL", "Apple", "NASDAQ", "Tech", 228, 55_000_000),
            new StockMeta("NVDA", "NVIDIA", "NASDAQ", "Semiconductor", 138, 240_000_000),
            new StockMeta("TSLA", "Tesla", "NASDAQ", "Auto", 250, 90_000_000),
            new StockMeta("MSFT", "Microsoft", "NASDAQ", "Tech", 430, 20_000_000),
            new StockMeta("PLTR", "Palantir", "NASDAQ", "Software", 80, 60_000_000)
    );

    private final Map<String, StockMeta> metaBySymbol = new LinkedHashMap<>();
    private final Map<String, List<Candle>> candlesBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Random> liveRandom = new ConcurrentHashMap<>();
    private volatile Instant lastTickAt = Instant.now();

    @PostConstruct
    void init() {
        for (StockMeta meta : UNIVERSE) {
            metaBySymbol.put(meta.symbol(), meta);
            candlesBySymbol.put(meta.symbol(), generateHistory(meta));
            liveRandom.put(meta.symbol(), new Random(meta.symbol().hashCode() * 31L + 7));
        }
    }

    public List<StockMeta> universe() {
        return List.copyOf(metaBySymbol.values());
    }

    public StockMeta meta(String symbol) {
        StockMeta meta = metaBySymbol.get(symbol);
        if (meta == null) {
            throw new UnknownSymbolException(symbol);
        }
        return meta;
    }

    /** Snapshot of the candle history, oldest first. */
    public List<Candle> candles(String symbol) {
        meta(symbol);
        return candlesBySymbol.get(symbol).stream().map(Candle::snapshot).toList();
    }

    public List<Candle> candles(String symbol, int days) {
        List<Candle> all = candles(symbol);
        if (days <= 0 || days >= all.size()) {
            return all;
        }
        return all.subList(all.size() - days, all.size());
    }

    public Instant lastTickAt() {
        return lastTickAt;
    }

    /** Emulated live feed: nudge the latest candle every 3 seconds. */
    @Scheduled(fixedRate = 3000, initialDelay = 3000)
    void liveTick() {
        for (StockMeta meta : UNIVERSE) {
            List<Candle> candles = candlesBySymbol.get(meta.symbol());
            Candle last = candles.get(candles.size() - 1);
            Random rnd = liveRandom.get(meta.symbol());
            double drift = (rnd.nextGaussian()) * 0.0012;
            double newClose = round(meta, last.getClose() * Math.exp(drift));
            long addedVolume = (long) (meta.baseVolume() * 0.001 * (0.5 + rnd.nextDouble()));
            last.tick(newClose, addedVolume);
        }
        lastTickAt = Instant.now();
    }

    private List<Candle> generateHistory(StockMeta meta) {
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

    private double round(StockMeta meta, double value) {
        if (meta.basePrice() >= 1000) {
            return Math.max(1, Math.round(value / 10.0) * 10);
        }
        return Math.max(0.01, Math.round(value * 100.0) / 100.0);
    }

    public static class UnknownSymbolException extends RuntimeException {
        public UnknownSymbolException(String symbol) {
            super("Unknown symbol: " + symbol);
        }
    }
}
