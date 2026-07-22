package com.stockmonitor.market.yahoo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Yahoo Finance provider, but only when
 * {@code stockmonitor.market.provider=yahoo}. With any other provider this bean
 * is never created and no outbound calls are made.
 */
@Configuration
@EnableConfigurationProperties(YahooProperties.class)
@ConditionalOnProperty(prefix = "stockmonitor.market", name = "provider", havingValue = "yahoo")
public class YahooConfig {

    @Bean
    public YahooMarketDataProvider yahooMarketDataProvider(YahooProperties props) {
        return new YahooMarketDataProvider(props);
    }
}
