package com.stockmonitor;

import com.stockmonitor.portfolio.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PortfolioTest {

    @Autowired
    PortfolioService portfolio;

    @BeforeEach
    void reset() {
        portfolio.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buyThenSellUpdatesCashAndHoldings() {
        Map<String, Object> before = portfolio.snapshot();
        double startCash = (double) before.get("cash");

        portfolio.order("005930", PortfolioService.Side.BUY, 10);
        Map<String, Object> afterBuy = portfolio.snapshot();
        assertTrue((double) afterBuy.get("cash") < startCash, "cash should drop after buy");
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) afterBuy.get("holdings");
        assertEquals(1, holdings.size());
        assertEquals(10, holdings.get(0).get("quantity"));

        portfolio.order("005930", PortfolioService.Side.SELL, 10);
        Map<String, Object> afterSell = portfolio.snapshot();
        assertTrue(((List<?>) afterSell.get("holdings")).isEmpty(), "no holdings after full sell");
    }

    @Test
    void cannotSellMoreThanHeld() {
        assertThrows(IllegalStateException.class,
                () -> portfolio.order("AAPL", PortfolioService.Side.SELL, 1));
    }

    @Test
    void cannotBuyBeyondCash() {
        // 1억 원 예수금으로는 감당 못 할 수량
        assertThrows(IllegalStateException.class,
                () -> portfolio.order("373220", PortfolioService.Side.BUY, 1_000_000));
    }

    @Test
    void resetRestoresInitialCash() {
        portfolio.order("AAPL", PortfolioService.Side.BUY, 5);
        Map<String, Object> after = portfolio.reset();
        assertEquals(after.get("initialCash"), after.get("cash"));
        assertTrue(((List<?>) after.get("holdings")).isEmpty());
    }
}
