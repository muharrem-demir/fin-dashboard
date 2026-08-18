package com.forinvest.dashboard.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Wires the live feed: the endpoint clients connect to, and the clock that drives it.
 *
 * <p>Raw WebSocket rather than STOMP. Every client keeps its own connection and its own watchlist,
 * so there is nothing for a broker or a destination hierarchy to do; a message broker would add a
 * routing model on top of a problem that does not have routing in it.
 *
 * <p>Scheduling is enabled here because the broadcast tick is the only scheduled work in the
 * application, and it exists solely to serve this endpoint.
 *
 * <p>Origins are open by default, matching the REST API: the feed carries public market data, no
 * credentials and no per-user state. Narrow {@code dashboard.quotes.stream.allowed-origins} when
 * the deployment calls for it.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableScheduling
@EnableConfigurationProperties(QuoteStreamProperties.class)
class QuoteStreamConfig implements WebSocketConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteStreamConfig.class);

    private final QuoteStreamHandler quoteStreamHandler;
    private final QuoteStreamProperties properties;

    QuoteStreamConfig(QuoteStreamHandler quoteStreamHandler, QuoteStreamProperties properties) {
        this.quoteStreamHandler = quoteStreamHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(quoteStreamHandler, properties.path())
                .setAllowedOriginPatterns(properties.originPatterns());
        LOG.info("Streaming quotes on {} every {} ms", properties.path(), properties.intervalMillis());
    }
}
