package com.forinvest.dashboard.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL for the integration tests.
 *
 * <p>The same image as production, so Flyway migrations, the unique constraint and the JPA mapping
 * are exercised against the database that will actually run them — not an in-memory substitute that
 * accepts SQL Postgres would reject.
 *
 * <p>{@code @ServiceConnection} points the datasource at the container, so no URL or credentials
 * need to be configured anywhere. Spring's context cache keeps one container for the whole test
 * run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
