package com.stockmonitor.controller;

import com.stockmonitor.indicator.IndicatorService;
import com.stockmonitor.market.MarketDataService;
import com.stockmonitor.meme.MemeAnalysisService;
import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import com.stockmonitor.signal.SignalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StockController {

    private final MarketDataService market;
    private final IndicatorService indicators;
    private final SignalService signals;
    private final MemeAnalysisService meme;

    public StockController(MarketDataService market, IndicatorService indicators,
                           SignalService signals, MemeAnalysisService meme) {
        this.market = market;
        this.indicators = indicators;
        this.signals = signals;
        this.meme = meme;
    }

    @GetMapping("/stocks")
    public List<Map<String, Object>> stocks() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockMeta meta : market.universe()) {
            out.add(quoteOf(meta));
        }
        return out;
    }

    @GetMapping("/stocks/{symbol}")
    public Map<String, Object> stock(@PathVariable String symbol) {
        return quoteOf(market.meta(symbol));
    }

    @GetMapping("/stocks/{symbol}/candles")
    public List<Map<String, Object>> candles(@PathVariable String symbol,
                                             @RequestParam(defaultValue = "0") int days) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Candle c : market.candles(symbol, days)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", c.getTime());
            m.put("open", c.getOpen());
            m.put("high", c.getHigh());
            m.put("low", c.getLow());
            m.put("close", c.getClose());
            m.put("volume", c.getVolume());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/stocks/{symbol}/indicators")
    public Map<String, Object> indicators(@PathVariable String symbol,
                                          @RequestParam(defaultValue = "0") int days) {
        // Indicators are computed on the full history so warm-up periods do not
        // truncate the visible window, then trimmed to the requested range.
        List<Candle> all = market.candles(symbol);
        Map<String, Object> result = indicators.allIndicators(all);
        if (days > 0 && days < all.size()) {
            String cutoff = all.get(all.size() - days).getTime();
            result = trim(result, cutoff);
        }
        return result;
    }

    @GetMapping("/stocks/{symbol}/signals")
    public Map<String, Object> signals(@PathVariable String symbol) {
        return signals.analyze(market.candles(symbol));
    }

    @GetMapping("/stocks/{symbol}/meme")
    public Map<String, Object> meme(@PathVariable String symbol) {
        return meme.analyze(symbol, market.candles(symbol));
    }

    @GetMapping("/market/summary")
    public Map<String, Object> marketSummary() {
        List<Map<String, Object>> quotes = stocks();
        int advancing = 0;
        int declining = 0;
        for (Map<String, Object> q : quotes) {
            double chg = (double) q.get("changePercent");
            if (chg > 0) advancing++;
            else if (chg < 0) declining++;
        }
        Comparator<Map<String, Object>> byChange =
                Comparator.comparingDouble(q -> (double) q.get("changePercent"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("advancing", advancing);
        out.put("declining", declining);
        out.put("unchanged", quotes.size() - advancing - declining);
        out.put("topGainers", quotes.stream().sorted(byChange.reversed()).limit(3).toList());
        out.put("topLosers", quotes.stream().sorted(byChange).limit(3).toList());
        out.put("updatedAt", market.lastTickAt().toString());
        return out;
    }

    private Map<String, Object> quoteOf(StockMeta meta) {
        List<Candle> candles = market.candles(meta.symbol());
        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);
        double change = last.getClose() - prev.getClose();

        // 30-day closes for the watchlist sparkline
        List<Double> spark = candles.subList(Math.max(0, candles.size() - 30), candles.size())
                .stream().map(Candle::getClose).toList();

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("symbol", meta.symbol());
        q.put("name", meta.name());
        q.put("market", meta.market());
        q.put("sector", meta.sector());
        q.put("price", last.getClose());
        q.put("change", round2(change));
        q.put("changePercent", round2(change / prev.getClose() * 100));
        q.put("open", last.getOpen());
        q.put("dayHigh", last.getHigh());
        q.put("dayLow", last.getLow());
        q.put("prevClose", prev.getClose());
        q.put("volume", last.getVolume());
        q.put("sparkline", spark);
        q.put("updatedAt", market.lastTickAt().toString());
        return q;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trim(Map<String, Object> indicatorMap, String cutoffDate) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : indicatorMap.entrySet()) {
            if (e.getValue() instanceof List<?> list) {
                out.put(e.getKey(), trimList(list, cutoffDate));
            } else if (e.getValue() instanceof Map<?, ?> nested) {
                Map<String, Object> nestedOut = new LinkedHashMap<>();
                for (Map.Entry<?, ?> ne : nested.entrySet()) {
                    nestedOut.put((String) ne.getKey(), trimList((List<?>) ne.getValue(), cutoffDate));
                }
                out.put(e.getKey(), nestedOut);
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private List<?> trimList(List<?> points, String cutoffDate) {
        return points.stream().filter(p -> timeOf(p).compareTo(cutoffDate) >= 0).toList();
    }

    private String timeOf(Object point) {
        if (point instanceof IndicatorService.Point p) {
            return p.time();
        }
        if (point instanceof Map<?, ?> m) {
            return (String) m.get("time");
        }
        return "";
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @ExceptionHandler(MarketDataService.UnknownSymbolException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> unknownSymbol(MarketDataService.UnknownSymbolException e) {
        return Map.of("error", e.getMessage());
    }
}
