package com.stockmonitor.portfolio;

import com.stockmonitor.market.MarketDataService;
import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory paper-trading (모의투자) account — a single demo portfolio that buys
 * and sells at the latest simulated/live price. Everything is denominated in KRW;
 * USD-listed names are converted at a fixed reference rate for a single, simple
 * cash balance and P&amp;L figure. State is process-local and resets on restart.
 */
@Service
public class PortfolioService {

    /** Approximate USD→KRW used to keep the account single-currency. */
    static final double USD_KRW = 1350.0;
    static final double INITIAL_CASH = 100_000_000.0; // 1억 원

    private final MarketDataService market;

    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private volatile double cash = INITIAL_CASH;
    private volatile double realizedPnl = 0;

    public PortfolioService(MarketDataService market) {
        this.market = market;
    }

    private static final class Position {
        int quantity;
        double avgPriceKrw; // average cost per share, in KRW
    }

    public enum Side { BUY, SELL }

    public record OrderResult(String symbol, String side, int quantity, double priceKrw,
                              double amountKrw, Map<String, Object> portfolio) {
    }

    /** Latest price for a symbol converted to KRW. */
    public double priceKrw(StockMeta meta) {
        List<Candle> candles = market.candles(meta.symbol());
        double close = candles.get(candles.size() - 1).getClose();
        return meta.isDomestic() ? close : close * USD_KRW;
    }

    public synchronized OrderResult order(String symbol, Side side, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        StockMeta meta = market.meta(symbol);
        double price = priceKrw(meta);
        double amount = price * quantity;

        if (side == Side.BUY) {
            if (amount > cash) {
                throw new IllegalStateException("현금이 부족합니다. 필요: "
                        + Math.round(amount) + "원, 보유: " + Math.round(cash) + "원");
            }
            Position pos = positions.computeIfAbsent(symbol, s -> new Position());
            double newCost = pos.avgPriceKrw * pos.quantity + amount;
            pos.quantity += quantity;
            pos.avgPriceKrw = newCost / pos.quantity;
            cash -= amount;
        } else {
            Position pos = positions.get(symbol);
            if (pos == null || pos.quantity < quantity) {
                throw new IllegalStateException("보유 수량이 부족합니다. 보유: "
                        + (pos == null ? 0 : pos.quantity) + "주");
            }
            realizedPnl += (price - pos.avgPriceKrw) * quantity;
            pos.quantity -= quantity;
            cash += amount;
            if (pos.quantity == 0) {
                positions.remove(symbol);
            }
        }
        return new OrderResult(symbol, side.name(), quantity, round2(price), round2(amount), snapshot());
    }

    public synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> holdings = new ArrayList<>();
        double holdingsValue = 0;
        double unrealized = 0;

        for (Map.Entry<String, Position> e : positions.entrySet()) {
            StockMeta meta = market.meta(e.getKey());
            Position pos = e.getValue();
            double price = priceKrw(meta);
            double value = price * pos.quantity;
            double cost = pos.avgPriceKrw * pos.quantity;
            double pnl = value - cost;
            holdingsValue += value;
            unrealized += pnl;

            Map<String, Object> h = new LinkedHashMap<>();
            h.put("symbol", meta.symbol());
            h.put("name", meta.name());
            h.put("market", meta.market());
            h.put("quantity", pos.quantity);
            h.put("avgPrice", round2(pos.avgPriceKrw));
            h.put("currentPrice", round2(price));
            h.put("value", round2(value));
            h.put("pnl", round2(pnl));
            h.put("pnlPercent", cost > 0 ? round2(pnl / cost * 100) : 0.0);
            holdings.add(h);
        }
        holdings.sort((a, b) -> Double.compare((double) b.get("value"), (double) a.get("value")));

        double totalValue = cash + holdingsValue;
        double totalPnl = totalValue - INITIAL_CASH;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cash", round2(cash));
        out.put("holdingsValue", round2(holdingsValue));
        out.put("totalValue", round2(totalValue));
        out.put("unrealizedPnl", round2(unrealized));
        out.put("realizedPnl", round2(realizedPnl));
        out.put("totalPnl", round2(totalPnl));
        out.put("totalPnlPercent", round2(totalPnl / INITIAL_CASH * 100));
        out.put("initialCash", INITIAL_CASH);
        out.put("holdings", holdings);
        out.put("usdKrw", USD_KRW);
        return out;
    }

    public synchronized Map<String, Object> reset() {
        positions.clear();
        cash = INITIAL_CASH;
        realizedPnl = 0;
        return snapshot();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
