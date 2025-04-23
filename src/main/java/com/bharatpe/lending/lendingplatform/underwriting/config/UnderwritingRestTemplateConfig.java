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
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
