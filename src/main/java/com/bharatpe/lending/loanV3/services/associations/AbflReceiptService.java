package com.bharatpe.lending.loanV3.services.associations;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.RepaymentRequestDto;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflReceiptService implements ILenderAssociationService<Optional> {

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    MerchantService merchantService;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
//                       String referenceNo, Double amount, Long lpsId) {
        try {
            String referenceNo = (String) args.get("referenceNo");
            Double amount = (Double) args.get("amount");
            Long lpsId = (Long) args.get("lpsId");
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingApplication.get().getMerchantId());
            try {
                String message = objectMapper.writeValueAsString(
                        RepaymentRequestDto.builder()
                                .lender(lendingApplicationLenderDetails.getLender())
                                .productName("LENDING")
                                .applicationId(applicationId)
                                .payload(RepaymentRequestDto.Payload.builder()
                                        .accountId(lendingApplication.get().getExternalLoanId())
                                        .loanNo(lendingApplicationLenderDetails.getLan())
                                        .paidByContactNo(basicDetailsDto.get().getMobile().substring(2))
                                        .transactionRefNumber(referenceNo)
                                        .uniqueId(lpsId + referenceNo)
                                        .receiptAmount(amount)
                                        .receiptDateTime(new Date())
                                        .build())
                                .build()
                );
                log.info("receipt msg : {}", message);
                kafkaTemplate.send("loan-receipt", objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {
                }));
            } catch (IOException e) {
                log.error("error occurred while posting receipt {} {} {} {}", applicationId, referenceNo, amount, e.getMessage());
            }
        } catch (Exception e) {
            log.error("error while posting receipt via pg callback for {}", applicationId, e);
        }
        return Optional.empty();
    }
}
