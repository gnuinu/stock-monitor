package com.stockmonitor.market.kis;

import com.stockmonitor.market.SimulatedMarketDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the KIS provider stack, but only when
 * {@code stockmonitor.market.provider=kis}. With any other provider these beans
 * are never created, so the app runs (on the simulator) without KIS credentials.
 */
@Configuration
@EnableConfigurationProperties(KisProperties.class)
@ConditionalOnProperty(prefix = "stockmonitor.market", name = "provider", havingValue = "kis")
public class KisConfig {

    @Bean
    public KisTokenManager kisTokenManager(KisProperties props) {
        return new KisTokenManager(props);
    }

    @Bean
    public KisClient kisClient(KisProperties props, KisTokenManager tokenManager) {
        return new KisClient(props, tokenManager);
    }

    @Bean
    public KisMarketDataProvider kisMarketDataProvider(KisProperties props, KisClient client,
                                                       SimulatedMarketDataProvider simulator) {
        return new KisMarketDataProvider(props, client, simulator);
    }
}
