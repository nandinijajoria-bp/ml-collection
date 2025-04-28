package com.bharatpe.lending.lendingplatform.nbfc.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.DisbursalMessage;
import com.bharatpe.lending.lendingplatform.nbfc.enums.Lender;
import com.bharatpe.lending.lendingplatform.nbfc.enums.WorkflowStage;
import com.bharatpe.lending.lendingplatform.nbfc.registry.WorkflowRegistryFactory;
import com.bharatpe.lending.lendingplatform.nbfc.service.workflow.Workflow;
import com.bharatpe.lending.lendingplatform.nbfc.util.WorkflowUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DisbursalConsumer {
    private final ObjectMapper objectMapper;
    private final WorkflowUtil workflowUtil;
    private final WorkflowRegistryFactory workflowRegistryFactory;

    @KafkaListener(
            topics="${disbursal.initiate.topic:lc-disbursal-initiate}",
            concurrency = "5",
            autoStartup = "${kafka.confluent.consumer.auto.startup:false}",
            containerFactory = "ConfluentKafkaListenerContainer"
    )
    public void consumeDisbursalEvent(String message) {
        log.info("Consumed message: {}", message);
        processMessage(message);
    }

    private void processMessage(String message) {
        DisbursalMessage disbursalMessage = null;
        try {
            disbursalMessage = objectMapper.readValue(message, DisbursalMessage.class);
        }catch(Exception e) {
            log.error("Error while converting message: {}", message, e);
            return;
        }
        if(ObjectUtils.isEmpty(disbursalMessage) || ObjectUtils.isEmpty(disbursalMessage.getApplicationId())) {
            log.error("Application not found in disbursal message");
            return;
        }
        LendingApplication lendingApplication = workflowUtil.getLendingApplication(disbursalMessage.getApplicationId());
        List<Workflow> workflows = workflowRegistryFactory
                .getWorkflowRegistry(Lender.valueOf(lendingApplication.getLender()))
                .getStageWorkflow(WorkflowStage.DISBURSAL);
        WorkflowUtil.invokeWorkflows(workflows, disbursalMessage.getApplicationId());
    }
}
