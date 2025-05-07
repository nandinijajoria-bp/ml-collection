package com.bharatpe.lending.lendingplatform.config;


import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class LendingPlatformRestTemplateConfig {

    // Inject configurable timeout values
    @Value("${resttemplate.connection-timeout:2000}")
    private int connectionTimeout;
    @Value("${resttemplate.socket-timeout:25000}")
    private int socketTimeout;
    @Value("${resttemplate.connection-request-timeout:2000}")
    private int connectionRequestTimeout;
    @Value("${resttemplate.max.total:500}")
    private int maxTotal;
    @Value("${resttemplate.default.max.per.route:250}")
    private int defaultMaxPerRoute;
    @Value("${resttemplate.validate.after.inactivity:500}")
    private int validateAfterInactivity;
    @Value("${resttemplate.evict.idle.connections:60000}")
    private int evictIdleConnections;



    // Bean for general API calls
    @Bean(name = "LendingPlatformRestTemplate")
    public RestTemplate generalRestTemplate() {
        return new RestTemplateBuilder()
                .requestFactory(this::generalRequestFactory)
                .build();
    }


    // Request factory for general use (default timeouts)
    private HttpComponentsClientHttpRequestFactory generalRequestFactory() {
        RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setSocketTimeout(socketTimeout)
                .setConnectTimeout(connectionTimeout)
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(defaultMaxPerRoute);
        connectionManager.setValidateAfterInactivity(validateAfterInactivity);
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .evictIdleConnections(evictIdleConnections, TimeUnit.MILLISECONDS)
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
