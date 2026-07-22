package com.stockmonitor.market.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockmonitor.market.MarketDataProvider;
import com.stockmonitor.market.SimulatedMarketDataProvider;
import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real market data from the KIS OpenAPI. Domestic (KRX) symbols use the
 * domestic-stock quotation endpoints; overseas symbols use the overseas-price
 * endpoints. Daily history is paged back until the configured depth is reached,
 * and the latest candle is kept fresh from the current-price endpoint on each
 * refresh (with full history re-pulls throttled to catch newly closed days).
 *
 * <p>The provider is defensive by contract: {@link #refresh} never throws, and
 * {@link com.stockmonitor.market.MarketDataService} falls back to the simulator
 * for any symbol whose {@link #history} call fails.
 */
public class KisMarketDataProvider implements MarketDataProvider {

    public static final String NAME = "kis";
    private static final Logger log = LoggerFactory.getLogger(KisMarketDataProvider.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int PAGE_DAYS = 100;
    private static final int MAX_PAGES = 6;

    private final KisProperties props;
    private final KisClient client;
    private final SimulatedMarketDataProvider simulator;
    private final Map<String, Long> lastHistoryRefresh = new ConcurrentHashMap<>();

    public KisMarketDataProvider(KisProperties props, KisClient client, SimulatedMarketDataProvider simulator) {
        this.props = props;
        this.client = client;
        this.simulator = simulator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Candle> history(StockMeta meta) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("KIS app key/secret not configured");
        }
        List<Candle> candles = meta.isDomestic() ? domesticHistory(meta) : overseasHistory(meta);
        lastHistoryRefresh.put(meta.symbol(), System.currentTimeMillis());
        return candles;
    }

    @Override
    public List<Candle> refresh(StockMeta meta, List<Candle> current) {
        if (current == null || current.isEmpty()) {
            return current;
        }
        try {
            // Periodically re-pull the full daily series so newly closed days appear.
            long since = System.currentTimeMillis() - lastHistoryRefresh.getOrDefault(meta.symbol(), 0L);
            if (since > props.getHistoryRefreshMs()) {
                List<Candle> fresh = history(meta);
                if (!fresh.isEmpty()) {
                    return fresh;
                }
            }
            // Otherwise just update today's (latest) candle with the live quote.
            Quote quote = meta.isDomestic() ? domesticQuote(meta) : overseasQuote(meta);
            if (quote == null || quote.price <= 0) {
                return current;
            }
            Candle last = current.get(current.size() - 1);
            Candle updated = new Candle(
                    last.getDate(),
                    last.getOpen(),
                    Math.max(last.getHigh(), quote.price),
                    Math.min(last.getLow() <= 0 ? quote.price : last.getLow(), quote.price),
                    quote.price,
                    quote.volume > 0 ? quote.volume : last.getVolume());
            List<Candle> copy = new ArrayList<>(current);
            copy.set(copy.size() - 1, updated);
            return copy;
        } catch (Exception e) {
            log.debug("KIS refresh failed for {}: {}", meta.symbol(), e.getMessage());
            return current;
        }
    }

    // ── Domestic (KRX) ────────────────────────────────────────────────

    private List<Candle> domesticHistory(StockMeta meta) {
        TreeMap<LocalDate, Candle> byDate = new TreeMap<>();
        LocalDate to = LocalDate.now();
        for (int page = 0; page < MAX_PAGES && byDate.size() < props.getHistoryDays(); page++) {
            LocalDate from = to.minusDays(PAGE_DAYS * 2L); // calendar span to cover ~100 trading days
            JsonNode output2 = client.domesticDaily(meta.symbol(), from.format(YMD), to.format(YMD)).path("output2");
            if (!output2.isArray() || output2.isEmpty()) {
                break;
            }
            LocalDate earliest = to;
            for (JsonNode row : output2) {
                String ymd = row.path("stck_bsop_date").asText("");
                if (ymd.length() != 8) {
                    continue;
                }
                LocalDate date = LocalDate.parse(ymd, YMD);
                double open = asDouble(row, "stck_oprc");
                double high = asDouble(row, "stck_hgpr");
                double low = asDouble(row, "stck_lwpr");
                double close = asDouble(row, "stck_clpr");
                long volume = asLong(row, "acml_vol");
                if (close <= 0) {
                    continue;
                }
                byDate.putIfAbsent(date, new Candle(date, open, high, low, close, volume));
                if (date.isBefore(earliest)) {
                    earliest = date;
                }
            }
            to = earliest.minusDays(1);
        }
        return trimmed(byDate);
    }

    private Quote domesticQuote(StockMeta meta) {
        JsonNode output = client.domesticPrice(meta.symbol()).path("output");
        double price = asDouble(output, "stck_prpr");
        long volume = asLong(output, "acml_vol");
        return new Quote(price, volume);
    }

    // ── Overseas ──────────────────────────────────────────────────────

    private List<Candle> overseasHistory(StockMeta meta) {
        TreeMap<LocalDate, Candle> byDate = new TreeMap<>();
        String excd = meta.overseasExchange();
        LocalDate to = LocalDate.now();
        for (int page = 0; page < MAX_PAGES && byDate.size() < props.getHistoryDays(); page++) {
            JsonNode output2 = client.overseasDaily(excd, meta.symbol(), to.format(YMD)).path("output2");
            if (!output2.isArray() || output2.isEmpty()) {
                break;
            }
            LocalDate earliest = to;
            for (JsonNode row : output2) {
                String ymd = row.path("xymd").asText("");
                if (ymd.length() != 8) {
                    continue;
                }
                LocalDate date = LocalDate.parse(ymd, YMD);
                double open = asDouble(row, "open");
                double high = asDouble(row, "high");
                double low = asDouble(row, "low");
                double close = asDouble(row, "clos");
                long volume = asLong(row, "tvol");
                if (close <= 0) {
                    continue;
                }
                byDate.putIfAbsent(date, new Candle(date, open, high, low, close, volume));
                if (date.isBefore(earliest)) {
                    earliest = date;
                }
            }
            if (!earliest.isBefore(to)) {
                break; // no older data returned; avoid an infinite loop
            }
            to = earliest.minusDays(1);
        }
        return trimmed(byDate);
    }

    private Quote overseasQuote(StockMeta meta) {
        JsonNode output = client.overseasPrice(meta.overseasExchange(), meta.symbol()).path("output");
        double price = asDouble(output, "last");
        long volume = asLong(output, "tvol");
        return new Quote(price, volume);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private List<Candle> trimmed(TreeMap<LocalDate, Candle> byDate) {
        List<Candle> all = new ArrayList<>(byDate.values()); // ascending by date
        int keep = props.getHistoryDays();
        if (all.size() > keep) {
            return new ArrayList<>(all.subList(all.size() - keep, all.size()));
        }
        return all;
    }

    private static double asDouble(JsonNode node, String field) {
        String raw = node.path(field).asText("").trim();
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long asLong(JsonNode node, String field) {
        String raw = node.path(field).asText("").trim();
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return (long) Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Quote(double price, long volume) {
    }
}
