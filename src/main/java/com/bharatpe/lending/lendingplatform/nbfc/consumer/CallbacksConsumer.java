package com.bharatpe.lending.lendingplatform.nbfc.consumer;

import com.bharatpe.lending.lendingplatform.nbfc.consumer.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbacksConsumer {

    private final KYCCallbackProcessingService kycCallbackProcessingService;
    private final BRECallbackProcessingService breCallbackProcessingService;
    private final DisbursalCallbackProcessingService disbursalCallbackProcessingService;
    private final EKycCallbackProcessingService eKycCallbackProcessingService;
    private final CKycCallbackProcessingService cKycCallbackProcessingService;

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.bre.callback:lc-bre-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeBRECallback(String message) {
        log.info("Received BRE callback from Kafka: {}", message);
        breCallbackProcessingService.processBRECallback(message);
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.disbursal.callback:lc-disbursal-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeDisbursalCallback(String message) {
        log.info("Received Disbursal callback from Kafka: {}", message);
        disbursalCallbackProcessingService.processDisbursalCallback(message);
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.kyc.callback:lc-kyc-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeKYCCallback(String message) {
        log.info("Received KYC callback from Kafka: {}", message);
        kycCallbackProcessingService.processKYCCallback(message);
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.ekyc.callback:lc-ekyc-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeEKycCallback(String message) {
        log.info("Received EKYC callback from Kafka: {}", message);
        eKycCallbackProcessingService.processEKYCCallback(message);
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.ckyc.callback:lc-ckyc-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeCKycCallback(String message) {
        log.info("Received CKYC callback from Kafka: {}", message);
        cKycCallbackProcessingService.processCKYCCallback(message);
    }
}
