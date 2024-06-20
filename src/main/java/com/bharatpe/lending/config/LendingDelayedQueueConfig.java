package com.bharatpe.lending.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class LendingDelayedQueueConfig {

    @Value("${lending.delayed.queue.redis.host:localhost}")
    private String redisHostName;

    @Value("${lending.delayed.queue.redis.port:6379}")
    private int redisPort;

    @Value("${lending.delayed.queue.redis.pool.max.size:50}")
    private int redisPoolMaxSize;

    @Value("${lending.delayed.queue.redis.pool.max.idle:20}")
    private int redisPoolMaxIdle;

    @Bean("LendingDelayedQueueLettucePoolConfig")
    LettucePoolingClientConfiguration lettucePoolConfig() {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(redisPoolMaxSize);
        poolConfig.setMaxIdle(redisPoolMaxIdle);

        return LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .clientOptions(clientOptions())
                .clientResources(clientResources())
                .build();
    }

    @Bean(destroyMethod = "shutdown", name = "LendingDelayedQueueClientResources")
    ClientResources clientResources() {
        return DefaultClientResources.create();
    }

    @Bean("LendingDelayedQueueRedisStandaloneConfiguration")
    public RedisStandaloneConfiguration redisStandaloneConfiguration() {
        return new RedisStandaloneConfiguration(redisHostName, redisPort);
    }

    @Bean("LendingDelayedQueueClientOptions")
    public ClientOptions clientOptions() {
        return ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();
    }

    @Bean("LendingDelayedQueueRedisConnectionFactory")
    public RedisConnectionFactory connectionFactory() {
        return new LettuceConnectionFactory(redisStandaloneConfiguration(), lettucePoolConfig());
    }

    @Bean("LendingDelayedQueueRedisTemplate")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
//        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        objectMapper.disableDefaultTyping();

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        return redisTemplate;
    }
}
