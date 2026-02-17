package com.bharatpe.lending.consumer;

import com.bharatpe.lending.dto.KycDocApprovedTopicDto;
import com.bharatpe.lending.loanV2.dto.BankStatementSessionCallbackDto;
import com.bharatpe.lending.service.LoanAndRTEEligibilityComputeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Map;

@Service
@Slf4j
public class LoanAndRTEELigibilityComputeConsumer {
    @Autowired
    LoanAndRTEEligibilityComputeService loanAndRTEEligibilityComputeService;

//    @KafkaListener(
//            topics = "kyc_doc_approved",
//            containerFactory = "ConfluentKafkaListenerContainer",
//            autoStartup = "${compute.loan_rte_eligibility.enabled:false}")
    public void computeLoanAdnRTEEligibliity(@Payload  String rawData, @Header(KafkaHeaders.RECEIVED_PARTITION_ID) String partition
            , @Header(KafkaHeaders.OFFSET) String offset) {
        log.info("Start computing loan & rte eligibility for : {}, partition: {}, offset: {}", rawData, partition, offset);
        try {
            KycDocApprovedTopicDto kycDocApprovedTopicDto = new ObjectMapper().readValue(rawData, KycDocApprovedTopicDto.class);
            loanAndRTEEligibilityComputeService.computeLoanAndRTEEligibility(kycDocApprovedTopicDto);
        }catch (Exception ex){
            log.error("Exception in computing rte & loan eligibility {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
    }
}
