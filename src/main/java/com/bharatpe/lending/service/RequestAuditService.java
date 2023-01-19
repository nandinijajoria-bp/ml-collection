package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.dto.RequestResponseAuditDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Service
@Slf4j
public class RequestAuditService {

    @Autowired
    RequestAuditFactory requestAuditFactory;

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Async
    public void auditApiRequestResponseData(RequestResponseAuditDto payload) {
        try {
            IRequestAudit iRequestAudit = requestAuditFactory.getRequestAuditService(payload.getRequestUri());
            KafkaAudit kafkaAudit = new KafkaAudit("easy_loans", "lending", null, payload);
//            lending_eligibility
            if (!ObjectUtils.isEmpty(iRequestAudit)) {
                kafkaAudit.setData(iRequestAudit.refineAuditData(payload));
                kafkaAudit.setEntityName(iRequestAudit.getEntityName());
                if (ObjectUtils.isEmpty(kafkaAudit.getData()) || ObjectUtils.isEmpty(kafkaAudit.getEntityName())) {
                    log.info("discarding audit as its empty or has to be bypassed or entity name is empty !");
                    return;
                }
                pushKafkaAudit(kafkaAudit);
            }
        } catch (Exception e) {
            log.error("exception occurred in auditing response {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

    }

    public void pushKafkaAudit(KafkaAudit kafkaAudit) {
        try {
            log.info("pushing kafka event for {}", kafkaAudit);
            kafkaTemplate.send("easyloan_audit_data", kafkaAudit);
        } catch (Exception e) {
            log.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }
}
