package com.portfolio.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {
    @Bean("geminiRestClient")
    public RestClient geminiRestClient(GeminiProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout().connectSeconds() * 1000);
        factory.setReadTimeout(properties.timeout().readSeconds() * 1000);
        return RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(factory).build();
    }
}
