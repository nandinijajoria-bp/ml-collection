package com.bharatpe.lending.loanV3.services.associations;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.RepaymentRequestDto;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.PaymentAdjustmentModes;
import com.bharatpe.lending.common.enums.TransferTypeModes;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV3.dto.LoanReceiptResponseDTO;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.gateway.AbflApiGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflReceiptService implements ILenderAssociationService<Optional> {

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    AbflApiGateway abflApiGateway;

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
                confluentKafkaTemplate.send("loan-receipt", objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {
                }));
            } catch (IOException e) {
                log.error("error occurred while posting receipt {} {} {} {}", applicationId, referenceNo, amount, e.getMessage());
            }
        } catch (Exception e) {
            log.error("error while posting receipt via pg callback for {}", applicationId, e);
        }
        return Optional.empty();
    }

    public boolean sendReceipt(Long ledgerId) {
        try {
            Optional<LendingLedger> lendingLedgerOptional = lendingLedgerDao.findById(ledgerId);
            if (!lendingLedgerOptional.isPresent()) {
                log.info("Ledger with provided id not found : {}", ledgerId);
                return false;
            }
            LendingLedger lendingLedger = lendingLedgerOptional.get();
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(lendingLedger.getLendingPaymentSchedule().getApplicationId());
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            String transferType = TransferTypeModes.getTransferTypeAbbr(lendingLedger.getTransferType());
            if (!ObjectUtils.isEmpty(transferType) && transferType.equalsIgnoreCase("DIRECT_TRANSFER_LENDER")) {
                transferType = "DTTL";
            } else if (!ObjectUtils.isEmpty(transferType) && transferType.equalsIgnoreCase("TRANSFER_BY_BP")) {
                transferType = "TBBP";
            }
            RepaymentRequestDto repaymentRequestDto =
                    RepaymentRequestDto.builder()
                            .lender("ABFL")
                            .productName("LENDING")
                            .applicationId(lendingApplication.get().getId())
                            .payload(RepaymentRequestDto.Payload.builder()
                                    .accountId(lendingApplication.get().getExternalLoanId())
                                    .loanNo(lendingApplication.get().getNbfcId())
                                    .paidByContactNo(lendingLedger.getLendingPaymentSchedule().getMobile().substring(2))
                                    .transactionRefNumber(String.valueOf(lendingLedger.getId()))
                                    .uniqueId(PaymentAdjustmentModes.getAdjustedModeAbbr(lendingLedger.getAdjustmentMode()) + "_" + transferType + "_" + txnId)
                                    .receiptAmount(lendingLedger.getAmount())
                                    .receiptDateTime(lendingLedger.getDate())
                                    .build())
                            .build();
            log.info("repaymentRequestDto : {} for ledgerId : {}", repaymentRequestDto, ledgerId);
            String message = objectMapper.writeValueAsString(repaymentRequestDto);
            log.info("receipt msg : {}", message);
            confluentKafkaTemplate.send("loan-receipt", objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {}));
            return true;
        } catch (Exception exception) {
            log.error("exception while processing the loan receipt for ledger id : {}", ledgerId, exception);
            return false;
        }
    }

}