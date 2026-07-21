package com.stockmonitor;

import com.stockmonitor.indicator.IndicatorService;
import com.stockmonitor.market.MarketDataService;
import com.stockmonitor.meme.MemeAnalysisService;
import com.stockmonitor.model.Candle;
import com.stockmonitor.signal.SignalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SmokeTest {

    @Autowired
    MarketDataService market;
    @Autowired
    IndicatorService indicators;
    @Autowired
    SignalService signals;
    @Autowired
    MemeAnalysisService meme;

    @Test
    void universeHasStocks() {
        assertEquals(15, market.universe().size());
    }

    @Test
    void candlesAreGenerated() {
        List<Candle> candles = market.candles("005930");
        assertEquals(420, candles.size());
        for (Candle c : candles) {
            assertTrue(c.getHigh() >= c.getLow());
            assertTrue(c.getClose() > 0);
            assertTrue(c.getVolume() > 0);
        }
    }

    @Test
    void indicatorsCompute() {
        Map<String, Object> result = indicators.allIndicators(market.candles("AAPL"));
        assertNotNull(result.get("sma20"));
        assertNotNull(result.get("rsi"));
        assertNotNull(result.get("macd"));
        assertNotNull(result.get("bollinger"));
        assertFalse(((List<?>) result.get("sma20")).isEmpty());
    }

    @Test
    void signalsProduceScore() {
        Map<String, Object> result = signals.analyze(market.candles("NVDA"));
        assertNotNull(result.get("score"));
        assertNotNull(result.get("signals"));
        int score = (int) result.get("score");
        assertTrue(score >= -100 && score <= 100);
    }

    @Test
    void memeAnalysisAlwaysReturnsVerdict() {
        for (var stockMeta : market.universe()) {
            Map<String, Object> result = meme.analyze(stockMeta.symbol(), market.candles(stockMeta.symbol()));
            assertNotNull(result.get("best"));
            assertEquals(3, ((List<?>) result.get("candidates")).size());
        }
    }
}
