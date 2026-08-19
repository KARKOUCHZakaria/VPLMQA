package com.figma.design.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for Spring WebClient
 * Used for making HTTP requests to external APIs (e.g., Figma API)
 * 
 * Configured for:
 * - Large file downloads (increased buffer from 256KB to 50MB)
 * - Network timeouts and connection pooling
 * - Proper resource cleanup
 */
@Configuration
public class WebClientConfig {

    /**
     * Create and configure WebClient bean for HTTP requests
     * Supports large image downloads from Figma CDN (up to 50MB)
     * @return Configured WebClient instance
     */
    @Bean
    public WebClient webClient() {
        // Increase buffer limit to 50MB to handle large Figma exports
        // Default is 256KB which causes DataBufferLimitException for larger images
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();

        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30 seconds
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(java.time.Duration.ofSeconds(120)) // 2 minutes for large downloads
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                           .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
