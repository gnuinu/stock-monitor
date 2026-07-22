package com.stockmonitor.portfolio;

import com.stockmonitor.market.MarketDataService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolio;

    public PortfolioController(PortfolioService portfolio) {
        this.portfolio = portfolio;
    }

    public record OrderRequest(@NotBlank String symbol, @NotBlank String side, @Min(1) int quantity) {
    }

    @GetMapping
    public Map<String, Object> get() {
        return portfolio.snapshot();
    }

    @PostMapping("/orders")
    public PortfolioService.OrderResult order(@RequestBody OrderRequest req) {
        PortfolioService.Side side;
        try {
            side = PortfolioService.Side.valueOf(req.side().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("side는 BUY 또는 SELL 이어야 합니다.");
        }
        return portfolio.order(req.symbol(), side, req.quantity());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return portfolio.reset();
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(RuntimeException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MarketDataService.UnknownSymbolException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> unknownSymbol(MarketDataService.UnknownSymbolException e) {
        return Map.of("error", e.getMessage());
    }
}
