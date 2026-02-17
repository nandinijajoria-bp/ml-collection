package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.collection.core.service.LoanClosurePostingService;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.PaymentAdjustmentModes;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.TransferTypeModes;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.piramal.LoanReceiptRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalChargesRequestDto;
import com.bharatpe.lending.loanV3.dto.trillions.TrillionForeclosureRequestDto;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentMode;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentRequestType;
import com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal;
import com.bharatpe.lending.loanV3.services.associations.piramal.PaymentAdjustmentModesPiramal;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.bharatpe.lending.service.PaymentService;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
public class LoanClosurePostingServiceImpl implements LoanClosurePostingService {
    Logger logger = LoggerFactory.getLogger(PaymentService.class);
    public static final String RECEIVABLE = "RECEIVABLE";
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    @Qualifier("CollectionLowLatencyKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;
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

    @Autowired
    LendingApplicationDao lendingApplicationDao;
    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingPayinDetailsDao lendingPayinDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;

    @Value("${payu.nach.bounce.charge:500}")
    Integer payUNachBounceCharge;

    @Value("${piramal.nach.bounce.charge:500}")
    Integer piramalNachBounceCharge;

    @Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
    String nbfcChargePosting;

    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;
    @Value("${nbfc.baseurl.v3.foreclosure:api/v3/lender/foreclosure}")
    String nbfcURI;
    @Value("${nbfc.usfb.foreclosure.topic:usfb-foreclose-loan}")
    String nbfcUsfbForeclosureTopic;

    @Value("${nbfc.capri.foreclosure.topic:capri-foreclose-loan}")
    String nbfcCapriForeclosureTopic;

    @Value("${nbfc.muthoot.foreclosure.topic:muthoot-loan-receipt}")
    String nbfcMuthootForeclosureTopic;

    @Value("${nbfc.trillion.foreclosure.topic:trillion-foreclose-loan}")
    String nbfcTrillionForeclosureTopic;
    @Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
    String nbfcForeClosureChargePosting;
    @Value("${nbfc.liquiloans.foreclosure.charges.topic:penalty_fee_on_nbfc}")
    String nbfcLiquiLoansForeclosureTopic;
    @Value("${nbfc.payu.foreclosure.topic:payu-foreclose-loan}")
    String nbfcPayuForeclosureTopic;

    @Value("${nbfc.collection.service.base.url:https://api-nbfc.bharatpemoney.com/}")
    String nbfcCollectionServiceBaseUrl;

    @Autowired
    SmfgConfig smfgConfig;

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Override
    public void sendForeclosureEvent(Long applicationId, String mobile, LendingLedger lendingLedger, Long orderId) {
        logger.info("Send Foreclosure Event: applicationId: {}, mobile: {}, lendingLedger: {}, orderId: {}", applicationId, mobile, lendingLedger, orderId);
        String status = "SUCCESS";
        Double charge = 0.0;
        Double chargeTax = 0.0;
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        logger.info("LoanForeclosureCharges Record: {}", loanForeClosureCharges);

        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                logger.info("no lending app details record found for the app {}", applicationId);
                return;
            }
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                logger.error("no lending application record found for the app {}", applicationId);
                return;
            }

            String ledgerId = String.valueOf(lendingLedger.getId());

            LendingPayinDetails lendingPayinDetails = lendingPayinDetailsDao.findByMerchantIdAndLoanIdAndTerminalOrderId(lendingApplication.get().getMerchantId(), lendingLedger.getLendingPaymentSchedule().getId(), lendingLedger.getTerminalOrderId());

            if(!ObjectUtils.isEmpty(lendingPayinDetails)){
                logger.info("Lending Payin Details found for applicationId: {}, lendingPayinDetails: {}", applicationId, lendingPayinDetails);
                ledgerId = lendingPayinDetails.getId() + "P";
            }

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(ledgerId);
            Date txnDate = LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate());

            if(loanForeClosureCharges != null) {
                logger.info("Creating ABFL Foreclosure Charges post charge request");
                ForeclosureChargesRequestDto foreclosureChargesRequestDto = ForeclosureChargesRequestDto.builder()
                        .applicationId(applicationId)
                        .productName("LENDING")
                        .lender(Lender.ABFL.name())
                        .payload(ForeclosureChargesRequestDto.Payload.builder()
                                         .accountId(lendingApplicationLenderDetails.getAccountId())
                                         .uniqueId("ABFL_FC_" + txnId)
                                         .dealNo(lendingApplicationLenderDetails.getDealNo())
                                         .loanNo(lendingApplicationLenderDetails.getLan())
                                         .transactionId(ledgerId)
                                         .chargeType("R")
                                         .businessPartnerType("CS")
                                         .chargeAmount(String.valueOf(loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax()))
                                         .taxInclusive("N")
                                         .finalAmount(String.valueOf(loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax()))
                                         .chargeCode("112")
                                         .build())
                        .build();
                logger.info("ABFL: posting foreclosure charges to lender {}", foreclosureChargesRequestDto);
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(foreclosureChargesRequestDto), NbfcResponseDto.class, nbfcCollectionServiceBaseUrl + nbfcForeClosureChargePosting);
                log.info("ABFL: response foreclosure charges posting request :{} and response : {}", objectMapper.writeValueAsString(foreclosureChargesRequestDto), nbfcResponseDto);

                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    log.info("ABFL: foreclosure charges posted to lender{}",nbfcResponseDto);
                    charge = loanForeClosureCharges.getAmount();
                    chargeTax = loanForeClosureCharges.getTax();
                    postingStatus = "POSTED";
                } else {
                    // Bhuvnesh :- if charge posting is failed then cancel foreclosure posting
                    // and make lendingCollectionAudit entry as failed
                    log.info("ABFL: foreclosure charges posting failed to request {} response {}", foreclosureChargesRequestDto, nbfcResponseDto);
                    throw new Exception("Foreclosure failed");
                }
            }

            ForeclosureRequestDto foreclosureRequestDto = ForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.ABFL.name())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()))
                    .payload(ForeclosureRequestDto.Payload.builder()
                            .accountId(lendingApplicationLenderDetails.getAccountId())
                            .dealNo(lendingApplicationLenderDetails.getDealNo())
                            .loanNo(lendingApplicationLenderDetails.getLan())
                             .uniqueId(PaymentAdjustmentModes.getAdjustedModeAbbr(lendingLedger.getAdjustmentMode()) + "_" + getTransferTypeAbbr(lendingLedger.getTransferType()) + "_" + txnId)
                            .loanReceiptDetails(ForeclosureRequestDto.LoanReceiptDetails.builder()
                                    .receiptAmount(lendingLedger.getAmount())
                                    .paidByContactNo(mobile.substring(2))
                                    .transactionRefNumber(ledgerId)
                                    .receiptDateTime(txnDate)
                                    .build())
                            .build())
                    .build();
            logger.info("foreclosure event sent {}", foreclosureRequestDto);
            confluentKafkaTemplate.send("foreclose-loan", objectMapper.readValue(objectMapper.writeValueAsString(foreclosureRequestDto), new TypeReference<Map<String, Object>>() {
            }));
            logger.info("ABFL: updating LCA for foreclosed event for application id : {} ", lendingApplicationLenderDetails.getApplicationId());
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
            status = "FAILED";
        }

        logger.info("ABFL: updating LCA for foreclosed event for application id : {}  and status is {}", applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("ABFL: updated LCA for foreclosed event for application id : {} and status :{} ", applicationId, status);
        }
        if (loanForeClosureCharges != null) {
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    private String getTransferTypeAbbr(String transferType) {
        if("DIRECT_TRANSFER_LENDER".equalsIgnoreCase(transferType)) return "DTTL";
        if("TRANSFER_BY_BP".equalsIgnoreCase(transferType)) return "TBBP";
        return transferType;
    }

    public void piramalPenaltyPosting(LendingApplicationLenderDetails lendingApplicationLenderDetails, PenaltyFeeLedger penaltyFeeLedger, double amount, String type) {
        PiramalChargesRequestDto piramalChargesRequestDto = createPiramalPostChargesDto(lendingApplicationLenderDetails, penaltyFeeLedger,amount,type);
        log.info("Piramal: posting penalty  to lender {}", piramalChargesRequestDto);
        try {
            NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(piramalChargesRequestDto), NbfcResponseDto.class,nbfcCollectionServiceBaseUrl+nbfcChargePosting);
            log.info("Piramal: response penalty  posting request :{} and response : {}", objectMapper.writeValueAsString(piramalChargesRequestDto), nbfcResponseDto);
            setPostingStatus(nbfcResponseDto, penaltyFeeLedger);
        } catch (Exception e) {
            log.error("Piramal penalty posting failed for request {} and error {}", piramalChargesRequestDto, e.getMessage());
        }
    }

    private PiramalChargesRequestDto createPiramalPostChargesDto(LendingApplicationLenderDetails lendingApplicationLenderDetails, PenaltyFeeLedger penaltyFeeLedger, double amount, String type) {
        return PiramalChargesRequestDto.builder()
                .applicationId(lendingApplicationLenderDetails.getApplicationId())
                .productName("LENDING")
                .lender(Lender.PIRAMAL.name())
                .payload(PiramalChargesRequestDto.Payload.builder()
                        .productId("BRTPE")
                        .uniqueReferenceId(String.valueOf(penaltyFeeLedger.getId()))
                        .loanAccountNumber(lendingApplicationLenderDetails.getLan())
                        .adviseType(RECEIVABLE)
                        .adviseAmount(amount)
                        .adviseDate(penaltyFeeLedger.getCreatedAt())
                        .feeTypeCode(type)
                        .isTopup(false)
                        .build())
                .build();
    }

    @Override
    public void postForeclosureReceiptPiramal(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, LoanClosureDTO loanClosureDTO) {
        try {
            logger.info("inside the post foreclosure");

            LoanReceiptRequestDTO loanReceiptRequestDTO = new LoanReceiptRequestDTO();

            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(activeLoan.getApplicationId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(activeLoan.getApplicationId());
            if(!lendingApplication.isPresent()) {
                logger.error("no lending application record found for application id {}", activeLoan.getApplicationId());
                throw new RuntimeException("no lending application record found for the app " + activeLoan.getApplicationId());
            }

            String adjustmentMode = PaymentAdjustmentModesPiramal.valueOf(lendingLedger.getAdjustmentMode()).getAdjustedModeEquivalent();

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            Date txnDate = LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate());
            logger.info("inside the post foreclosure-  adjustmentMode - {}, txnId - {} ", adjustmentMode,txnId);
            LoanReceiptRequestDTO.PaymentReceiptData paymentReceiptData = new LoanReceiptRequestDTO.PaymentReceiptData();
            paymentReceiptData.setTransactionReference(txnId);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            paymentReceiptData.setReceivedDate(simpleDateFormat.format(txnDate));
            paymentReceiptData.setRemarks(lendingLedger.getAdjustmentMode());


            List<LoanReceiptRequestDTO.AllocationDetail> allocationDetails = new ArrayList<>();
            allocationDetails.add(LoanReceiptRequestDTO.AllocationDetail.builder().allocationItem("P").paidAmount(lendingLedger.getPrinciple()).build());
            allocationDetails.add(LoanReceiptRequestDTO.AllocationDetail.builder().allocationItem("I").paidAmount(lendingLedger.getInterest()).build());

            loanReceiptRequestDTO.setProductId(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())? "BPETU" : "BRTPE");

            loanReceiptRequestDTO.setLoanAccountNumber(lendingApplicationLenderDetails.getLan());
            BigDecimal amount = BigDecimal.valueOf(lendingLedger.getAmount());
            loanReceiptRequestDTO.setPaymentAmount(amount);
            loanReceiptRequestDTO.setLedgerId(lendingLedger.getId());
            loanReceiptRequestDTO.setPaymentMode(PaymentMode.valueOf(adjustmentMode));
            loanReceiptRequestDTO.setPaymentType(PaymentTypePiramal.FORECLOSURE_PAYMENT);
            loanReceiptRequestDTO.setPaymentRequestType(PaymentRequestType.POST);
            loanReceiptRequestDTO.setPaymentReceiptData(paymentReceiptData);
            Date  loanEligibilityDate = activeLoan.getLoanApplication().getAgreementAt();
            loanReceiptRequestDTO.setFeeList(null);
            if (loanUtil.checkIfForeClosureChargesApplicable(loanEligibilityDate, activeLoan.getNbfc())) {
                List<LoanReceiptRequestDTO.FeeList> fcFeeList = new ArrayList<>();
                fcFeeList.add(LoanReceiptRequestDTO.FeeList.builder()
                        .feeType("FORECLOSURE_FEES")
                        .feeAmount(loanClosureDTO.getForeclosureCharges())
                        .paidAmount(loanClosureDTO.getForeclosureCharges())
                        .waiverAmount(0d)
                        .build());
                loanReceiptRequestDTO.setFeeList(fcFeeList);
            }

            loanReceiptRequestDTO.setAllocationDetails(allocationDetails);
            NbfcRequestDto<LoanReceiptRequestDTO> dtoNbfcRequestDto = new NbfcRequestDto<>();
            dtoNbfcRequestDto.setLender("PIRAMAL");
            dtoNbfcRequestDto.setTopup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()));
            dtoNbfcRequestDto.setApplicationId(activeLoan.getApplicationId());
            dtoNbfcRequestDto.setProductName("LENDING");
            dtoNbfcRequestDto.setPayload(loanReceiptRequestDTO);
            logger.info("resquest dto {}",dtoNbfcRequestDto);
            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
            try {
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(dtoNbfcRequestDto), NbfcResponseDto.class,nbfcCollectionServiceBaseUrl+nbfcURI);
                logger.info("Successfully hit the api for foreclosure {}",nbfcResponseDto);
                if(nbfcResponseDto != null && nbfcResponseDto.getSuccess()){
                    lendingCollectionAudit.setStatus("SUCCESS");
                }else{
                    lendingCollectionAudit.setStatus("FAILED");
                }
            } catch (JsonProcessingException e) {
                logger.error("exception occurred while fetching foreclosure amt to nbfc svc for {}",dtoNbfcRequestDto, e);
                lendingCollectionAudit.setStatus("FAILED");
            }
            lendingCollectionAuditDao.save(lendingCollectionAudit);
        } catch (Exception e){
            logger.error("Exception {} while posting the foreclosure receipt for application id {}",e.getMessage(),activeLoan.getApplicationId());
        }
    }

    @Override
    public void postForeclosureReceipt(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger) {
        try {
            Date  loanEligibilityDate = activeLoan.getLoanApplication().getAgreementAt();
            if("UGRO".equalsIgnoreCase(activeLoan.getNbfc()) && loanUtil.checkIfForeClosureChargesApplicable(loanEligibilityDate, activeLoan.getNbfc()) ){
                log.info("foreclosure reciept posting for ugro loan is skipping {}", activeLoan.getId());
                return;
            }
            logger.info("inside the post foreclosure of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
            NBFCRequestDTO nbfcRequest = associationServiceUtil.foreclosureReceiptRequest(activeLoan.getNbfc(), activeLoan.getApplicationId(), lendingLedger, null);
            if(ObjectUtils.isEmpty(nbfcRequest)) {
                log.info("Error in generating request for foreclosure receipt of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
                return;
            }
            logger.info("foreclosure receipt request for {} {}", activeLoan.getNbfc(), nbfcRequest);
            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
            lendingCollectionAudit.setStatus("SUCCESS");
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            confluentKafkaTemplate.send(getLenderForeclsoureReceiptTopic(activeLoan.getNbfc()), objectMapper.readValue(objectMapper.writeValueAsString(nbfcRequest), new TypeReference<Map<String, Object>>() {}));
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
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(trilionLoansForeclosureChargesRequestDto), NbfcResponseDto.class,nbfcCollectionServiceBaseUrl+nbfcForeClosureChargePosting);
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
            Date txnDate = LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate());
            TrillionForeclosureRequestDto trillionForeclosureRequestDto = TrillionForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .payload(TrillionForeclosureRequestDto.Payload.builder()
                            .loanAccounts(lendingApplicationLenderDetails.getLan())
                            .note("Foreclosure")
                            .preClosureReasonId(192)
                            .transactionAmount(String.valueOf(Math.ceil(lendingLedger.getAmount())))
                            .transactionDate(txnDate)
                            .paymentTypeId(1)
                            .interestWaiverAmount(0.0)
                            .receiptNumber(txnId)
                            .chargeDiscountDetails(new ArrayList<>())
                            .waiveCharges(new ArrayList<>())
                            .build())
                    .build();
            logger.info("TrillionLoans: foreclosure event sent {}", trillionForeclosureRequestDto);
            confluentKafkaTemplate.send(nbfcTrillionForeclosureTopic, objectMapper.readValue(objectMapper.writeValueAsString(trillionForeclosureRequestDto), new TypeReference<Map<String, Object>>() {}));
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
            Object metadata = confluentKafkaTemplate.send(nbfcLiquiLoansForeclosureTopic, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto)).get();
            logger.info(" {}  foreclosure charges event sent {}",lender, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto));
            postingStatus = "POSTED";
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
        }
        loanForeClosureCharges.setChargePostingStatus(postingStatus);
        loanForeClosureChargesDao.save(loanForeClosureCharges);
    }

    public void postPenaltyFeeChargeToLender(LendingPaymentSchedule activeLoan, PostChargesToLenderDTO postChargesToLenderDTO) {
        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(activeLoan.getApplicationId(), Status.ACTIVE.name());

            PayUChargesRequestDto payUChargesRequestDto = PayUChargesRequestDto.builder()
                    .applicationId(activeLoan.getApplicationId().toString())
                    .productName("LENDING")
                    .lender(Lender.PAYU.name())
                    .payload(PayUChargesRequestDto.Payload.builder()
                            .chargeDate(new SimpleDateFormat("yyyy-MM-dd").format( Calendar.getInstance(TimeZone.getDefault()).getTime()))
                            .chargeType(postChargesToLenderDTO.getChargeType())
                            .requestId(postChargesToLenderDTO.getChargeId())
                            .loanId(lendingApplicationLenderDetails.getLan())
                            .amount(postChargesToLenderDTO.getPenaltyFee())
                            .applicationId(lendingApplicationLenderDetails.getLeadId())
                            .build())
                    .build();

            log.info("PayU: posting penalty charges to lender {}", payUChargesRequestDto);

            NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(payUChargesRequestDto), NbfcResponseDto.class, nbfcCollectionServiceBaseUrl + nbfcChargePosting);

            log.info("PayU: response penalty charges posting request :{} and response : {}", objectMapper.writeValueAsString(payUChargesRequestDto), nbfcResponseDto);
            PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findNachBounceCharge(activeLoan.getId(),postChargesToLenderDTO.getChargeId());

            setPostingStatus(nbfcResponseDto, penaltyFeeLedger);
        }
        catch (Exception e){
            log.error("Error in Posting Penalty Charge to Lender for loan: {} and error: {}: {}", activeLoan.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private void setPostingStatus(NbfcResponseDto nbfcResponseDto, PenaltyFeeLedger penaltyFeeLedger) throws Exception {
        if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
            log.info("postPenaltyFeeChargeToLender: penalty charges posted to lender: {}", nbfcResponseDto);
            penaltyFeeLedger.setIsPosted(true);
            penaltyFeeLedger.setPostingStatus("SUCCESS");
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
        } else {
            penaltyFeeLedger.setIsPosted(false);
            penaltyFeeLedger.setPostingStatus("FAILED");
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
            log.error("postPenaltyFeeChargeToLender: penalty charges posting failed, response : {}",  nbfcResponseDto);
            throw new Exception("Penalty charges posting failed");
        }
    }

    @Override
    public void sendForeclosureEventPayu(Long applicationId, LendingLedger lendingLedger, Long orderId, Boolean postPendingCharges, String requestId) {
        LendingPaymentSchedule loan = lendingPaymentScheduleDao.findByApplicationId(applicationId);
        if(postPendingCharges){
            postPenaltyFeeChargeToLender(loan, PostChargesToLenderDTO.builder()
                    .penaltyFee(payUNachBounceCharge)
                    .chargeType("NACH_BOUNCE_CHARGE")
                    .chargeId(requestId)
                    .build());
        }
        String postingStatus = "FAILURE";
        String status="SUCCESS";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(applicationId);

        if(ObjectUtils.isEmpty(lendingPaymentSchedule)){
            logger.info("Payu: Lending Payment Schedule does not exist for {}", applicationId);
            return;
        }

        if (loanForeClosureCharges == null && !loanUtil.checkLoanCoolOffPeriod(lendingPaymentSchedule.getCreatedAt())) {
            logger.info("Payu : No foreclosure charges exist for the orderId {}", orderId);
            return;
        }
        try {
            NBFCRequestDTO nbfcRequestDTO = associationServiceUtil.foreclosureReceiptRequest(Lender.PAYU.name(), applicationId, lendingLedger, null);
            logger.info("Payu :  foreclosure charges event Sending {}", nbfcRequestDTO);
            Object metadata = confluentKafkaTemplate.send(nbfcPayuForeclosureTopic, objectMapper.readValue(objectMapper.writeValueAsString(nbfcRequestDTO), new TypeReference<Map<String, Object>>() {}));
            logger.info("Payu : foreclosure charges event sent {}", objectMapper.writeValueAsString(nbfcRequestDTO));
            postingStatus = "POSTED";
        } catch (Exception e) {
            logger.error("Payu : error occurred while sending foreclosure event {}", e.getMessage());
            status="FAILED";
        }
        logger.info("Payu : updating LCA for foreclosed event for application id : {}  and status is {}", applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("Payu: updated LCA for foreclosed event for application id : {} and status :{} ", applicationId, status);
        }

        if(loanForeClosureCharges != null){
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    @Override
    public void sendForeclosureEventToLender(Long applicationId, LendingLedger lendingLedger, Long orderId, String lender) {
        String status = "SUCCESS";
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            logger.info("{} no lending app details record found for the app {}", lender, applicationId);
            return;
        }

        try {
            NBFCRequestDTO nbfcRequestDTO = associationServiceUtil.foreclosureReceiptRequest(lender, applicationId, lendingLedger, orderId);
            logger.info("{} :  foreclosure charges event Sending {}", lender, nbfcRequestDTO);
            Object metadata = confluentKafkaTemplate.send(getLenderForeclsoureReceiptTopic(lender), objectMapper.readValue(objectMapper.writeValueAsString(nbfcRequestDTO), new TypeReference<Map<String, Object>>() {
            }));
            logger.info("{} : foreclosure charges event sent {}", lender, objectMapper.writeValueAsString(nbfcRequestDTO));
            postingStatus = "POSTED";
        } catch (Exception e) {
            logger.error("{} : error occurred while sending foreclosure event {}", lender, e.getMessage());
            status = "FAILED";
        }


        logger.info("{}: updating LCA for foreclosed event for application id : {}  and status is {}", lender,applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(), 1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("{}: updated LCA for foreclosed event for application id : {} and status :{} ", lender, applicationId, status);
        }
        if (loanForeClosureCharges != null) {
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    private String getLenderForeclsoureReceiptTopic(String lender) {
        switch (lender) {
            case "USFB":
                return nbfcUsfbForeclosureTopic;
            case "CAPRI":
                return nbfcCapriForeclosureTopic;
            case "CREDITSAISON":
                return csConfig.getNbfcCreditsaisonForeclosureTopic();
            case "SMFG":
                return smfgConfig.getForeclosureTopic();
            case "UGRO":
                return ugroConfig.getForeclosureTopic();
            case "OXYZO":
                return oxyzoConfig.getForeclosureTopic();
            case "MUTHOOT":
                return nbfcMuthootForeclosureTopic;
            default:
                return null;
        }
    }
}
