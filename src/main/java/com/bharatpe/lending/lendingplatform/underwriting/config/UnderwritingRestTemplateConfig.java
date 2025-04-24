package com.bharatpe.lending.lendingplatform.underwriting.config;

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
public class UnderwritingRestTemplateConfig {

    @Value("${lending.platform.underwriting.rest.template.connection-timeout:2000}")
    private int connectionTimeout;
    @Value("${lending.platform.underwriting.rest.template.socket-timeout:25000}")
    private int socketTimeout;
    @Value("${lending.platform.underwriting.rest.template.connection-request-timeout:2000}")
    private int connectionRequestTimeout;
    @Value("${lending.platform.underwriting.rest.template.max.total:500}")
    private int maxTotal;
    @Value("${lending.platform.underwriting.rest.template.default.max.per.route:250}")
    private int defaultMaxPerRoute;
    @Value("${lending.platform.underwriting.rest.template.validate.after.inactivity:500}")
    private int validateAfterInactivity;
    @Value("${lending.platform.underwriting.rest.template.evict.idle.connections:60000}")
    private int evictIdleConnections;

    @Bean(name = "underwritingRestTemplate")
    public RestTemplate generalRestTemplate() {
        return new RestTemplateBuilder()
                .requestFactory(this::generalRequestFactory)
                .build();
    }
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
