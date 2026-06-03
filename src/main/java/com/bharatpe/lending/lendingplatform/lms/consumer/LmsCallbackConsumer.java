package com.bharatpe.lending.lendingplatform.lms.consumer;

import com.bharatpe.lending.lendingplatform.lms.consumer.service.*;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LmsLoanCreationCallback;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LmsCallbackConsumer {

    private final LenderSidePaymentStatusCallback lenderSidePaymentStatusCallback;
    private final LmsSidePaymentStatusCallback lmsSidePaymentStatusCallback;
    private final LmsCreateLoanSuccessCallback lmsCreateLoanSuccessCallback;
    private final LmsCreateLoanFailureCallback lmsCreateLoanFailureCallback;
    private final LmsCreateLoanRetryCallback lmsCreateLoanRetryCallback;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${trillion.lender.receipt.posting:lc-trillion-receipt}",
            groupId = "lending-service",
            autoStartup = "${kafka.lending.collection.1lms.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeLenderReceiptPostingStatus(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received Lender Receipt posting callback from Kafka: {}", message);
        lenderSidePaymentStatusCallback.updateLenderPostingStatus(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.consumer.lms.payment:lp-loan-payment-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.lending.collection.1lms.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeLmsPaymentPostingStatus(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received Lms payment posting callback from Kafka: {}", message);
        lmsSidePaymentStatusCallback.updateLmsPostingStatus(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.consumer.loan.retry:lending-loan-creation-retry}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.1lms.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeCreateLoanRetryRequest(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received loan creation retry request from Kafka: {}", message);
        lmsCreateLoanRetryCallback.retryLoanCreationAtLms(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.consumer.lms.loan.creation:lp-loan-creation-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.1lms.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeLmsCreateLoanSuccessCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received Lms create loan callback - {}", message);

        Optional<LmsLoanCreationCallback> callbackOpt = getLoanCreationStatus(message);
        if (!callbackOpt.isPresent()) {
            log.info("Invalid callback message received or parsing failed: {}", message);
            return;
        }

        LmsLoanCreationCallback callback = callbackOpt.get();
        if (Boolean.TRUE.equals(callback.getIsSuccess())) {
            log.info("Received Lms create loan success callback for bpLoanId: {}", callback.getBpLoanId());
            lmsCreateLoanSuccessCallback.saveSuccessLoanDetails(callback.getBpLoanId());
        } else {
            log.info("Received Lms create loan failure callback for bpLoanId: {}", callback.getBpLoanId());
            lmsCreateLoanFailureCallback.sendLoanToOldFlow(callback.getBpLoanId());
        }
        MDC.clear();
    }

    private Optional<LmsLoanCreationCallback> getLoanCreationStatus(String message) {
        try {
            LmsLoanCreationCallback callback = objectMapper.readValue(message, new TypeReference<LmsLoanCreationCallback>() {});
            log.info("Parsed loan creation callback: {}", callback);
            return Optional.of(callback);
        } catch (Exception e) {
            log.error("Failed to parse loan creation callback from message: {}", message, e);
            return Optional.empty();
        }
    }
}
