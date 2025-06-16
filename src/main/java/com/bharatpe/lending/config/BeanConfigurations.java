package com.bharatpe.lending.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class BeanConfigurations {
    @Value("${nbfc.connection.timeout.threshold:15}")
    Integer nbfcConnectionTimeoutThreshold;
    @Value("${nbfc.read.timeout.threshold:15}")
    Integer nbfcReadTimeoutThreshold;


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

    @Bean(name = "nbfcRestTemplate")
    public RestTemplate nbfcRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(nbfcConnectionTimeoutThreshold))
                .setReadTimeout(Duration.ofSeconds(nbfcReadTimeoutThreshold))
                .build();
    }
}
