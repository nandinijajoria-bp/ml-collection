package com.bharatpe.lending.consumer;

import com.bharatpe.lending.common.entity.NBFCPayout;
import com.bharatpe.lending.dto.payout.PayoutResponseDTO;
import com.bharatpe.lending.service.NBFCPayoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static com.bharatpe.lending.constant.ServiceConstants.PAYOUT.NBFC_PAYOUT_STATUS_CALLBACK_TOPIC;

@Service
@Slf4j
public class NBFCPayoutConsumer {

    @Autowired
    private NBFCPayoutService nbfcPayoutService;

    @Autowired
    private ObjectMapper mapper;

    @KafkaListener(concurrency = "${kafka.consumerGroup.nbfc.payout.status.callback.concurrency:1}"
            , topics = NBFC_PAYOUT_STATUS_CALLBACK_TOPIC
            , groupId = "NBFCPAYOUT", autoStartup = "${kafka.consumerGroup.nbfc.payout.status.callback.enabled:false}")
    @KafkaListener(
            concurrency = "${kafka.consumerGroup.nbfc.payout.status.callback.concurrency:1}",
            topics = NBFC_PAYOUT_STATUS_CALLBACK_TOPIC,
            groupId = "NBFCPAYOUT",
            autoStartup = "${kafka.confluent.consumer.new:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void handlePaymentNotifications(@Payload String rawData, @Header(KafkaHeaders.RECEIVED_PARTITION_ID) String partition
            , @Header(KafkaHeaders.OFFSET) String offset) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Start processing NBFC payout status callback for : {}, partition: {}, offset: {}", rawData, partition, offset);
        try {
            PayoutResponseDTO payoutStatus = mapper.readValue(rawData, PayoutResponseDTO.class);
            nbfcPayoutService.updatePayoutByStatusData(payoutStatus);
            log.info("Payout update completed");
        } catch (Exception ex) {
            log.error(ex.getClass().getSimpleName() + " occurred while consuming NBFC payout status callback: {} - {}", rawData, ex);
        }
    }
}
