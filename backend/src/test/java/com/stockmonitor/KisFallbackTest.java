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
 * When the KIS provider is selected but no credentials are configured, the app
 * must still start and serve data by falling back to the simulator per symbol.
 * (Live KIS calls can't be exercised in CI, so this guards the wiring + fallback
 * contract, which is the part that would otherwise break silently.)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "stockmonitor.market.provider=kis",
        "stockmonitor.kis.app-key=",
        "stockmonitor.kis.app-secret="
})
class KisFallbackTest {

    @Autowired
    MarketDataService market;

    @Test
    void reportsKisAsSelectedProvider() {
        assertEquals("kis", market.dataSource());
    }

    @Test
    void allSymbolsFallBackToSimulatorWithoutCredentials() {
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
