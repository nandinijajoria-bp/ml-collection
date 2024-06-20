package com.bharatpe.lending.consumer;

import com.bharatpe.lending.common.dto.PayoutResponseDTO;
import com.bharatpe.lending.common.service.PennyDropService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.bharatpe.lending.common.Constants.PennyDropConstants.PENNYDROP_CALLBACK_TOPIC;

@Service
@Slf4j
public class PennyDropPayoutConsumer {

    @Autowired
    private PennyDropService pennyDropService;

    @Autowired
    private ObjectMapper mapper;

    @KafkaListener(
            concurrency = "${kafka.consumerGroup.pennydrop.payout.status.callback.concurrency:1}",
            topics = PENNYDROP_CALLBACK_TOPIC,
            groupId = "PENNYDROP",
            autoStartup = "${kafka.confluent.consumer.new:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void handlePaymentNotifications(@Payload String rawData, @Header(KafkaHeaders.RECEIVED_PARTITION_ID) String partition
            , @Header(KafkaHeaders.OFFSET) String offset) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Start processing Pennydrop payout status callback for : {}, partition: {}, offset: {}", rawData, partition, offset);
        try {
            PayoutResponseDTO payoutStatus = mapper.readValue(rawData, PayoutResponseDTO.class);
            pennyDropService.updatePayoutByStatusData(payoutStatus);
            log.info("Pennydrop update completed for orderId : {}", payoutStatus.getOrderId());
        } catch (Exception ex) {
            log.error(ex.getClass().getSimpleName() + " occurred while consuming PennyDrop payout status callback: {} - {}", rawData, ex);
        }
    }
}
