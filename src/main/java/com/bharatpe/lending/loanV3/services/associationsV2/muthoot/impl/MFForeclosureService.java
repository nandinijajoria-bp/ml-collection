package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingCollectionAuditDao;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingCollectionAudit;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFForeclosureDetailsRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MuthootRepaymentRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFForeclosureDetailsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.bharatpe.lending.common.enums.Status.ACTIVE;

@Slf4j
@Service
public class MFForeclosureService  {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private AutoPayUPIDao autoPayUPIDao;

    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Autowired
    private LendingCollectionAuditDao lendingCollectionAuditDao;

    @Value("${muthoot.foreclosure.details.timeout.threshold:20000}")
    Integer muthootForeclosureDetailsTimeoutThreshold;

    @Value("${muthoot.partial.realtime.receipt.posting.rollout:true}")
    private boolean muthootPartialRealTimeReceiptPostingRollout;

    @Value("#{'${muthoot.realtime.receipt.posting.loan.ids:}'.split(',')}")
    private List<Long> muthootRealtimeReceiptPostingLoanIds;

    private static final String PROGRAM = "EDI";
    private static final String PRODUCT_NAME = "LENDING";
    private static final String PURPOSE_CLOSURE = "CLOSURE";
    private static final String DATE_TIME_FORMAT = "dd/MM/yyyy hh:mm:ss a";
    private static final String TRANSACTION_MODE = "ELECTRONIC_FUND_TRANSFER";


    public Double getForeclosureDetails(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.MUTHOOT.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of MUTHOOT for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.MUTHOOT.name())
                .applicationId(applicationId)
                .payload(MFForeclosureDetailsRequestDTO.builder()
                        .customerID(lendingApplicationLenderDetails.getLeadId())
                        .program("EDI")
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH, muthootForeclosureDetailsTimeoutThreshold);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                MFForeclosureDetailsResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFForeclosureDetailsResponseDTO.class);
                if(!ObjectUtils.isEmpty(response.getData())
                        && !ObjectUtils.isEmpty(response.getData().getDetails())
                        && !ObjectUtils.isEmpty(response.getData().getDetails().getDues())
                        && !ObjectUtils.isEmpty(response.getData().getDetails().getDues().getTotalDueAmount())) {
                    return response.getData().getDetails().getDues().getTotalDueAmount();
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while fetching foreclosure details of MUTHOOT for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.error("MUTHOOT: application not found for given applicationId while foreclosure: {}", applicationId);
                return null;
            }

//            If muthootPartialRealTimeReceiptPostingRollout is true then loan ids in this list muthootRealtimeReceiptPostingLoanIds
//            only will be processed for real-time receipt posting
            if (muthootPartialRealTimeReceiptPostingRollout
                    && (ObjectUtils.isEmpty(muthootRealtimeReceiptPostingLoanIds)
                    || lendingLedger.getLendingPaymentSchedule().getId() == null
                    || !muthootRealtimeReceiptPostingLoanIds.contains(lendingLedger.getLendingPaymentSchedule().getId()))) {
                log.info("MUTHOOT: Skipping receipt posting - loanId {} not in whitelist",
                        lendingLedger.getLendingPaymentSchedule().getId());
                return null;
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                            applicationId,
                            ACTIVE.name(),
                            lendingApplication.getLender());

            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(), 1);

            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.error("MUTHOOT: LendingApplicationLenderDetails not found for applicationId: {}",
                        applicationId);
                return null;
            }

            String repaymentID = generateIdempotencyKey(lendingLedger);

            String utrNumber = ObjectUtils.isEmpty(lendingLedger.getTerminalOrderId())
                    ? String.valueOf(lendingLedger.getId())
                    : lendingLedger.getTerminalOrderId();

            String provider = getSubPaymentMode(lendingLedger);

            String digioTxnId = getDigioTxnId(lendingLedger, provider);

            String customerID = lendingApplicationLenderDetails.getLeadId();

            String remark = ObjectUtils.isEmpty(lendingLedger.getAdjustmentMode()) ? null : lendingLedger.getAdjustmentMode();

            String transactionDate = DateTimeUtil.getDateInFormat(LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate()), DATE_TIME_FORMAT);

            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("ledgerId", lendingLedger.getId());
            identifier.put("idempotencyKey", repaymentID);
            identifier.put("bpReferenceId", lendingCollectionAudit.getId());
            identifier.put("lenderReferenceId", utrNumber);

            return NBFCRequestDTO.builder()
                    .lender(lendingApplicationLenderDetails.getLender())
                    .productName(PRODUCT_NAME)
                    .applicationId(applicationId)
                    .payload(
                            MuthootRepaymentRequestDTO.builder()
                                    .customerID(customerID)
                                    .program(PROGRAM)
                                    .loanAccountNumber(lendingApplicationLenderDetails.getLan())
                                    .amount(lendingLedger.getAmount())
                                    .repaymentID(repaymentID)
                                    .purpose(PURPOSE_CLOSURE)
                                    .remark(remark)
                                    .lmsPostingTime(transactionDate)
                                    .realisationTime(transactionDate)
                                    .transactionDetails(
                                            MuthootRepaymentRequestDTO.TransactionDetails.builder()
                                                    .transactionTime(transactionDate)
                                                    .pgTransactionID(digioTxnId)
                                                    .pgOrderID(digioTxnId)
                                                    .utrNumber(utrNumber)
                                                    .mode(TRANSACTION_MODE)
                                                    .subMode(provider)
                                                    .build()
                                    )
                                    .build()
                    )
                    .identifier(identifier)
                    .build();

        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of MUTHOOT for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String generateIdempotencyKey(LendingLedger lendingLedger) {
        String terminalOrderId = lendingLedger.getTerminalOrderId() == null ? "" : lendingLedger.getTerminalOrderId();
        return String.format("%d_%s", lendingLedger.getId(), terminalOrderId);
    }

    private String getSubPaymentMode(LendingLedger lendingLedger) {
        String mode = lendingLedger.getAdjustmentMode();
        if (ObjectUtils.isEmpty(mode)) {
            log.info("MUTHOOT: adjustmentMode is null for Ledger ID: {}", lendingLedger.getId());
            return "CASH FREE PG";
        }

        switch (mode.trim().toUpperCase()) {
            case "SETTLEMENT":
            case "FP":
                return "QR_COLLECTION";
            case "BHARATPE_NACH":
            case "NACH":
            case "EXTERNAL_NACH":
                return "Digio";
            case "UPI_AUTOPAY":
                return getAutoPayUpiProvider(lendingLedger);
            default:
                return "CASH FREE PG";
        }
    }

    private String getAutoPayUpiProvider(LendingLedger lendingLedger) {

        Long applicationId = lendingLedger.getLendingPaymentSchedule().getApplicationId();
        String pullPaymentMode = getPullPaymentModeFromAdjustmentMode(lendingLedger.getAdjustmentMode());
        
        if (pullPaymentMode != null) {
            log.info("MUTHOOT: Checking LendingPullPayment for applicationId: {}, mode: {}", 
                applicationId, pullPaymentMode);
            LendingPullPayment pullPayment = lendingPullPaymentDao.findTop1ByTerminalOrderIdAndLoanIdAndModeOrderByIdDesc(
                    lendingLedger.getTerminalOrderId(), lendingLedger.getLendingPaymentSchedule().getId(), pullPaymentMode);

            if (pullPayment != null && StringUtils.hasText(pullPayment.getProvider())) {
                String provider = pullPayment.getProvider();
                log.info("MUTHOOT: Found provider from LendingPullPayment for applicationId: {}, provider: {}",
                        lendingLedger.getLendingPaymentSchedule().getApplicationId(), provider);
                return mapProviderToSubMode(provider);
            }
        } else {
            log.info("MUTHOOT: PullPayment mode is null for adjustmentMode: {}, skipping LendingPullPayment lookup", 
                lendingLedger.getAdjustmentMode());
        }

        // Fallback: Get provider from autopay_upi table if not found in LendingPullPayment
        log.info("MUTHOOT: Provider not found in LendingPullPayment, falling back to autopay_upi for applicationId: {}",
                applicationId);
        AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(
                applicationId, Lender.MUTHOOT.name(), Collections.singletonList("ACTIVE"));

        if (ObjectUtils.isEmpty(autoPayUPI)) {
            log.info("MUTHOOT: AutoPayUPI not found for applicationId: {}", applicationId);
            return null;
        }

        String provider = autoPayUPI.getGateway();
        return mapProviderToSubMode(provider);
    }

    private String mapProviderToSubMode(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }

        switch (provider.trim().toUpperCase()) {
            case "DIGIO":
                return "Digio";
            case "JS_CASHFREE":
            case "DR_CASHFREE":
            case "CASHFREE":
                return "CASH FREE PG";
            case "UNITY":
                return "UNITY";
            default:
                return provider;
        }
    }

    private String getDigioTxnId(LendingLedger lendingLedger, String provider) {
        String mode = getPullPaymentModeFromAdjustmentMode(lendingLedger.getAdjustmentMode());

        if ("DIGIO".equalsIgnoreCase(provider) && mode != null) {
            log.info("MUTHOOT: Checking LendingPullPayment for digioTxnId, mode: {}", mode);
            LendingPullPayment pullPayment = lendingPullPaymentDao.findTop1ByTerminalOrderIdAndLoanIdAndModeOrderByIdDesc(
                    lendingLedger.getTerminalOrderId(), lendingLedger.getLendingPaymentSchedule().getId(), mode);
            if (pullPayment != null && pullPayment.getMetaData() != null && pullPayment.getMetaData().containsKey("digioTxnId")) {
                Object digioTxnIdObj = pullPayment.getMetaData().get("digioTxnId");
                if (digioTxnIdObj != null) {
                    String digioTxnId = digioTxnIdObj.toString();
                    if (StringUtils.hasText(digioTxnId)) {
                        log.info("MUTHOOT: Found digioTxnId from LendingPullPayment metadata for LCA ID: {}, digioTxnId: {}",
                                lendingLedger.getId(), digioTxnId);
                        return digioTxnId;
                    }
                }
            }
        } else if ("DIGIO".equalsIgnoreCase(provider) && mode == null) {
            log.info("MUTHOOT: PullPayment mode is null for adjustmentMode: {}, skipping LendingPullPayment lookup for digioTxnId", 
                lendingLedger.getAdjustmentMode());
        }

        return ObjectUtils.isEmpty(lendingLedger.getTerminalOrderId())
                ? String.valueOf(lendingLedger.getId())
                : lendingLedger.getTerminalOrderId();
    }

    private String getPullPaymentModeFromAdjustmentMode(String adjustmentMode) {
        if (adjustmentMode == null) {
            return null;
        }

        String upperMode = adjustmentMode.toUpperCase();
        if ("UPI_AUTOPAY".equals(upperMode)) {
            return "AUTOPAYUPI";
        }

        if ("BHARATPE_NACH".equals(upperMode)
                || "NACH".equals(upperMode)
                || "EXTERNAL_NACH".equals(upperMode)) {
            return "NACH";
        }

        return null;
    }
}
