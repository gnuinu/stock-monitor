package com.stockmonitor.model;

public record StockMeta(
        String symbol,
        String name,
        String market,
        String sector,
        double basePrice,
        long baseVolume
) {
}
