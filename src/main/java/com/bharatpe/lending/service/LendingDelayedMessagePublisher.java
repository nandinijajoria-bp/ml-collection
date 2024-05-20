package com.bharatpe.lending.service;

public interface LendingDelayedMessagePublisher {
    void publish(String appName, String destinationTopic, String partitionKey, Object payload, String hashKey, long timeoutInSeconds) throws Exception;

    void publish(String destinationTopic, String partitionKey, Object payload, String hashKey, long timeoutInSeconds) throws Exception;

    void delete(String hashKey) throws Exception;
}