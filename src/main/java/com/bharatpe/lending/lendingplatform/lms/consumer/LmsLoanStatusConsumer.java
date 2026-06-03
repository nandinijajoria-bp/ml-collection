package com.bharatpe.lending.lendingplatform.lms.consumer;


import com.bharatpe.lending.lendingplatform.lms.dto.pojo.LmsLoanStatus;
import com.bharatpe.lending.lendingplatform.lms.service.LmsLoanStatusUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LmsLoanStatusConsumer {

    private final ObjectMapper objectMapper;
    private final LmsLoanStatusUpdateService lmsLoanStatusUpdateService;

    private static final String REQUEST_ID = "requestId";


    @KafkaListener(
            topics = "${kafka.consumer.lms.loan.creation:lp-loan-status-update}",
            groupId = "lending-service",
            autoStartup = "${kafka.lending.collection.1lms.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeLoanStatus(String message) {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString());
        try {
            log.info("Received Loan Status Update Call: {}", message);
            LmsLoanStatus lmsLoanStatus = objectMapper.readValue(message, LmsLoanStatus.class);

            if (lmsLoanStatus.getBpLoanId() == null || lmsLoanStatus.getStatus() == null) {
                log.warn("Invalid loan status update message: {}", message);
                return;
            }

            lmsLoanStatusUpdateService.updateLoanStatus(lmsLoanStatus);
        } catch (Exception e) {
            log.error("Failed to process loan status update message: {}", message, e);
        } finally {
            MDC.clear();
        }
    }
}
