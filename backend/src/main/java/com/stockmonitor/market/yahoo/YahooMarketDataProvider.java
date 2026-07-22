package com.stockmonitor.market.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockmonitor.market.MarketDataProvider;
import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real market data from Yahoo Finance's public chart endpoint
 * ({@code /v8/finance/chart/{symbol}}). No API key or account required. Korean
 * listings use the {@code .KS} (KOSPI) / {@code .KQ} (KOSDAQ) suffixes; US
 * symbols are passed through as-is.
 *
 * <p>Defensive by contract: {@link #refresh} never throws, and
 * {@link com.stockmonitor.market.MarketDataService} falls back to the simulator
 * for any symbol whose {@link #history} call fails.
 */
public class YahooMarketDataProvider implements MarketDataProvider {

    public static final String NAME = "yahoo";
    private static final Logger log = LoggerFactory.getLogger(YahooMarketDataProvider.class);

    private final YahooProperties props;
    private final RestClient http;
    private final Map<String, Long> lastHistoryRefresh = new ConcurrentHashMap<>();

    public YahooMarketDataProvider(YahooProperties props) {
        this.props = props;
        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("User-Agent", props.getUserAgent())
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Candle> history(StockMeta meta) {
        JsonNode result = fetchChart(yahooSymbol(meta), props.getRange());
        List<Candle> candles = parseCandles(result);
        if (candles.isEmpty()) {
            throw new IllegalStateException("Yahoo returned no candles for " + yahooSymbol(meta));
        }
        lastHistoryRefresh.put(meta.symbol(), System.currentTimeMillis());
        return candles;
    }

    @Override
    public List<Candle> refresh(StockMeta meta, List<Candle> current) {
        if (current == null || current.isEmpty()) {
            return current;
        }
        try {
            long since = System.currentTimeMillis() - lastHistoryRefresh.getOrDefault(meta.symbol(), 0L);
            if (since > props.getHistoryRefreshMs()) {
                List<Candle> fresh = history(meta);
                if (!fresh.isEmpty()) {
                    return fresh;
                }
            }
            // Lightweight live update: 1-day chart carries the latest regular-market price.
            JsonNode result = fetchChart(yahooSymbol(meta), "1d");
            JsonNode metaNode = result.path("meta");
            double price = metaNode.path("regularMarketPrice").asDouble(0);
            if (price <= 0) {
                return current;
            }
            long volume = metaNode.path("regularMarketVolume").asLong(0);
            Candle last = current.get(current.size() - 1);
            Candle updated = new Candle(
                    last.getDate(),
                    last.getOpen(),
                    Math.max(last.getHigh(), price),
                    Math.min(last.getLow() <= 0 ? price : last.getLow(), price),
                    price,
                    volume > 0 ? volume : last.getVolume());
            List<Candle> copy = new ArrayList<>(current);
            copy.set(copy.size() - 1, updated);
            return copy;
        } catch (Exception e) {
            log.debug("Yahoo refresh failed for {}: {}", meta.symbol(), e.getMessage());
            return current;
        }
    }

    private JsonNode fetchChart(String symbol, String range) {
        JsonNode body = http.get()
                .uri(b -> b.path("/v8/finance/chart/{symbol}")
                        .queryParam("range", range)
                        .queryParam("interval", "1d")
                        .build(symbol))
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new IllegalStateException("Empty Yahoo response for " + symbol);
        }
        JsonNode error = body.path("chart").path("error");
        if (!error.isNull() && !error.isMissingNode()) {
            throw new IllegalStateException("Yahoo error for " + symbol + ": " + error);
        }
        JsonNode result = body.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new IllegalStateException("Yahoo returned no result for " + symbol);
        }
        return result.get(0);
    }

    private List<Candle> parseCandles(JsonNode result) {
        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }
        JsonNode q = quote.get(0);
        JsonNode opens = q.path("open");
        JsonNode highs = q.path("high");
        JsonNode lows = q.path("low");
        JsonNode closes = q.path("close");
        JsonNode volumes = q.path("volume");
        long gmtOffset = result.path("meta").path("gmtoffset").asLong(0);

        List<Candle> candles = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            if (closes.path(i).isNull() || closes.path(i).isMissingNode()) {
                continue; // Yahoo emits nulls for non-trading gaps
            }
            double close = closes.path(i).asDouble(0);
            if (close <= 0) {
                continue;
            }
            long ts = timestamps.path(i).asLong();
            LocalDate date = Instant.ofEpochSecond(ts + gmtOffset).atZone(ZoneOffset.UTC).toLocalDate();
            double open = firstPositive(opens.path(i).asDouble(0), close);
            double high = firstPositive(highs.path(i).asDouble(0), close);
            double low = firstPositive(lows.path(i).asDouble(0), close);
            long volume = volumes.path(i).asLong(0);
            candles.add(new Candle(date, round(open), round(high), round(low), round(close), volume));
        }
        return candles;
    }

    private String yahooSymbol(StockMeta meta) {
        return switch (meta.market()) {
            case "KOSPI" -> meta.symbol() + ".KS";
            case "KOSDAQ" -> meta.symbol() + ".KQ";
            default -> meta.symbol();
        };
    }

    private double firstPositive(double value, double fallback) {
        return value > 0 ? value : fallback;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
