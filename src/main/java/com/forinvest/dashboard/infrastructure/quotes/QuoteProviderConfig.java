package com.forinvest.dashboard.infrastructure.quotes;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the quote-provider settings. Kept beside the adapter so the package is self-contained. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YahooFinanceProperties.class)
class QuoteProviderConfig {}
