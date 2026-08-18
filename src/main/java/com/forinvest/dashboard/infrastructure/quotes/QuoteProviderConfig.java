package com.forinvest.dashboard.infrastructure.quotes;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.forinvest.dashboard.domain.port.StockQuoteProvider;

import tools.jackson.databind.json.JsonMapper;

/** Binds the quote-provider settings and assembles the adapter. Kept beside it so the package is self-contained. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YahooFinanceProperties.class)
class QuoteProviderConfig {

    /**
     * The adapter is built here rather than annotated, so the {@link HttpClient} it needs never
     * becomes a bean of its own. The cookie jar is part of that client and must not be shared with
     * anything else: it is the other half of the crumb the adapter sends, and a jar cleared or
     * refilled by unrelated traffic would invalidate the crumb without the adapter knowing.
     */
    @Bean
    StockQuoteProvider stockQuoteProvider(JsonMapper jsonMapper, YahooFinanceProperties properties) {
        return new YahooFinanceStockQuoteAdapter(httpClient(properties), jsonMapper, properties);
    }

    private static HttpClient httpClient(YahooFinanceProperties properties) {
        // ACCEPT_ALL rather than the default: the cookie is issued by one Yahoo host and presented
        // to another, which the stricter original-server policy would refuse to do.
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofMillis(properties.connectionTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
