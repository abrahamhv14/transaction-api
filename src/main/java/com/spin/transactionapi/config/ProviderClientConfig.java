package com.spin.transactionapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ProviderClientConfig {

    @Value("${provider.base-url}")
    private String providerBaseUrl;

    @Value("${provider.connect-timeout-ms:1000}")
    private long connectTimeoutMs;

    @Value("${provider.read-timeout-ms:2000}")
    private long readTimeoutMs;

    @Bean
    public RestClient providerRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(providerBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
