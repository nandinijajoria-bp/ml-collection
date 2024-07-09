package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DelayedMessage {
    private String hashKey;
    private String partitionKey;
    private String destinationTopic;
    private Object payload;
    private String processAt;
    private String expiryMinute;

    public DelayedMessage() {
    }

    public DelayedMessage(String hashKey, String partitionKey, String destinationTopic, Object payload, String processAt, String expiryMinute) {
        this.hashKey = hashKey;
        this.partitionKey = partitionKey;
        this.destinationTopic = destinationTopic;
        this.payload = payload;
        this.processAt = processAt;
        this.expiryMinute = expiryMinute;
    }

    public String getHashKey() {
        return hashKey;
    }

    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getDestinationTopic() {
        return destinationTopic;
    }

    public void setDestinationTopic(String destinationTopic) {
        this.destinationTopic = destinationTopic;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public String getProcessAt() {
        return processAt;
    }

    public void setProcessAt(String processAt) {
        this.processAt = processAt;
    }

    public String getExpiryMinute() {
        return expiryMinute;
    }

    public void setExpiryMinute(String expiryMinute) {
        this.expiryMinute = expiryMinute;
    }

    @Override
    public String toString() {
        return "DelayedMessage{" +
                "hashKey='" + hashKey + '\'' +
                ", partitionKey='" + partitionKey + '\'' +
                ", destinationTopic='" + destinationTopic + '\'' +
                ", payload=" + payload +
                ", processAt='" + processAt + '\'' +
                ", expiryMinute='" + expiryMinute + '\'' +
                '}';
    }
}