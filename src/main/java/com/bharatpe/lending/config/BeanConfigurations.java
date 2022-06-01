package com.bharatpe.lending.config;

import com.bharatpe.common.utils.HmacCalculator;
import com.bharatpe.lending.service.merchant.constants.Constants;
import com.bharatpe.lending.service.merchant.service.Impl.MerchantServiceImpl;
import com.bharatpe.lending.service.merchant.service.MerchantService;
import com.bharatpe.lending.util.MapperUtil;
import com.bharatpe.lending.util.RestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class BeanConfigurations {


    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    HmacCalculator hmacCalculator;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    Constants constants;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "Lending");
    }

    @Bean(name = "customRestTemplate")
    public RestTemplate customRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    MapperUtil mapperUtil() {
        return new MapperUtil(objectMapper);
    }

    @Bean
    RestUtils restUtils() {
        return new RestUtils(restTemplate, mapperUtil());
    }

    @Bean
    MerchantService merchantService() {
        return new MerchantServiceImpl(mapperUtil(), restUtils(), hmacCalculator, constants);
    }
}
