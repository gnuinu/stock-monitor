package com.stockmonitor.market;

import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over the active {@link MarketDataProvider}. Owns the symbol universe,
 * the candle cache and the refresh schedule, and guarantees the app always has
 * data: if the real provider fails to load a symbol, that symbol falls back to
 * the built-in simulator. The REST layer depends only on this class, so the
 * data source can be swapped purely by configuration.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

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

    private final MarketDataProvider provider;
    private final SimulatedMarketDataProvider simulator;
    private final Map<String, StockMeta> metaBySymbol = new LinkedHashMap<>();
    private final Map<String, List<Candle>> candlesBySymbol = new ConcurrentHashMap<>();
    private final Set<String> fallbackSymbols = ConcurrentHashMap.newKeySet();
    private volatile Instant lastTickAt = Instant.now();

    public MarketDataService(@Value("${stockmonitor.market.provider:simulated}") String providerName,
                             List<MarketDataProvider> providers,
                             SimulatedMarketDataProvider simulator) {
        this.simulator = simulator;
        this.provider = providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown market provider '{}'; falling back to simulator", providerName);
                    return simulator;
                });
    }

    @PostConstruct
    void init() {
        log.info("Market data provider: {}", provider.name());
        for (StockMeta meta : UNIVERSE) {
            metaBySymbol.put(meta.symbol(), meta);
            candlesBySymbol.put(meta.symbol(), loadWithFallback(meta));
        }
        if (!fallbackSymbols.isEmpty()) {
            log.warn("{} symbol(s) using simulated fallback: {}", fallbackSymbols.size(), fallbackSymbols);
        }
    }

    private List<Candle> loadWithFallback(StockMeta meta) {
        if (provider != simulator) {
            try {
                List<Candle> history = provider.history(meta);
                if (history != null && !history.isEmpty()) {
                    fallbackSymbols.remove(meta.symbol());
                    return history;
                }
                log.warn("Provider '{}' returned no data for {}; using simulator", provider.name(), meta.symbol());
            } catch (Exception e) {
                log.warn("Provider '{}' failed for {} ({}); using simulator",
                        provider.name(), meta.symbol(), e.getMessage());
            }
            fallbackSymbols.add(meta.symbol());
        }
        return simulator.history(meta);
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

    public String dataSource() {
        return provider.name();
    }

    /** Symbols currently served by the simulator instead of the real provider. */
    public Set<String> fallbackSymbols() {
        return Set.copyOf(fallbackSymbols);
    }

    /** Refresh the latest candle for every symbol from the active provider. */
    @Scheduled(fixedRateString = "${stockmonitor.market.refresh-ms:3000}", initialDelay = 3000)
    void refresh() {
        for (StockMeta meta : UNIVERSE) {
            List<Candle> current = candlesBySymbol.get(meta.symbol());
            MarketDataProvider active = fallbackSymbols.contains(meta.symbol()) ? simulator : provider;
            try {
                List<Candle> updated = active.refresh(meta, current);
                if (updated != null && !updated.isEmpty()) {
                    candlesBySymbol.put(meta.symbol(), updated);
                }
            } catch (Exception e) {
                // refresh must never break the schedule; keep last good data
                log.debug("Refresh failed for {}: {}", meta.symbol(), e.getMessage());
            }
        }
        lastTickAt = Instant.now();
    }

    public static class UnknownSymbolException extends RuntimeException {
        public UnknownSymbolException(String symbol) {
            super("Unknown symbol: " + symbol);
        }
    }
}
