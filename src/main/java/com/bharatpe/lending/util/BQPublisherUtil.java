package com.bharatpe.lending.util;

import com.bharatpe.lending.common.dto.KafkaAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class BQPublisherUtil {

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "easyloan_audit_data";

    public <T> void publish(String service, String entityName, T data) {
        log.info("BQ publish for enitityName: {}", entityName);
        sanityCheck(entityName, data);
        KafkaAudit<T> kafkaAudit = new KafkaAudit<>("easy_loan", service, entityName, data);
        kafkaTemplate.send(TOPIC, kafkaAudit);
        log.info("Published data in topic:{} for entity:{}", TOPIC, entityName);
    }

    private <T> void sanityCheck(String entityName, T data) {
        if (!StringUtils.hasText(entityName)) {
            throw new IllegalArgumentException("Invalid entityName!");
        }
        if (ObjectUtils.isEmpty(data)) {
            throw new IllegalArgumentException("Invalid data!");
        }
    }


}
