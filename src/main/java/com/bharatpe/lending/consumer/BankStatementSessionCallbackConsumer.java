package com.bharatpe.lending.consumer;

import com.bharatpe.lending.dto.payout.PayoutResponseDTO;
import com.bharatpe.lending.loanV2.dto.BankStatementSessionCallbackDto;
import com.bharatpe.lending.loanV2.service.BankStatementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BankStatementSessionCallbackConsumer {

    @Autowired
    private BankStatementService bankStatementService;

    public static final String BANK_STATEMENT_SESSION_CALLBACK_TOPIC = "el.bank.statement.session.callback";

    @KafkaListener(concurrency = "${kafka.consumerGroup.bank.statement.session.callback.concurrency:1}"
            , topics = BANK_STATEMENT_SESSION_CALLBACK_TOPIC
            , groupId = "BANKSTATEMENT", autoStartup = "${kafka.consumerGroup.bank.statement.session.callback.enabled:false}")
    @KafkaListener(
            concurrency = "${kafka.consumerGroup.bank.statement.session.callback.concurrency:1}",
            topics = BANK_STATEMENT_SESSION_CALLBACK_TOPIC,
            groupId = "BANKSTATEMENT",
            autoStartup = "${kafka.confluent.consumer:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void bankStatementSessionCallback(@Payload String rawData, @Header(KafkaHeaders.RECEIVED_PARTITION_ID) String partition
            , @Header(KafkaHeaders.OFFSET) String offset){
        log.info("Start processing bank statement session callback for : {}, partition: {}, offset: {}", rawData, partition, offset);
        try {
            BankStatementSessionCallbackDto bankStatementSessionCallbackDto = new ObjectMapper().readValue(rawData, BankStatementSessionCallbackDto.class);
            bankStatementService.updateBankStatementSession(bankStatementSessionCallbackDto);
            log.info("bank statement session update completed");
        } catch (Exception ex) {
            log.error(ex.getClass().getSimpleName() + " occurred while consuming bank statement session callback: {} - {}", rawData, ex);
        }
    }
}
