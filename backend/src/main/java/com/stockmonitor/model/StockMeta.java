package com.stockmonitor.model;

public record StockMeta(
        String symbol,
        String name,
        String market,
        String sector,
        double basePrice,
        long baseVolume
) {

    /** KRX-listed (KOSPI/KOSDAQ) — queried via the KIS domestic-stock APIs. */
    public boolean isDomestic() {
        return "KOSPI".equals(market) || "KOSDAQ".equals(market);
    }

    /**
     * KIS overseas exchange code (EXCD) for non-domestic symbols.
     * Only NASDAQ is in the default universe; extend as needed.
     */
    public String overseasExchange() {
        return switch (market) {
            case "NYSE" -> "NYS";
            case "AMEX" -> "AMS";
            default -> "NAS";
        };
    }
}
