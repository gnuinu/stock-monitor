package com.stockmonitor;

import com.stockmonitor.market.MarketDataService;
import com.stockmonitor.model.Candle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * When the Yahoo provider is selected but the endpoint is unreachable, the app
 * must still start and serve data by falling back to the simulator per symbol.
 * The base URL is pointed at a dead port so history() fails fast (connection
 * refused) — this guards the wiring + fallback contract without real network.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "stockmonitor.market.provider=yahoo",
        "stockmonitor.yahoo.base-url=http://localhost:1"
})
class YahooFallbackTest {

    @Autowired
    MarketDataService market;

    @Test
    void reportsYahooAsSelectedProvider() {
        assertEquals("yahoo", market.dataSource());
    }

    @Test
    void allSymbolsFallBackToSimulatorWhenUnreachable() {
        assertEquals(market.universe().size(), market.fallbackSymbols().size());
    }

    @Test
    void stillServesCandlesForEverySymbol() {
        for (var meta : market.universe()) {
            List<Candle> candles = market.candles(meta.symbol());
            assertFalse(candles.isEmpty(), "no candles for " + meta.symbol());
        }
    }
}
