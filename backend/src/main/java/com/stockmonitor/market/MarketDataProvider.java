package com.stockmonitor.market;

import com.stockmonitor.model.Candle;
import com.stockmonitor.model.StockMeta;

import java.util.List;

/**
 * A source of daily OHLCV candle data for a single symbol. Implementations are
 * selected by the {@code stockmonitor.market.provider} property. The
 * {@link com.stockmonitor.market.MarketDataService} facade owns the universe,
 * caching and scheduling; a provider only needs to turn a {@link StockMeta}
 * into candles and keep the latest one fresh.
 *
 * <p>Implementations must be resilient: a provider should never throw out of
 * {@link #refresh}. If a remote call fails it returns {@code current}
 * unchanged so the app keeps serving the last known-good data.
 */
public interface MarketDataProvider {

    /** Stable id matched against {@code stockmonitor.market.provider}. */
    String name();

    /**
     * Full daily history for a symbol, oldest first. Called once at startup.
     * May throw; the facade will fall back to the simulator for that symbol.
     */
    List<Candle> history(StockMeta meta);

    /**
     * Return an updated candle list reflecting the latest price/volume, or
     * {@code current} unchanged if nothing changed or the refresh failed.
     * Called periodically. Must not throw.
     */
    List<Candle> refresh(StockMeta meta, List<Candle> current);
}
