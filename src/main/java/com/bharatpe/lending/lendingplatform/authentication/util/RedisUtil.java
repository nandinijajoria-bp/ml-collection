package com.bharatpe.lending.lendingplatform.authentication.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public Object getValue(String key) {
        return executeWithExceptionHandling(
                () -> {
                    final ValueOperations<String, Object> operations = redisTemplate.opsForValue();
                    final boolean hasKey = Boolean.TRUE.equals(redisTemplate.hasKey(key));
                    return hasKey ? operations.get(key) : Optional.empty();
                });
    }

    public void setValueWithLock(
            String lockKey, String key, String token, long tokenExpiryInMinutes, TimeUnit timeUnit) {
        executeWithExceptionHandling(
                () -> {
                    final ValueOperations<String, Object> operations = redisTemplate.opsForValue();
                    if (acquireLock(lockKey) && !Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                        log.info("lock acquired on key: {} and setting token in cache: {}", lockKey, token);
                        operations.set(key, token, tokenExpiryInMinutes, timeUnit);
                        log.info("lock released on key: {}", lockKey);
                        releaseLock(lockKey);
                    }
                    return Optional.empty();
                });
    }

    public void setValue(String key, String token, long tokenExpiryInMinutes, TimeUnit timeUnit) {
        executeWithExceptionHandling(
                () -> {
                    final ValueOperations<String, Object> operations = redisTemplate.opsForValue();
                    operations.set(key, token, tokenExpiryInMinutes, timeUnit);
                    return Optional.empty();
                });
    }

    public boolean acquireLock(String key) {
        return this.acquireLock(key, 10);
    }

    public boolean acquireLock(String key, Integer ttl) {
        return Boolean.TRUE.equals(
                executeWithExceptionHandling(
                        () -> {
                            if (Objects.nonNull(key)) {
                                ValueOperations<String, Object> operations = this.redisTemplate.opsForValue();
                                Boolean lockTaken = operations.setIfAbsent(key, "LOCK_TAKEN", Duration.ofSeconds(ttl));
                                return lockTaken != null && lockTaken;
                            }
                            return false;
                        }));
    }

    public void releaseLock(String key) {
        executeWithExceptionHandling(
                () -> {
                    if (Objects.nonNull(key)) {
                        this.redisTemplate.delete(key);
                    }
                    return Optional.empty();
                });
    }

    private <T> T executeWithExceptionHandling(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RedisConnectionFailureException redisConnectionFailureException) {
            log.error(
                    "exception in redis connection is {}, complete exception in redis connection is {} ",
                    redisConnectionFailureException.getMessage(),
                    Arrays.asList(redisConnectionFailureException.getStackTrace()));
        } catch (Exception exception) {
            log.error(
                    "exception is {}, complete exception in redis connection is {} ",
                    exception.getMessage(),
                    Arrays.asList(exception.getStackTrace()));
        }
        return null;
    }
}


