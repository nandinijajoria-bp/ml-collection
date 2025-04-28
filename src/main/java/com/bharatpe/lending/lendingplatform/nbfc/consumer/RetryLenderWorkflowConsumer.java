package com.bharatpe.lending.lendingplatform.nbfc.consumer;

import com.bharatpe.lending.lendingplatform.nbfc.consumer.service.RetryLenderWorkflowProcessingService;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.RetryWorkflowMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetryLenderWorkflowConsumer {
    private final ObjectMapper objectMapper;
    private final RetryLenderWorkflowProcessingService retryLenderWorkflowProcessingService;

    @KafkaListener(
            topics = "${lending.platform.retry.workflow.topic:lender-retry-workflow}",
            concurrency = "5",
            autoStartup = "${lending.platform.retry.workflow.kafka.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer"
    )
    public void consumeRetryLenderMessage(String message) {
        log.info("Consumed message: {}", message);
        processMessage(message);
    }

    private void processMessage(String message) {
        RetryWorkflowMessage retryWorkflowMessage = null;
        try {
            retryWorkflowMessage = objectMapper.readValue(message, RetryWorkflowMessage.class);
        } catch (Exception e) {
            log.error("Error in parsing consumed message into retry workflow message {}", e.getMessage());
            return;
        }
        if(ObjectUtils.isEmpty(retryWorkflowMessage) || ObjectUtils.isEmpty(retryWorkflowMessage.getApplicationId())
                || ObjectUtils.isEmpty(retryWorkflowMessage.getLenderWorkflowRetryId())) {
            log.error("Can't process message as applicationId or lenderWorkflowRetryId is empty : {}", retryWorkflowMessage);
        }
        retryLenderWorkflowProcessingService.processRetryWorkflowMessage(retryWorkflowMessage);
    }
}
