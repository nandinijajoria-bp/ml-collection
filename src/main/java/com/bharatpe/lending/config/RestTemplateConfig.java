package com.bharatpe.lending.config;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RestTemplateConfig {

    @Value("${resttemplate.high.timeout:40000}")
    private int timeout;

    @Value("${resttemplate.high.connect-timeout:20000}")
    private int connectTimeout;

    @Value("${resttemplate.high.max-total:500}")
    private int maxTotal;

    @Value("${resttemplate.high.max-per-route:500}")
    private int maxPerRoute;

    @Value("${resttemplate.high.validate-after-inactivity:5000}")
    private int validateAfterInactivity;

    @Bean(name = "restTemplateHigh")
    public RestTemplate restTemplateHigh() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(timeout);

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        connectionManager.setValidateAfterInactivity(validateAfterInactivity);

        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .evictIdleConnections(60, TimeUnit.SECONDS)
                .build();

        factory.setHttpClient(httpClient);

        return new RestTemplate(factory);
    }
}