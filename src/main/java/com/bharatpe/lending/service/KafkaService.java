package com.bharatpe.lending.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * @author dhvl
 */
@Service
@Slf4j
public class KafkaService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToTopic(String topicName, String partitionKey, Object payload) {
        if (!ObjectUtils.isEmpty(partitionKey)) {
            log.info("Processing payload {} to topic {} partition key {}", payload, topicName, partitionKey);
            kafkaTemplate.send(topicName, partitionKey, payload);
        } else {
            send(topicName, payload);
        }
    }

    public void send(String topicName, Object payload) {
        log.info("Processing payload {} to topic {}", payload, topicName);
        kafkaTemplate.send(topicName, payload);
    }

    public void send(ProducerRecord producerRecord) {
        log.info("Publishing producer record {}", producerRecord);
        kafkaTemplate.send(producerRecord);
    }
}
