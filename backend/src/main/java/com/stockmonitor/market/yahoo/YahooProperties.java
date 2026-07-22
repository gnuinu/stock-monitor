package com.stockmonitor.market.yahoo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Yahoo Finance provider settings. Yahoo's public chart endpoint needs no API
 * key and no account, so the defaults work out of the box; only the base URL,
 * history range and refresh cadence are tunable.
 */
@ConfigurationProperties(prefix = "stockmonitor.yahoo")
public class YahooProperties {

    /** Chart API host. query1/query2 are interchangeable public mirrors. */
    private String baseUrl = "https://query1.finance.yahoo.com";

    /** Yahoo range token for the daily history pull (e.g. 6mo, 1y, 2y). */
    private String range = "1y";

    /** Sent as User-Agent; Yahoo throttles requests without a browser-like UA. */
    private String userAgent = "Mozilla/5.0 (stock-monitor)";

    /** Minimum interval (ms) between full daily-history re-pulls per symbol. */
    private long historyRefreshMs = 600_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public long getHistoryRefreshMs() {
        return historyRefreshMs;
    }

    public void setHistoryRefreshMs(long historyRefreshMs) {
        this.historyRefreshMs = historyRefreshMs;
    }
}
