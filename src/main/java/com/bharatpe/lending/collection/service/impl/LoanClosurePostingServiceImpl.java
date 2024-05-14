package com.bharatpe.lending.collection.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.service.LoanClosurePostingService;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.enums.PaymentAdjustmentModes;
import com.bharatpe.lending.common.enums.TransferTypeModes;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.ForeclosureRequestDto;
import com.bharatpe.lending.loanV3.dto.LiquiLoansForeclosureChargesRequestDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.TrilionLoansForeclosureChargesRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.LoanReceiptRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.trillions.TrillionForeclosureRequestDto;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentMode;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentRequestType;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal;
import com.bharatpe.lending.loanV3.services.associations.piramal.PaymentAdjustmentModesPiramal;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.bharatpe.lending.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class LoanClosurePostingServiceImpl implements LoanClosurePostingService {
    Logger logger = LoggerFactory.getLogger(PaymentService.class);
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    KafkaTemplate kafkaTemplate;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;
    @Autowired
    NbfcLenderGateway nbfcLenderGateway;
    @Autowired
    AssociationServiceUtil associationServiceUtil;
    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;
    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;
    @Value("${nbfc.baseurl.v3.foreclosure:api/v3/lender/foreclosure}")
    String nbfcURI;
    @Value("${nbfc.usfb.foreclosure.topic:usfb-foreclose-loan}")
    String nbfcUsfbForeclosureTopic;

    @Value("${nbfc.capri.foreclosure.topic:capri-foreclose-loan}")
    String nbfcCapriForeclosureTopic;

    @Value("${nbfc.trillion.foreclosure.topic:trillion-foreclose-loan}")
    String nbfcTrillionForeclosureTopic;
    @Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
    String nbfcForeClosureChargePosting;
    @Value("${nbfc.liquiloans.foreclosure.charges.topic:penalty_fee_on_nbfc}")
    String nbfcLiquiLoansForeclosureTopic;

    @Override
    public void sendForeclosureEvent(Long applicationId, String mobile, LendingLedger lendingLedger) {
        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                logger.info("no lending app details record found for the app {}", applicationId);
                return;
            }
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            ForeclosureRequestDto foreclosureRequestDto = ForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.ABFL.name())
                    .productName("LENDING")
                    .payload(ForeclosureRequestDto.Payload.builder()
                            .accountId(lendingApplicationLenderDetails.getAccountId())
                            .dealNo(lendingApplicationLenderDetails.getDealNo())
                            .loanNo(lendingApplicationLenderDetails.getLan())
                            .uniqueId(PaymentAdjustmentModes.getAdjustedModeAbbr(lendingLedger.getAdjustmentMode()) + "_" + TransferTypeModes.getTransferTypeAbbr(lendingLedger.getTransferType()) + "_" + txnId)
                            .loanReceiptDetails(ForeclosureRequestDto.LoanReceiptDetails.builder()
                                    .receiptAmount(lendingLedger.getAmount())
                                    .paidByContactNo(mobile.substring(2))
                                    .transactionRefNumber(String.valueOf(lendingLedger.getId()))
                                    .receiptDateTime(lendingLedger.getDate())
                                    .build())
                            .build())
                    .build();
            logger.info("foreclosure event sent {}", foreclosureRequestDto);
            kafkaTemplate.send("foreclose-loan", objectMapper.readValue(objectMapper.writeValueAsString(foreclosureRequestDto), new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
        }
    }

    @Override
    public void postForeclosureReceiptPiramal(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger) {
        try {
            logger.info("inside the post foreclosure");

            LoanReceiptRequestDTO loanReceiptRequestDTO = new LoanReceiptRequestDTO();

            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(activeLoan.getApplicationId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name());

            String adjustmentMode = PaymentAdjustmentModesPiramal.valueOf(lendingLedger.getAdjustmentMode()).getAdjustedModeEquivalent();

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));

            LoanReceiptRequestDTO.PaymentReceiptData paymentReceiptData = new LoanReceiptRequestDTO.PaymentReceiptData();
            paymentReceiptData.setTransactionReference(txnId);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            paymentReceiptData.setReceivedDate(simpleDateFormat.format(lendingLedger.getDate()));
            paymentReceiptData.setRemarks(lendingLedger.getAdjustmentMode());


            List<LoanReceiptRequestDTO.AllocationDetail> allocationDetails = new ArrayList<>();
            allocationDetails.add(LoanReceiptRequestDTO.AllocationDetail.builder().allocationItem("P").paidAmount(lendingLedger.getPrinciple()).build());
            allocationDetails.add(LoanReceiptRequestDTO.AllocationDetail.builder().allocationItem("I").paidAmount(lendingLedger.getInterest()).build());

            loanReceiptRequestDTO.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());

            BigDecimal amount = BigDecimal.valueOf(lendingLedger.getAmount());
            loanReceiptRequestDTO.setPaymentAmount(amount);
            loanReceiptRequestDTO.setLedgerId(lendingLedger.getId());
            loanReceiptRequestDTO.setPaymentMode(PaymentMode.valueOf(adjustmentMode));
            loanReceiptRequestDTO.setPaymentType(PaymentTypePiramal.FORECLOSURE_PAYMENT);
            loanReceiptRequestDTO.setPaymentRequestType(PaymentRequestType.POST);
            loanReceiptRequestDTO.setPaymentReceiptData(paymentReceiptData);
            loanReceiptRequestDTO.setAllocationDetails(allocationDetails);

            NbfcRequestDto<LoanReceiptRequestDTO> dtoNbfcRequestDto = new NbfcRequestDto<>();
            dtoNbfcRequestDto.setLender("PIRAMAL");
            dtoNbfcRequestDto.setApplicationId(activeLoan.getApplicationId());
            dtoNbfcRequestDto.setProductName("LENDING");
            dtoNbfcRequestDto.setPayload(loanReceiptRequestDTO);
            logger.info("resquest dto {}",dtoNbfcRequestDto);
            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
            lendingCollectionAudit.setStatus("SUCCESS");
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            try {
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(dtoNbfcRequestDto), NbfcResponseDto.class,nbfcBaseUrl+nbfcURI);
                logger.info("Successfully hit the api for foreclosure {}",nbfcResponseDto);
            } catch (JsonProcessingException e) {
                logger.error("exception occurred while fetching foreclosure amt to nbfc svc for {}",dtoNbfcRequestDto, e);
            }
        } catch (Exception e){
            logger.error("Exception {} while posting the foreclosure receipt for application id {}",e.getMessage(),activeLoan.getApplicationId());
        }
    }

    @Override
    public void postForeclosureReceipt(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger) {
        try {
            logger.info("inside the post foreclosure of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
            NBFCRequestDTO nbfcRequest = associationServiceUtil.foreclosureReceiptRequest(activeLoan.getNbfc(), activeLoan.getApplicationId(), lendingLedger);
            if(ObjectUtils.isEmpty(nbfcRequest)) {
                log.info("Error in generating request for foreclosure receipt of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
                return;
            }
            logger.info("foreclosure receipt request for {} {}", activeLoan.getNbfc(), nbfcRequest);
            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
            lendingCollectionAudit.setStatus("SUCCESS");
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            kafkaTemplate.send(getLenderForeclsoureReceiptTopic(activeLoan.getNbfc()), objectMapper.readValue(objectMapper.writeValueAsString(nbfcRequest), new TypeReference<Map<String, Object>>() {}));
            log.info("foreclosure event sent for application {} {}", activeLoan.getApplicationId(), nbfcRequest);
        } catch (Exception e){
            logger.error("Exception {} while posting the foreclosure receipt for application id {} {}",e.getMessage(),activeLoan.getApplicationId(), e);
        }
    }

    @Override
    public void sendForeclosureEventTrillionLoans(Long applicationId, LendingLedger lendingLedger, Long orderId) {
        String status = "SUCCESS";
        Double charge = 0.0;
        Double chargeTax = 0.0;
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                logger.info("TrillionLoans: no lending app details record found for the app {}", applicationId);
                return;
            }
            if(loanForeClosureCharges != null) {
                TrilionLoansForeclosureChargesRequestDto trilionLoansForeclosureChargesRequestDto = TrilionLoansForeclosureChargesRequestDto.builder()
                        .applicationId(applicationId)
                        .productName("LENDING")
                        .lender(Lender.TRILLIONLOANS.name())
                        .payload(TrilionLoansForeclosureChargesRequestDto.Payload.builder()
                                .lan(lendingApplicationLenderDetails.getLan())
                                .amount(loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax())
                                .chargeId("5")
                                .dueDate(loanForeClosureCharges.getCreatedAt())
                                .build())
                        .build();
                logger.info("TrillionLoans: posting foreclosure charges to lender {}", trilionLoansForeclosureChargesRequestDto);
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(trilionLoansForeclosureChargesRequestDto), NbfcResponseDto.class,nbfcBaseUrl+nbfcForeClosureChargePosting);
                log.info("TrillionLoans: response foreclosure charges posting request :{} and response : {}", objectMapper.writeValueAsString(trilionLoansForeclosureChargesRequestDto), nbfcResponseDto);

                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    log.info("TrillionLoans: foreclosure charges posted to lender{}",nbfcResponseDto);
                    charge = loanForeClosureCharges.getAmount();
                    chargeTax = loanForeClosureCharges.getTax();
                    postingStatus = "POSTED";
                } else {
                    // Bhuvnesh :- if charge posting is failed then cancel foreclosure posting
                    // and make lendingCollectionAudit entry as failed
                    log.info("TrillionLoans: foreclosure charges posting failed to request {} response {}",trilionLoansForeclosureChargesRequestDto, nbfcResponseDto);
                    throw new Exception("Foreclosure failed");
                }
            }

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            TrillionForeclosureRequestDto trillionForeclosureRequestDto = TrillionForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .payload(TrillionForeclosureRequestDto.Payload.builder()
                            .loanAccounts(lendingApplicationLenderDetails.getLan())
                            .note("Foreclosure")
                            .preClosureReasonId(192)
                            .transactionAmount(String.valueOf(Math.ceil(lendingLedger.getAmount()+charge+chargeTax)))
                            .transactionDate(lendingLedger.getDate())
                            .paymentTypeId(1)
                            .interestWaiverAmount(0.0)
                            .receiptNumber(txnId)
                            .chargeDiscountDetails(new ArrayList<>())
                            .waiveCharges(new ArrayList<>())
                            .build())
                    .build();
            logger.info("TrillionLoans: foreclosure event sent {}", trillionForeclosureRequestDto);
            kafkaTemplate.send(nbfcTrillionForeclosureTopic, objectMapper.readValue(objectMapper.writeValueAsString(trillionForeclosureRequestDto), new TypeReference<Map<String, Object>>() {}));
            logger.info("TrillionLoans: updating LCA for foreclosed event for application id : {} ", lendingApplicationLenderDetails.getApplicationId());
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
            status = "FAILED";
        }

        logger.info("TrillionLoans: updating LCA for foreclosed event for application id : {}  and status is {}", applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("TrillionLoans: updated LCA for foreclosed event for application id : {} and status :{} ", applicationId, status);
        }
        if (loanForeClosureCharges != null) {
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    @Override
    public void sendForeclosureChargesEventLiquiLoans(long applicationId, long loanId, long lendingLedgerId, String lender, long orderId) {
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        if (loanForeClosureCharges == null) {
            logger.info("No fore closure charges exist for the orderId {}",orderId);
            return;
        }
        try{
            LiquiLoansForeclosureChargesRequestDto liquiLoansForeclosureChargesRequestDto = LiquiLoansForeclosureChargesRequestDto.builder()
                    .loanId(loanId)
                    .applicationId(applicationId)
                    .lender(lender)
                    .chargeDate(loanForeClosureCharges.getCreatedAt())
                    //.chargeDate(new Date())
                    .chargeAmount(loanForeClosureCharges.getAmount()+loanForeClosureCharges.getTax())
                    .chargeId(String.valueOf(loanForeClosureCharges.getId()))
                    .chargeType(8)  // defined by lender
                    .build();
            logger.info(" {}  foreclosure charges event Sending {}",lender, liquiLoansForeclosureChargesRequestDto);
            Object metadata = kafkaTemplate.send(nbfcLiquiLoansForeclosureTopic, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto)).get();
            logger.info(" {}  foreclosure charges event sent {}",lender, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto));
            postingStatus = "POSTED";
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
        }
        loanForeClosureCharges.setChargePostingStatus(postingStatus);
        loanForeClosureChargesDao.save(loanForeClosureCharges);
    }
    private String getLenderForeclsoureReceiptTopic(String lender) {
        switch (lender) {
            case "USFB":
                return nbfcUsfbForeclosureTopic;
            case "CAPRI":
                return nbfcCapriForeclosureTopic;
            default:
                return null;
        }
    }
}
