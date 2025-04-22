package com.bharatpe.lending.lendingplatform.authentication.service;


import com.bharatpe.lending.lendingplatform.authentication.dto.request.TokenRequest;
import com.bharatpe.lending.lendingplatform.authentication.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.authentication.dto.response.TokenResponse;
import com.bharatpe.lending.lendingplatform.authentication.util.RedisUtil;
import com.bharatpe.lending.lendingplatform.config.LendingPlatformConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class LendingPlatformTokenHandler {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LendingPlatformConfiguration lendingPlatformConfiguration;

    @Autowired
    private ObjectMapper objectMapper;

    public String getAuthenticationToken() {
        String key = lendingPlatformConfiguration.getRedisTokenKey();
        final boolean hasKey = Boolean.TRUE.equals(redisTemplate.hasKey(key));
        if (!hasKey) {
            String token = generateToken().getToken();
            log.info("Connector Token fetched from api: {} ", token);
            redisUtil.setValueWithLock(lendingPlatformConfiguration.getRedisTokenLock(), key, token, lendingPlatformConfiguration.getTokenExpiryInMinutes(), TimeUnit.MINUTES);
            return token;
        }
        String value = redisUtil.getValue(key).toString();
        log.info("Connector Token Fetched from Cache : {} ", value);
        long keyExpiryInMinutes = redisTemplate.getExpire(key, TimeUnit.MINUTES);
        if (keyExpiryInMinutes <= lendingPlatformConfiguration.getTokenUpdateThresholdInMinutes()) {
            log.info("Connector Token key:{} expired in {} minutes", key, keyExpiryInMinutes);
            if (redisUtil.acquireLock(lendingPlatformConfiguration.getRedisTokenLock())) {
                new Thread(() -> updateTokenAsync(key)).start();
            }
        }
        return value;
    }

    /**
     * This method is used to update the token in cache asynchronously
     *
     * @param key
     */
    public void updateTokenAsync(String key) {
        String token = generateToken().getToken();
        redisUtil.setValue(key, token, lendingPlatformConfiguration.getTokenExpiryInMinutes(), TimeUnit.MINUTES);
        log.info("Connector Token updated in cache with key: {} token: {} ", key, token);
        redisUtil.releaseLock(lendingPlatformConfiguration.getRedisTokenLock());
    }

    public TokenResponse generateToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<?> request =
                    new HttpEntity<>(
                            TokenRequest.builder()
                                    .name(lendingPlatformConfiguration.getTokenUsername())
                                    .password(lendingPlatformConfiguration.getTokenPassword())
                                    .build(),
                            headers);
            log.info(
                    "TokenGeneration - url : {} and request : {}",
                    lendingPlatformConfiguration.getTokenGenerationPath(),
                    request);
            ResponseEntity<ApiResponse> tokenResponseDTOResponseEntity =
                    restTemplate.postForEntity(
                            lendingPlatformConfiguration.getTokenGenerationPath(), request, ApiResponse.class);

            if (tokenResponseDTOResponseEntity == null) {
                log.error("TokenGeneration - ResponseEntity is null");
                throw new RuntimeException("Token generation failed: response is null");
            }

            log.info("TokenGeneration - status : {} and response: {}",
                    tokenResponseDTOResponseEntity.getStatusCode(),
                    tokenResponseDTOResponseEntity.getBody());

            ApiResponse apiResponse = tokenResponseDTOResponseEntity.getBody();
            if (apiResponse == null) {
                log.error("TokenGeneration - API response body is null");
                throw new RuntimeException("Token generation failed: API response body is null");
            }

            if (apiResponse.getData() == null) {
                log.error("TokenGeneration - API response data is null");
                throw new RuntimeException("Token generation failed: API response data is null");
            }
            TokenResponse tokenResponse = objectMapper.convertValue(apiResponse.getData(), TokenResponse.class);

            return tokenResponse;
        } catch (Exception exception) {
            log.error(
                    "Connector TokenGeneration - error occurred with message: {} : ",
                    exception.getMessage(),
                    exception);
            // TODO: Handle exception here
            throw new RuntimeException("Unable to generate token for Connector");
        }
    }
}

