package com.bharatpe.lending.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Aspect
@Component
@Slf4j
public class LogExecutionTimeAspect {
    @Around("@annotation(com.bharatpe.lending.annotations.LogExecutionTime)")
    public Object logger(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Instant start = Instant.now();
        try {
            return proceedingJoinPoint.proceed();
        } finally {
            String className = proceedingJoinPoint.getTarget().getClass().getSimpleName();
            String methodName = proceedingJoinPoint.getSignature().getName();
            log.info("Method {}.{} Execution Time: {} ms", className, methodName, Duration.between(start, Instant.now()).toMillis());
        }
    }
}
