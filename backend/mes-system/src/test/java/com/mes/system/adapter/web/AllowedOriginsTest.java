package com.mes.system.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedOriginsTest {

    @Test
    void defaultPatternsAllowLanViteOrigin() {
        CorsConfiguration configuration = new CorsConfiguration();
        AllowedOrigins.apply(configuration, AllowedOrigins.DEFAULT);

        assertThat(configuration.checkOrigin("http://192.168.130.108:5173"))
                .isEqualTo("http://192.168.130.108:5173");
        assertThat(configuration.checkOrigin("http://localhost:5173"))
                .isEqualTo("http://localhost:5173");
        assertThat(configuration.checkOrigin("http://10.0.0.8:5173"))
                .isEqualTo("http://10.0.0.8:5173");
    }

    @Test
    void unknownOriginIsRejected() {
        CorsConfiguration configuration = new CorsConfiguration();
        AllowedOrigins.apply(configuration, AllowedOrigins.DEFAULT);

        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    void exactProductionOriginStillMatches() {
        CorsConfiguration configuration = new CorsConfiguration();
        AllowedOrigins.apply(configuration, "https://mes.example.com");

        assertThat(configuration.checkOrigin("https://mes.example.com"))
                .isEqualTo("https://mes.example.com");
        assertThat(configuration.checkOrigin("http://192.168.130.108:5173")).isNull();
    }
}
