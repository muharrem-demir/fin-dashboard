package com.forinvest.dashboard.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Defaults and parsing of the CORS settings. */
class CorsPropertiesTest {

    @Test
    @DisplayName("falls back to an open policy over the API when nothing is configured")
    void appliesDefaults() {
        CorsProperties properties = new CorsProperties(null, null, null, null, null, null, null);

        assertThat(properties.pathPattern()).isEqualTo("/api/v1/**");
        assertThat(properties.originPatterns()).containsExactly("*");
        assertThat(properties.methods()).containsExactly("GET", "POST", "PATCH", "DELETE", "OPTIONS");
        assertThat(properties.requestHeaders()).containsExactly("*");
        assertThat(properties.responseHeaders()).containsExactly("Location");
        assertThat(properties.allowCredentials()).isFalse();
        assertThat(properties.maxAgeSeconds()).isEqualTo(3_600);
    }

    @Test
    @DisplayName("blank values fall back rather than configuring an empty list")
    void treatsBlankAsUnset() {
        CorsProperties properties = new CorsProperties("  ", "", "  ", "", "  ", null, null);

        assertThat(properties.pathPattern()).isEqualTo("/api/v1/**");
        assertThat(properties.originPatterns()).containsExactly("*");
        assertThat(properties.methods()).containsExactly("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("splits comma-separated lists and trims the entries")
    void splitsLists() {
        CorsProperties properties = new CorsProperties(
                "/**",
                "https://app.example.com, https://*.example.org ",
                "GET, POST",
                "Content-Type , Accept",
                "Location, Link",
                true,
                60L);

        assertThat(properties.originPatterns()).containsExactly("https://app.example.com", "https://*.example.org");
        assertThat(properties.methods()).containsExactly("GET", "POST");
        assertThat(properties.requestHeaders()).containsExactly("Content-Type", "Accept");
        assertThat(properties.responseHeaders()).containsExactly("Location", "Link");
        assertThat(properties.allowCredentials()).isTrue();
        assertThat(properties.maxAgeSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("a negative max age falls back rather than being sent to the browser")
    void rejectsNegativeMaxAge() {
        assertThat(new CorsProperties(null, null, null, null, null, null, -1L).maxAgeSeconds())
                .isEqualTo(3_600);
    }
}
