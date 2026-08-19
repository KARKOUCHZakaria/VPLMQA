package com.vplmqa.design.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

@Configuration
public class ProjectServiceWebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
        return WebClient.builder().exchangeStrategies(strategies);
    }

    @Bean
    public WebClient projectServiceClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
            .baseUrl("http://project-service:8088")
            .build();
    }
}
