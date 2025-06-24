package com.bharatpe.lending.lendingplatform.nbfc.consumer;

import com.bharatpe.lending.lendingplatform.nbfc.consumer.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbacksConsumer {

    private final KYCCallbackProcessingService kycCallbackProcessingService;
    private final BRECallbackProcessingService breCallbackProcessingService;
    private final DisbursalCallbackProcessingService disbursalCallbackProcessingService;
    private final EKycCallbackProcessingService eKycCallbackProcessingService;
    private final CKycCallbackProcessingService cKycCallbackProcessingService;
    private final PennyDropCallbackProcessingService pennyDropCallbackProcessingService;

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.bre.callback:lc-bre-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeBRECallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received BRE callback from Kafka: {}", message);
        breCallbackProcessingService.processBRECallback(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.disbursal.callback:lc-disbursal-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeDisbursalCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received Disbursal callback from Kafka: {}", message);
        disbursalCallbackProcessingService.processDisbursalCallback(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.kyc.callback:lc-kyc-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeKYCCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received KYC callback from Kafka: {}", message);
        kycCallbackProcessingService.processKYCCallback(message);
        MDC.clear();
    }

//    @KafkaListener(
//            topics = "${kafka.topic.lending.connector.ekyc.callback:lc-ekyc-callback}",
//            groupId = "lending-service",
//            autoStartup = "${kafka.listener.autoStartup:false}",
//            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeEKycCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received EKYC callback from Kafka: {}", message);
        eKycCallbackProcessingService.processEKYCCallback(message);
        MDC.clear();
    }

//    @KafkaListener(
//            topics = "${kafka.topic.lending.connector.ckyc.callback:lc-ckyc-callback}",
//            groupId = "lending-service",
//            autoStartup = "${kafka.listener.autoStartup:false}",
//            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumeCKycCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received CKYC callback from Kafka: {}", message);
        cKycCallbackProcessingService.processCKYCCallback(message);
        MDC.clear();
    }

    @KafkaListener(
            topics = "${kafka.topic.lending.connector.penny.drop.callback:lc-penny-drop-callback}",
            groupId = "lending-service",
            autoStartup = "${kafka.listener.autoStartup:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void consumePennyDropCallback(String message) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received PennyDrop callback from Kafka: {}", message);
        pennyDropCallbackProcessingService.processPennyDropCallback(message);
        MDC.clear();
    }
}
