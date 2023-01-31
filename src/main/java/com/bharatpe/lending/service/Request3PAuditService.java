package com.bharatpe.lending.service;


import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.dto.Request3PAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Slf4j
public class Request3PAuditService<T> {

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Async
    public void pushKafkaAudit(T payload, String entity) {
        try {
            KafkaAudit<Request3PAudit> kafkaAudit = new KafkaAudit("easy_loan", "lending",
                    entity, payload);
            log.info("pushing kafka event for {}", kafkaAudit);
            kafkaTemplate.send("easyloan_audit_data", kafkaAudit);
        } catch (Exception e) {
            log.error("error while sending audit data {}", Arrays.asList(e.getStackTrace()));
        }
    }

}
