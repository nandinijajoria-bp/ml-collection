package com.bharatpe.lending.service;

import com.bharatpe.lending.dto.DelayedMessage;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LendingRedisMessagePublisher implements LendingDelayedMessagePublisher {

    private static final String EVENT_KEY_PREFIX = "DEL_EVENT_";
    private static final String DATA_KEY_SUFFIX = "_DATA";
    private static final String PROP_APP_NAME = "APP.NAME";
    private static final Long DATA_VALUE_TIMEOUT_BUFFER_IN_SECONDS = 10800L;
    private static final Long MAX_DELAY_IN_SECONDS = 1800L;

    @Autowired
    @Qualifier("LendingDelayedQueueRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Environment env;

    ObjectMapper objectMapper;

    @Override
    public void publish(String appName, String destinationTopic, String partitionKey, Object payload, String hashKey, long timeoutInSeconds) throws Exception {
        log.info("publish() invoked with params destinationTopic {}, id {}, timeout {}", destinationTopic, partitionKey, timeoutInSeconds);

        sanityCheck(appName, destinationTopic, payload, hashKey, timeoutInSeconds);

        String jsonPayload = getObjectMapper().writeValueAsString(payload);
        log.debug("jsonPayload {}", jsonPayload);

        Long expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutInSeconds);

        Date processAt = new Date(expiryTime);
        String expiryTimeString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(processAt);
        String expiryMinute = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(processAt);
        DelayedMessage message = new DelayedMessage(hashKey, partitionKey, destinationTopic, payload, expiryTimeString, expiryMinute);

        String eventKey = EVENT_KEY_PREFIX + appName + "_" + hashKey;
        String dataKey = eventKey + DATA_KEY_SUFFIX;
        redisTemplate.opsForValue().set(dataKey, message, timeoutInSeconds + DATA_VALUE_TIMEOUT_BUFFER_IN_SECONDS, TimeUnit.SECONDS);
        log.debug("Published delayed event data with key {}", dataKey);

        redisTemplate.opsForValue().set(eventKey, null, timeoutInSeconds, TimeUnit.SECONDS);
        log.debug("Published delayed event with key {}, timeout {}", eventKey, timeoutInSeconds);

        redisTemplate.opsForSet().add(expiryMinute, dataKey);
        log.debug("Data key {} added to set for expiry minute {}", dataKey, expiryMinute);

        redisTemplate.expireAt(expiryMinute, DateUtils.addMinutes(processAt, 30));
        log.info("delayed event() pushed to the delayed queue {}", destinationTopic);
    }

    @Override
    public void publish(String destinationTopic, String partitionKey, Object payload, String hashKey, long timeoutInMillis) throws Exception {
        String appName = env.getProperty(PROP_APP_NAME);
        publish(appName, destinationTopic, partitionKey, payload, hashKey, timeoutInMillis);
    }

    private void sanityCheck(String appName, String destinationTopic, Object payload, String hashKey, long timeoutInSeconds) {
        if (StringUtils.isEmpty(appName)) {
            throw new IllegalArgumentException("Invalid app name!");
        }

        if (StringUtils.isEmpty(destinationTopic)) {
            throw new IllegalArgumentException("Invalid destination topic!");
        }

        if (ObjectUtils.isEmpty(payload)) {
            throw new IllegalArgumentException("Invalid payload!");
        }

        if (StringUtils.isEmpty(hashKey)) {
            throw new IllegalArgumentException("Invalid hash key!");
        }

        if (timeoutInSeconds <= 0) {
            throw new IllegalArgumentException("Invalid timeout!");
        }
    }

    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
//            objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
            objectMapper.disableDefaultTyping();
        }

        return objectMapper;
    }

    @Override
    public void delete(String hashKey) throws Exception {
        log.debug("Deleting key :{}", hashKey);
        String eventKey = EVENT_KEY_PREFIX + env.getProperty(PROP_APP_NAME) + "_" + hashKey;
        String dataKey = eventKey + DATA_KEY_SUFFIX;

        Object jsonObj = redisTemplate.opsForValue().get(dataKey);
        if (jsonObj == null) {
            log.info("No delayed msg found for key {}", dataKey);
            return;
        }

        String jsonString = getObjectMapper().writeValueAsString(jsonObj);
        DelayedMessage delayedMessage = getObjectMapper().readValue(jsonString, DelayedMessage.class);

        if (!StringUtils.isEmpty(delayedMessage.getExpiryMinute())) {
            redisTemplate.opsForSet().remove(delayedMessage.getExpiryMinute(), dataKey);
        }

        redisTemplate.delete(dataKey);
        redisTemplate.delete(eventKey);
    }
}
