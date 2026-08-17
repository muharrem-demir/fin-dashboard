package com.forinvest.dashboard.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Describes the API for the generated OpenAPI document.
 *
 * <p>Only the document-level metadata lives here. Paths, schemas and status codes are derived from
 * the controllers and DTOs themselves, so the specification cannot drift away from the code.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI dashboardOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("Financial Portfolio Dashboard API")
                        .description("""
                                CRUD API for stock portfolios.

                                Adding a ticker that a portfolio already holds increases the existing position \
                                rather than replacing it, and ticker matching is case-insensitive.

                                Errors are returned as RFC 9457 problem documents.""")
                        .version("v1")
                        .contact(new Contact().name(applicationName))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
