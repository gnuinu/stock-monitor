package com.stockmonitor.market.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Korea Investment &amp; Securities (한국투자증권) OpenAPI credentials and settings.
 * Secrets come from environment variables (KIS_APP_KEY / KIS_APP_SECRET) — never
 * commit them. See README for issuance steps.
 */
@ConfigurationProperties(prefix = "stockmonitor.kis")
public class KisProperties {

    /** App key issued in the KIS developer portal. */
    private String appKey = "";

    /** App secret issued alongside the app key. */
    private String appSecret = "";

    /**
     * REST base URL. Production: https://openapi.koreainvestment.com:9443
     * Paper/모의투자: https://openapivts.koreainvestment.com:29443
     */
    private String baseUrl = "https://openapi.koreainvestment.com:9443";

    /** Paper-trading (모의투자) account — affects some tr_id prefixes. */
    private boolean paper = false;

    /** Trading days of daily history to pull per symbol at startup. */
    private int historyDays = 260;

    /** Minimum interval (ms) between full daily-history re-pulls per symbol. */
    private long historyRefreshMs = 600_000;

    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isPaper() {
        return paper;
    }

    public void setPaper(boolean paper) {
        this.paper = paper;
    }

    public int getHistoryDays() {
        return historyDays;
    }

    public void setHistoryDays(int historyDays) {
        this.historyDays = historyDays;
    }

    public long getHistoryRefreshMs() {
        return historyRefreshMs;
    }

    public void setHistoryRefreshMs(long historyRefreshMs) {
        this.historyRefreshMs = historyRefreshMs;
    }
}
