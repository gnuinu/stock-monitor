package com.stockmonitor.market.kis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Issues and caches the KIS OAuth2 access token (client-credentials grant).
 * Tokens are valid ~24h and KIS rate-limits issuance, so the token is cached
 * and only refreshed shortly before expiry.
 */
public class KisTokenManager {

    private static final Logger log = LoggerFactory.getLogger(KisTokenManager.class);

    private final KisProperties props;
    private final RestClient http;

    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    public KisTokenManager(KisProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /** Returns a valid bearer token, refreshing if needed. Throws on failure. */
    public synchronized String bearer() {
        if (token != null && Instant.now().isBefore(expiresAt)) {
            return token;
        }
        Map<?, ?> res = http.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", props.getAppKey(),
                        "appsecret", props.getAppSecret()))
                .retrieve()
                .body(Map.class);

        if (res == null || res.get("access_token") == null) {
            throw new IllegalStateException("KIS token response missing access_token: " + res);
        }
        token = (String) res.get("access_token");
        long expiresIn = res.get("expires_in") instanceof Number n ? n.longValue() : 86_400L;
        // refresh 10 minutes early to avoid mid-request expiry
        expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 600));
        log.info("Issued new KIS access token (expires ~{})", expiresAt);
        return token;
    }
}
