package com.bharatpe.lending.util;

import com.bharatpe.lending.common.dto.KafkaAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BQPublisherUtil {

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    private KafkaTemplate<String, Object> confluentKafkaTemplate;

    private static final String TOPIC = "easyloan_audit_data";

    public <T> void publish(String service, String entityName, T data) {
        log.info("BQ publish for enitityName: {}", entityName);
        sanityCheck(entityName, data);
        KafkaAudit<T> kafkaAudit = new KafkaAudit<>("easy_loan", service, entityName, data);
        confluentKafkaTemplate.send(TOPIC, kafkaAudit);
        log.info("Published data in topic:{} for entity:{}", TOPIC, entityName);
    }

    public void sendBqAudit(String service, String entityName, String responseBody, String requestBody, Long merchantId) {
        try {
            Map<String, Object> bqMap = new HashMap<>();
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("response_body", responseBody);
            dataMap.put("request_body", requestBody);
            dataMap.put("merchant_id", String.valueOf(merchantId));
            dataMap.put("created_at", new Date());
            dataMap.put("updated_at", new Date());
            bqMap.put("entityName", entityName);
            bqMap.put("response", dataMap);
            sanityCheck(entityName, bqMap);
            KafkaAudit<Map<String, Object>> kafkaAudit = new KafkaAudit<>("easy_loan", service, entityName, dataMap);
            confluentKafkaTemplate.send(TOPIC, kafkaAudit);
            log.info("Published data in topic:{} for entity:{}", TOPIC, entityName);
        } catch (Exception ex) {
            log.error("Exception while sending req, res to bigquery for requestType : {} and merchantId : {}, exception : {}", requestBody, merchantId,
                    ex.getMessage());
        }
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
