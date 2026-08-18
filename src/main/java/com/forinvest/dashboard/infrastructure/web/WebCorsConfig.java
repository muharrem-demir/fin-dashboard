package com.forinvest.dashboard.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Browser access to the REST API, configured in one place.
 *
 * <p>Deliberately not spread over the controllers: {@code @CrossOrigin} on a handler makes the
 * policy a property of whoever remembered to annotate, and an endpoint added later inherits
 * nothing. One registration covers every path under the configured pattern, so there is a single
 * answer to "who may call this API" and it is the same answer for every route.
 *
 * <p>Open by default, matching the WebSocket feed: the API carries public market data and has no
 * authentication by design, so an origin restriction here would protect nothing that is not
 * already public. Narrow {@code dashboard.web.cors.allowed-origins} when a deployment calls for
 * it.
 *
 * <p>The live feed is not covered by this. A WebSocket handshake is not a CORS request, and its
 * origins are checked by the handshake itself — see {@code dashboard.quotes.stream.allowed-origins}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
class WebCorsConfig implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WebCorsConfig.class);

    private final CorsProperties properties;

    WebCorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(properties.pathPattern())
                .allowedOriginPatterns(properties.originPatterns())
                .allowedMethods(properties.methods())
                .allowedHeaders(properties.requestHeaders())
                .exposedHeaders(properties.responseHeaders())
                .allowCredentials(properties.allowCredentials())
                .maxAge(properties.maxAgeSeconds());
        LOG.info("CORS enabled on {} for origins {}", properties.pathPattern(), properties.allowedOrigins());
    }
}
